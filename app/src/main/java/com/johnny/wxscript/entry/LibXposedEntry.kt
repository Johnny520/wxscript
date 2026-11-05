package com.johnny.wxscript.entry

import android.app.Application
import android.app.Instrumentation
import android.os.Handler
import android.os.Looper
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.startup.WxScriptStartup
import com.johnny.wxscript.xposed.XC_MethodHook
import com.johnny.wxscript.xposed.XposedBridge
import com.johnny.wxscript.xposed.XposedRuntime
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WxScript 的 libxposed 入口（API 102）。
 *
 * 102 起框架不再允许调用 legacy `de.robv.android.xposed`，所以入口继承 [XposedModule]；
 * 模块内部所有 hook 通过自带兼容层（[XposedBridge] / [XC_MethodHook]）转到 `XposedInterface.hook(...).intercept(...)`。
 *
 * 两点关键：
 * 1. 入口（悬浮按钮）在 [onPackageLoaded] 里就开始挂，不依赖 Application 回调是否命中；
 * 2. Application 装配挂了两个钩子（newApplication / callApplicationOnCreate）+ [onPackageReady] 兜底，
 *    不同 ROM 走哪条不一定。
 */
class LibXposedEntry : XposedModule() {

    companion object {
        private const val TAG = "WxScript"
    }

    private val started = AtomicBoolean(false)

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != BuildConfig.HOST_PACKAGE) return

        try {
            XposedRuntime.attach(this, param.defaultClassLoader)
            XposedRuntime.log(4, "[$TAG] libxposed entry loaded: ${param.packageName}")

            // 入口不依赖 Application 创建：这里就先挂上（LauncherUI.onResume 触发时再插入 View）。
            val hostClassLoader = param.defaultClassLoader
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching {
                    com.johnny.wxscript.features.api.ui.WxScriptFloatingEntry.install(hostClassLoader)
                    XposedRuntime.log(
                        4,
                        "[$TAG] floating entry: " +
                            com.johnny.wxscript.features.api.ui.WxScriptFloatingEntry.status,
                    )
                }.onFailure { XposedRuntime.log(6, "[$TAG] floating entry failed: ${it.message}") }
            }, 2_000L)

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

    /** 包就绪回调（Application 创建前的最后时机），再补一次 hook 兜底。 */
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

    private fun ensureStartup(application: Application) {
        if (!started.compareAndSet(false, true)) return
        runCatching { WxScriptStartup.onApplicationCreated(application) }
            .onFailure {
                XposedRuntime.log(6, "[$TAG] startup failed: ${it.message}")
                XposedRuntime.log(6, android.util.Log.getStackTraceString(it))
            }
    }
}
