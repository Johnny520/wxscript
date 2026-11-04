package com.johnny.wxscript.entry

import android.app.Application
import android.app.Instrumentation
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.startup.WxScriptStartup
import com.johnny.wxscript.xposed.XC_MethodHook
import com.johnny.wxscript.xposed.XposedBridge
import com.johnny.wxscript.xposed.XposedRuntime
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * WxScript 的 libxposed 入口（API 102）。
 *
 * 102 起框架不再允许调用 legacy `de.robv.android.xposed`，所以入口改成继承
 * [XposedModule]；模块内部所有 hook 通过自带的兼容层（[XposedBridge] /
 * [XC_MethodHook]）转到 `XposedInterface.hook(...).intercept(...)`。
 *
 * 只处理微信：其它包在 LSPosed 里本来就选不进作用域，这里再判一次更稳。
 */
class LibXposedEntry : XposedModule() {

    companion object {
        private const val TAG = "WxScript"
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != BuildConfig.HOST_PACKAGE) return

        try {
            XposedRuntime.attach(this, param.defaultClassLoader)
            XposedRuntime.log(4, "[$TAG] libxposed entry loaded: ${param.packageName}")

            // 宿主 Application 创建完成后再装配：此时 baseContext / classLoader 都已就绪。
            XposedBridge.hookAllMethods(
                Instrumentation::class.java,
                "callApplicationOnCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val application = param.args.getOrNull(0) as? Application ?: return
                        runCatching { WxScriptStartup.onApplicationCreated(application) }
                            .onFailure {
                                XposedRuntime.log(6, "[$TAG] startup failed: ${it.message}")
                                XposedRuntime.log(6, android.util.Log.getStackTraceString(it))
                            }
                    }
                },
            )
        } catch (t: Throwable) {
            XposedRuntime.log(6, "[$TAG] entry init failed: ${t.message}")
            XposedRuntime.log(6, android.util.Log.getStackTraceString(t))
        }
    }
}
