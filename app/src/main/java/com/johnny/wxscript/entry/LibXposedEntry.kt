package com.johnny.wxscript.entry

import android.app.Application
import android.os.Handler
import android.os.Looper
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

            // 入口不依赖 Application 创建：这里就先挂上（LauncherUI.onResume 触发时再插入 View）。
            // 之前只靠下面的 Application hook，一旦 hook 未命中就「什么入口都没有」，无法区分
            // 「模块没加载」和「入口代码失败」，所以把能提前做的都提前做。
            val classLoader = param.defaultClassLoader
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching {
                    com.johnny.wxscript.features.api.ui.WxScriptFloatingEntry.install(classLoader)
                    XposedRuntime.log(4, "[$TAG] floating entry: " + com.johnny.wxscript.features.api.ui.WxScriptFloatingEntry.status)
                }.onFailure { XposedRuntime.log(6, "[$TAG] floating entry failed: ${it.message}") }
            }, 2_000L)

            // Application 有两个钩子点，任一命中都能完成装配（不同 ROM / 微信版本走的路径不同）。
            // newApplication 在 Application 实例刚创建时返回，callApplicationOnCreate 在 onCreate 前调用。
            XposedBridge.hookAllMethods(
                Instrumentation::class.java,
                "newApplication",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val application = param.result as? Application ?: return
                        ensureStartup(application)
                    }
                },
            )
            XposedBridge.hookAllMethods(
                Instrumentation::class.java,
                "callApplicationOnCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val application = param.args.getOrNull(0) as? Application ?: return
                        ensureStartup(application)
                    }
                },
            )
        } catch (t: Throwable) {
            XposedRuntime.log(6, "[$TAG] entry init failed: ${t.message}")
            XposedRuntime.log(6, android.util.Log.getStackTraceString(t))
        }
    }

    /** 装配只执行一次（newApplication / callApplicationOnCreate 可能都触发）。 */
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun ensureStartup(application: Application) {
        if (!started.compareAndSet(false, true)) return
        runCatching { WxScriptStartup.onApplicationCreated(application) }
            .onFailure {
                XposedRuntime.log(6, "[$TAG] startup failed: ${it.message}")
                XposedRuntime.log(6, android.util.Log.getStackTraceString(it))
            }
    }

    /**
     * 包就绪回调（Application 创建前的最后时机）。这里再做一次 hook 兜底：
     * 某些 ROM 上 onPackageLoaded 阶段 hook 系统类会失败，换到 ready 阶段就能成功。
     */
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != BuildConfig.HOST_PACKAGE) return
        runCatching {
            XposedBridge.hookAllMethods(
                Instrumentation::class.java,
                "callApplicationOnCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val application = param.args.getOrNull(0) as? Application ?: return
                        ensureStartup(application)
                    }
                },
            )
            XposedRuntime.log(4, "[$TAG] package ready hook installed: ${param.packageName}")
        }.onFailure { XposedRuntime.log(6, "[$TAG] package ready hook failed: ${it.message}") }
    }
}
