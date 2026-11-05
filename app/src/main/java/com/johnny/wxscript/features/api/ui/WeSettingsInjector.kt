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
 * ## 锚点策略（8.0.79 / 8.0.80 实测）
 *
 * 设置页的「分组类」本身**没有混淆**，可以直接按类名反射拿到：
 * `com.tencent.mm.plugin.setting.ui.setting_new.settings.SettingGroupMain` /
 * `SettingGroupPersonalInfo` / `SettingGroupAccountInfo` / `SettingAdditionHeaderSearch`、
 * `...setting_new.base.BaseSettingPrefUI` / `BaseSettingUI`。
 *
 * 只有三个基类是混淆的（`BaseSettingItem` / `SettingItemClassesProvider` / `SettingLocation`），
 * 必须靠 DexKit 猜。早先版本要求 [SettingsDexFinder] 把所有类都猜出来才继续，
 * 只要它漏了一个（例如 provider 判定条件随版本变化）整条注入链就直接返回失败、用户看不到入口，
 * 所以这里对「不混淆的那批」全部按真实类名兜底，只在真正无法确定时才报错。
 */
object WeSettingsInjector {

    private const val TAG = "SettingsEntry"
    private const val ENTRY_KEY = "SettingGroup_Main_WxScript"
    private const val ENTRY_TITLE = "微信脚本"
    private const val ENTRY_GROUP = "插件"

    // 这些类名在 8.0.49+ 的设置页里没有混淆，可以直接反射。
    private const val CLS_SETTING_GROUP_MAIN =
        "com.tencent.mm.plugin.setting.ui.setting_new.settings.SettingGroupMain"
    private const val CLS_SETTING_ADDITION_HEADER_SEARCH =
        "com.tencent.mm.plugin.setting.ui.setting_new.settings.SettingAdditionHeaderSearch"
    private const val CLS_SETTING_GROUP_PERSONAL_INFO =
        "com.tencent.mm.plugin.setting.ui.setting_new.settings.SettingGroupPersonalInfo"
    private const val CLS_SETTING_GROUP_ACCOUNT_INFO =
        "com.tencent.mm.plugin.setting.ui.setting_new.settings.SettingGroupAccountInfo"
    private const val CLS_BASE_SETTING_PREF_UI =
        "com.tencent.mm.plugin.setting.ui.setting_new.base.BaseSettingPrefUI"
    private const val CLS_BASE_SETTING_UI =
        "com.tencent.mm.plugin.setting.ui.setting_new.base.BaseSettingUI"

    @Volatile
    private var installed = false

    /** 上次安装失败的原因，诊断页可读。 */
    @Volatile
    var lastError: String? = null
        private set

    /** 当前状态（成功/失败原因），诊断页直接展示。 */
    @Volatile
    var status: String = "未执行"
        private set

    /** 已完成注入的入口 key（诊断用）。 */
    @Volatile
    var installedKey: String? = null
        private set

    /** 锚点解析明细（诊断页可读，便于定位是哪一项没解析到）。 */
    @Volatile
    var anchorDetail: String = ""
        private set

    fun install(hostContext: Context, dexKit: DexKitBridge, classLoader: ClassLoader): Boolean {
        if (installed) return true
        lastError = null
        return runCatching {
            val finder = SettingsDexFinder(dexKit, classLoader, hostContext)
            finder.resolveAll(true)

            // 不混淆的一批：finder 拿不到就用真实类名兜底。
            val groupMain = finder.settingGroupMainClass
                ?: hostClassOrNull(classLoader, CLS_SETTING_GROUP_MAIN)
            val additionHeader = finder.settingAdditionHeaderSearchClass
                ?: hostClassOrNull(classLoader, CLS_SETTING_ADDITION_HEADER_SEARCH)
            val personalInfo = finder.settingGroupPersonalInfoClass
                ?: hostClassOrNull(classLoader, CLS_SETTING_GROUP_PERSONAL_INFO)
            val accountInfo = finder.settingGroupAccountInfoClass
                ?: hostClassOrNull(classLoader, CLS_SETTING_GROUP_ACCOUNT_INFO)
            val basePrefUI = finder.baseSettingPrefUIClass
                ?: hostClassOrNull(classLoader, CLS_BASE_SETTING_PREF_UI)
            val baseUI = finder.baseSettingUIClass
                ?: hostClassOrNull(classLoader, CLS_BASE_SETTING_UI)

            // 真正混淆、只能靠 DexKit 猜的三个基类。
            val baseItem = finder.baseSettingItemClass
            val provider = finder.settingItemClassesProviderClass
            val locationClass = finder.settingLocationClass

            anchorDetail = buildString {
                append("groupMain=${groupMain?.simpleName ?: "null"}")
                append(", addHeader=${additionHeader?.simpleName ?: "null"}")
                append(", personalInfo=${personalInfo?.simpleName ?: "null"}")
                append(", accountInfo=${accountInfo?.simpleName ?: "null"}")
                append(", basePrefUI=${basePrefUI?.simpleName ?: "null"}")
                append(", baseUI=${baseUI?.simpleName ?: "null"}")
                append(", baseItem=${baseItem?.simpleName ?: "null"}")
                append(", provider=${provider?.simpleName ?: "null"}")
                append(", location=${locationClass?.simpleName ?: "null"}")
            }

            val missing = buildList {
                if (groupMain == null) add("SettingGroupMain")
                if (personalInfo == null) add("SettingGroupPersonalInfo")
                if (accountInfo == null) add("SettingGroupAccountInfo")
                if (baseItem == null) add("BaseSettingItem")
                if (provider == null) add("SettingItemClassesProvider")
                if (locationClass == null) add("SettingLocation")
                if (basePrefUI == null) add("BaseSettingPrefUI")
                if (baseUI == null) add("BaseSettingUI")
            }
            if (missing.isNotEmpty() || groupMain == null || personalInfo == null ||
                accountInfo == null || baseItem == null || provider == null ||
                locationClass == null || basePrefUI == null || baseUI == null
            ) {
                lastError = "设置页锚点缺失: " + missing.joinToString() + "｜" + anchorDetail
                status = "失败：" + lastError
                WeLogger.w(TAG, lastError!!)
                return@runCatching false
            }

            val resourceGetString = findResourceHelperGetString(dexKit, classLoader)
            if (resourceGetString == null) {
                lastError = "找不到 ResourceHelper.getString 方法（标题资源 id 无法映射成文字）"
                WeLogger.w(TAG, lastError!!)
                return@runCatching false
            }

            val names = resolveMethodNames(accountInfo, personalInfo, locationClass, finder) ?: run {
                lastError = "设置项方法名推导失败｜" + anchorDetail
                WeLogger.w(TAG, lastError!!)
                return@runCatching false
            }

            val manager = WeChatSettingsManager(
                classBaseSettingItem = baseItem,
                // 不提供开关项：占位传基类即可，switch 相关方法名传空串（不会被调用）
                classBaseSettingSwitchItem = baseItem,
                classSettingLocation = locationClass,
                classSettingItemClassesProvider = provider,
                classBaseSettingPrefUI = basePrefUI,
                classBaseSettingUI = baseUI,
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

            val providerMethods = findProviderMethods(provider)
            if (providerMethods.isEmpty()) {
                lastError = "设置项映射方法为空（provider=${provider.name}）｜" + anchorDetail
                status = "失败：" + lastError
                WeLogger.w(TAG, lastError!!)
                return@runCatching false
            }
            WeLogger.i(TAG, "provider methods: " + providerMethods.joinToString { it.name })

            manager.createItem {
                key = ENTRY_KEY
                title = ENTRY_TITLE
                groupTitle = ENTRY_GROUP
                level = 1
                pageClass = groupMain
                parentClass = additionHeader ?: groupMain
                childClass = personalInfo
                onClick = { activity -> openSettings(activity) }
            }
            manager.install(providerMethods)
            installed = true
            installedKey = ENTRY_KEY
            status = "已注入 · key=$ENTRY_KEY · provider=${provider.simpleName}"
            WeLogger.i(TAG, "settings entry installed (key=$ENTRY_KEY)｜$anchorDetail")
            true
        }.getOrElse {
            lastError = (it.message ?: it.javaClass.simpleName) + "｜" + anchorDetail
            status = "失败：" + lastError
            WeLogger.e(TAG, "settings entry install failed", it)
            false
        }
    }

    private fun hostClassOrNull(loader: ClassLoader, name: String): Class<*>? =
        runCatching { Class.forName(name, false, loader) }.getOrNull()

    private fun openSettings(activity: Activity) {
        if (com.johnny.wxscript.ui.SettingsDialogHost.show(activity)) {
            WeLogger.i(TAG, "open settings via dialog host")
            return
        }
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
    private fun resolveMethodNames(
        accountInfo: Class<*>,
        personalInfo: Class<*>,
        locationClass: Class<*>,
        finder: SettingsDexFinder,
    ): Map<String, String>? {
        val methods = accountInfo.declaredMethods.toList()

        val pageGroupItemClass = methods.firstOrNull { it.returnType == Class::class.java }?.name
            ?: return null
        val level = finder.methodAccountInfoReturns1?.name
            ?: methods.firstOrNull {
                (it.returnType == Int::class.javaPrimitiveType || it.returnType == java.lang.Integer::class.java) &&
                    it.parameterCount == 1
            }?.name
            ?: return null
        val onClick = methods.firstOrNull { it.parameterCount == 3 }?.name ?: return null
        val key = finder.methodAccountInfoSettingKey?.name
            ?: methods.firstOrNull {
                it.returnType == String::class.java && !Modifier.isStatic(it.modifiers)
            }?.name
            ?: return null
        val settingLocation = methods.lastOrNull { it.returnType == locationClass }?.name ?: return null
        val nameResId = methods.lastOrNull {
            (it.returnType == Int::class.javaPrimitiveType || it.returnType == java.lang.Integer::class.java) &&
                it.name != level
        }?.name ?: return null
        val groupNameResId = personalInfo.declaredMethods.firstOrNull {
            it.returnType == java.lang.Integer::class.java
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

    /**
     * 找出「无参且返回 Map」的方法。
     *
     * 微信把每个设置页的 item class 收在一张 Map 里，插件只要往这张表里加自己的 class 就能出现；
     * 具体是哪个方法返回这张表每个版本都不同，所以这里全量筛选、注入时每个都 hook 一遍
     * （只有真的返回 Map 的那次会生效）。
     */
    private fun findProviderMethods(provider: Class<*>): List<Method> {
        val candidates = provider.declaredMethods.filter { method ->
            method.parameterCount == 0 && Map::class.java.isAssignableFrom(method.returnType)
        }
        if (candidates.isNotEmpty()) return candidates
        // 兜底：返回类型不是 Map 但实现类可能返回 Map（接口签名宽泛）
        return provider.declaredMethods.filter { it.parameterCount == 0 }
    }
}
