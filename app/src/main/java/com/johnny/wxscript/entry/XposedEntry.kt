package com.johnny.wxscript.entry

import android.app.Application
import android.os.Handler
import android.os.Looper
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

            val classLoader = lpparam.classLoader
            // 入口尽早挂：不依赖 Application 回调是否命中（之前只靠它，一旦没命中就「什么入口都没有」）。
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching {
                    com.johnny.wxscript.features.api.ui.WxScriptFloatingEntry.install(classLoader)
                    XposedBridge.log(
                        "[$TAG] floating entry: " + com.johnny.wxscript.features.api.ui.WxScriptFloatingEntry.status
                    )
                }.onFailure { XposedBridge.log("[$TAG] floating entry failed: ${it.message}") }
            }, 2_000L)

            // 宿主的 Application 创建完成后装配。两个钩子任一命中即可（不同 ROM 走的路径不同）。
            val startupHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val application = param.args.getOrNull(0) as? Application
                        ?: param.result as? Application
                        ?: return
                    runCatching { WxScriptStartup.onApplicationCreated(application) }
                        .onFailure {
                            XposedBridge.log("[$TAG] startup failed: ${it.message}")
                            XposedBridge.log(it)
                        }
                }
            }
            runCatching {
                XposedHelpers.findAndHookMethod(
                    Instrumentation::class.java,
                    "callApplicationOnCreate",
                    Application::class.java,
                    startupHook,
                )
            }.onFailure { XposedBridge.log("[$TAG] hook callApplicationOnCreate failed: ${it.message}") }
            runCatching {
                XposedHelpers.findAndHookMethod(
                    Instrumentation::class.java,
                    "newApplication",
                    ClassLoader::class.java,
                    String::class.java,
                    android.content.Context::class.java,
                    startupHook,
                )
            }.onFailure { XposedBridge.log("[$TAG] hook newApplication failed: ${it.message}") }
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] init failed: ${t.message}")
            XposedBridge.log(t)
        }
    }
}
