package com.johnny.wxscript.entry

import android.app.Application
import android.app.Instrumentation
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.startup.WxScriptStartup
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * WxScript 的 Xposed 入口（legacy API 82）。
 *
 * 与 WCX 主模块完全独立：不同的 APK、不同的 applicationId、不同的数据目录，
 * 两个模块可同时安装并在 LSPosed 中分别勾选。
 */
class XposedEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "WxScript"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != BuildConfig.HOST_PACKAGE) return

        try {
            XposedBridge.log("[$TAG] loaded into ${lpparam.packageName} (process=${lpparam.processName})")
            // 宿主的 Application 创建完成后再装配：此时 baseContext / classLoader 都已就绪。
            XposedHelpers.findAndHookMethod(
                Instrumentation::class.java,
                "callApplicationOnCreate",
                Application::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val application = param.args.getOrNull(0) as? Application ?: return
                        runCatching { WxScriptStartup.onApplicationCreated(application) }
                            .onFailure {
                                XposedBridge.log("[$TAG] startup failed: ${it.message}")
                                XposedBridge.log(it)
                            }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] init failed: ${t.message}")
            XposedBridge.log(t)
        }
    }
}
