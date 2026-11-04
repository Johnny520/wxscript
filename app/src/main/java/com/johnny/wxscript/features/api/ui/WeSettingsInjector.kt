package com.johnny.wxscript.features.api.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.johnny.wxscript.activity.SettingsActivity
import com.johnny.wxscript.ui.settings.WeChatSettingsManager
import com.johnny.wxscript.utils.WeLogger
import h.Hchat.dexkit.SettingsDexFinder
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 微信设置页入口注入。
 *
 * 在微信「我 → 设置 → 插件」分组里插入一项「微信脚本」，点它打开本模块的脚本设置页
 * （[SettingsActivity]，运行在微信进程内，与脚本运行时共享同一份状态）。
 *
 * 两条路径：
 * - 新版设置页（8.0.49+，setting_new）：用 dexmaker 生成 BaseSettingItem 子类，
 *   把 class 注册进 SettingItemClassesProvider 的 page→items 映射，由微信自己渲染；
 * - 类解析失败时直接放弃并打日志，不影响脚本功能本身。
 *
 * 说明：微信设置页的类名/方法名都是混淆的，全部通过 [SettingsDexFinder] + 反射推导，
 * 不写死任何混淆名。
 */
object WeSettingsInjector {

    private const val TAG = "SettingsEntry"
    private const val ENTRY_KEY = "SettingGroup_Main_WxScript"
    private const val ENTRY_TITLE = "微信脚本"
    private const val ENTRY_GROUP = "插件"

    @Volatile
    private var installed = false

    /** 上次安装失败的原因，诊断页可读。 */
    @Volatile
    var lastError: String? = null
        private set

    fun install(hostContext: Context, dexKit: DexKitBridge, classLoader: ClassLoader): Boolean {
        if (installed) return true
        lastError = null
        return runCatching {
            val finder = SettingsDexFinder(dexKit, classLoader, hostContext)
            finder.resolveAll(true)

            val missing = buildList {
                if (finder.settingGroupMainClass == null) add("SettingGroupMain")
                if (finder.baseSettingItemClass == null) add("BaseSettingItem")
                if (finder.settingItemClassesProviderClass == null) add("SettingItemClassesProvider")
                if (finder.settingLocationClass == null) add("SettingLocation")
                if (finder.baseSettingPrefUIClass == null) add("BaseSettingPrefUI")
                if (finder.baseSettingUIClass == null) add("BaseSettingUI")
                if (finder.settingGroupAccountInfoClass == null) add("SettingGroupAccountInfo")
                if (finder.settingGroupPersonalInfoClass == null) add("SettingGroupPersonalInfo")
                if (finder.settingAdditionHeaderSearchClass == null) add("SettingAdditionHeaderSearch")
            }
            if (missing.isNotEmpty()) {
                lastError = "设置页锚点缺失: " + missing.joinToString()
                WeLogger.w(TAG, lastError!!)
                return@runCatching false
            }

            val resourceGetString = findResourceHelperGetString(dexKit, classLoader)
            if (resourceGetString == null) {
                lastError = "找不到 ResourceHelper.getString 方法"
                WeLogger.w(TAG, lastError!!)
                return@runCatching false
            }

            val names = resolveMethodNames(finder) ?: run {
                lastError = "设置项方法名推导失败"
                WeLogger.w(TAG, lastError!!)
                return@runCatching false
            }

            val manager = WeChatSettingsManager(
                classBaseSettingItem = finder.baseSettingItemClass,
                // 不提供开关项：占位传基类即可，switch 相关方法名传空串（不会被调用）
                classBaseSettingSwitchItem = finder.baseSettingItemClass,
                classSettingLocation = finder.settingLocationClass,
                classSettingItemClassesProvider = finder.settingItemClassesProviderClass,
                classBaseSettingPrefUI = finder.baseSettingPrefUIClass,
                classBaseSettingUI = finder.baseSettingUIClass,
                methodResourceHelperGetStringById = resourceGetString,
                mGetPageGroupItemClass = names.getValue("pageGroupItemClass"),
                mGetLevel = names.getValue("level"),
                mOnClick = names.getValue("onClick"),
                mGetKey = names.getValue("key"),
                mGetSettingLocation = names.getValue("settingLocation"),
                mGetNameResId = names.getValue("nameResId"),
                mGetGroupNameResId = names.getValue("groupNameResId"),
                mGetSwitchState = "",
                mGetSwitchProperty = "",
            )

            manager.createItem {
                key = ENTRY_KEY
                title = ENTRY_TITLE
                groupTitle = ENTRY_GROUP
                level = 1
                pageClass = finder.settingGroupMainClass
                parentClass = finder.settingAdditionHeaderSearchClass
                childClass = finder.settingGroupPersonalInfoClass
                onClick = { activity -> openSettings(activity) }
            }
            manager.install()
            installed = true
            WeLogger.i(TAG, "settings entry installed (key=$ENTRY_KEY)")
            true
        }.getOrElse {
            lastError = it.message ?: it.javaClass.simpleName
            WeLogger.e(TAG, "settings entry install failed", it)
            false
        }
    }

    private fun openSettings(activity: Activity) {
        runCatching {
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }.onFailure {
            WeLogger.e(TAG, "open SettingsActivity failed", it)
        }
    }

    /** `MicroMsg.ResourceHelper` 的 getString(resId) —— 用于把动态资源 id 映射成中文标题。 */
    private fun findResourceHelperGetString(dexKit: DexKitBridge, classLoader: ClassLoader): Method? {
        return runCatching {
            val query = FindMethod.create().apply {
                matcher(
                    MethodMatcher.create().usingEqStrings(
                        "MicroMsg.ResourceHelper",
                        "get string, resId %d, but context is null",
                    )
                )
            }
            dexKit.findMethod(query).firstOrNull()?.getMethodInstance(classLoader)
        }.getOrNull()
    }

    /**
     * 从 SettingGroupAccountInfo 反推设置项基类的方法名。
     *
     * 这些都是抽象方法（getKey / getNameResId / getSettingLocation / onClick / getLevel / ...），
     * 微信每个版本的混淆名都不同，只能按签名特征找：
     * - 返回 Class 的 → 该分组属于哪个页面
     * - 返回 int 且不是 getLevel 的那个 → 名称资源 id
     * - 参数 3 个的 → 点击回调
     * - 返回 SettingLocation 的 → 所在位置
     */
    private fun resolveMethodNames(finder: SettingsDexFinder): Map<String, String>? {
        val accountInfo = finder.settingGroupAccountInfoClass ?: return null
        val personalInfo = finder.settingGroupPersonalInfoClass ?: return null
        val locationClass = finder.settingLocationClass ?: return null
        val methods = accountInfo.declaredMethods.toList()

        val pageGroupItemClass = methods.firstOrNull { it.returnType == Class::class.java }?.name ?: return null
        val level = finder.methodAccountInfoReturns1?.name ?: return null
        val onClick = methods.firstOrNull { it.parameterCount == 3 }?.name ?: return null
        val key = finder.methodAccountInfoSettingKey?.name
            ?: methods.firstOrNull { it.returnType == String::class.java && !Modifier.isStatic(it.modifiers) }?.name
            ?: return null
        val settingLocation = methods.lastOrNull { it.returnType == locationClass }?.name ?: return null
        val nameResId = methods.lastOrNull {
            (it.returnType == Int::class.javaPrimitiveType || it.returnType == Integer::class.java) &&
                it.name != level
        }?.name ?: return null
        val groupNameResId = personalInfo.declaredMethods.firstOrNull {
            it.returnType == Integer::class.java
        }?.name ?: return null

        return mapOf(
            "pageGroupItemClass" to pageGroupItemClass,
            "level" to level,
            "onClick" to onClick,
            "key" to key,
            "settingLocation" to settingLocation,
            "nameResId" to nameResId,
            "groupNameResId" to groupNameResId,
        )
    }
}
