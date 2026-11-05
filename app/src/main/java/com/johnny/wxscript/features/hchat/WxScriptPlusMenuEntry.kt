package com.johnny.wxscript.features.hchat

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.johnny.wxscript.activity.SettingsActivity
import com.johnny.wxscript.constants.Preferences
import com.johnny.wxscript.loader.utils.ActivityLauncher
import com.johnny.wxscript.ui.SettingsDialogHost
import com.johnny.wxscript.utils.HostInfo
import com.johnny.wxscript.utils.WeLogger
import h.Hchat.hooks.items.script.ScriptMenuDispatcher

/**
 * 微信主界面右上角「+」菜单里的固定入口「微信脚本」。
 *
 * 为什么不只依赖「我 → 设置 → 插件」分组注入（[com.johnny.wxscript.features.api.ui.WeSettingsInjector]）：
 * 设置页的类名/方法名每版都会被混淆，锚点一旦漂移整条注入链就静默失效，用户会完全找不到入口。
 * 「+」菜单走的是 [ScriptMenuDispatcher] 已有的 PlusSubMenuHelper Hook（脚本插件注册菜单用的同一套），
 * 是独立于设置页的第二条通道，因此这里再做一份，两条通道任意一条成功用户都能进脚本页。
 *
 * 默认开启，可在「首页 → 快捷入口」里关闭（写入 MMKV multi-process，微信进程与设置页共享）。
 */
object WxScriptPlusMenuEntry {

    private const val TAG = "PlusMenuEntry"
    private const val OWNER = "wxscript"
    private const val TITLE = "微信脚本"
    private const val SETTINGS_CLASS = "com.johnny.wxscript.activity.SettingsActivity"

    @Volatile
    private var handle: ScriptMenuDispatcher.ScriptMenuHandle? = null

    /** 当前状态，设置页/诊断页直接展示。 */
    @Volatile
    var status: String = "未执行"
        private set

    @Volatile
    var installed: Boolean = false
        private set

    /** 在微信进程（主进程）里注册「+」菜单入口。重复调用是安全的。 */
    /** 开关读取容错：微信进程早期 MMKV 可能还没初始化，读配置会抛异常，此时按「开启」处理。 */
    private fun enabled(): Boolean =
        runCatching { Preferences.plusMenuEntryEnabled }.getOrDefault(true)

    fun install(context: Context) {
        if (!enabled()) {
            uninstall()
            status = "已关闭（首页 → 快捷入口可开启）"
            return
        }
        if (installed) return
        runCatching {
            handle = ScriptMenuDispatcher.registerPlusMenu(
                owner = OWNER,
                pluginDir = null,
                title = TITLE,
                iconPath = "",
                front = true,
            ) { activity ->
                openSettings(activity as? Context ?: HostInfo.application)
            }
            if (handle != null) {
                installed = true
                status = "已注入（右上角 + → 微信脚本）"
            } else {
                installed = false
                status = "失败：注册返回 null"
            }
        }.onFailure {
            installed = false
            status = "失败：" + (it.message ?: it.javaClass.simpleName)
            WeLogger.e(TAG, "register plus menu entry failed", it)
        }
        WeLogger.i(TAG, status)
    }

    /** 开关变化后调用：先撤销再按新状态注册。 */
    fun refresh(context: Context) {
        uninstall()
        install(context)
    }

    fun uninstall() {
        val current = handle
        handle = null
        installed = false
        if (current != null) {
            runCatching { ScriptMenuDispatcher.remove(current) }
                .onFailure { WeLogger.w(TAG, "remove plus menu entry failed: ${it.message}") }
        }
    }

    /**
     * 打开脚本设置页（运行在微信进程内）。
     *
     * 先走 `Intent(context, SettingsActivity::class.java)`：微信进程里的 ActivityProxy 会把模块 Activity
     * 重定向到模块 ClassLoader 实例化；失败再退到 ActivityLauncher 的「宿主包名 + 模块类名」代理方式。
     */
    private fun openSettings(context: Context) {
        if (context is Activity && SettingsDialogHost.show(context)) {
            WeLogger.i(TAG, "open settings via dialog host")
            return
        }
        runCatching {
            context.startActivity(
                Intent(context, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            WeLogger.i(TAG, "open settings via module activity")
        }.onFailure { first ->
            WeLogger.w(TAG, "open settings failed, fallback to ActivityLauncher: ${first.message}")
            runCatching { ActivityLauncher.launch(context, SETTINGS_CLASS) }
                .onFailure { WeLogger.e(TAG, "open settings fallback failed", it) }
        }
    }
}
