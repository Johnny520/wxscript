package com.johnny.wxscript.entry

import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.xposed.XposedRuntime
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * WxScript 的 libxposed 入口（modern API，standard flavor 使用）。
 *
 * 只负责「拿到宿主 ClassLoader 并交给 [EntryBootstrap]」，
 * 真正的 Hook 全部走 [com.johnny.wxscript.xposed.XposedBridge] 兼容层，与 legacy flavor 共用一套代码。
 *
 * legacy flavor 的入口见 `src/legacy/java/.../LegacyXposedEntry.kt`，它走 {@code assets/xposed_init}。
 */
class LibXposedEntry : XposedModule() {

    companion object {
        private const val TAG = "WxScript"
    }

    /**
     * 模块被框架注入进程的**第一个**回调（先于 onPackageLoaded）。
     *
     * 在这里落心跳是判断「模块到底有没有被注入」的最可靠手段：
     * 之后任何入口没出现，都可能是入口代码自己的问题；但这个文件只要不存在，
     * 就说明框架根本没把模块装进微信进程。
     */
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        Heartbeat.write(
            "module_loaded",
            "api=modern process=${param.processName} systemServer=${param.isSystemServer}",
        )
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        Heartbeat.write(
            "package_loaded",
            "api=modern package=${param.packageName} first=${param.isFirstPackage}",
        )
        if (param.packageName != BuildConfig.HOST_PACKAGE) return

        try {
            XposedRuntime.attach(this, param.defaultClassLoader)
            XposedRuntime.log(4, "[$TAG] libxposed entry loaded: ${param.packageName}")
            EntryBootstrap.bootstrap("modern", param.defaultClassLoader)
        } catch (t: Throwable) {
            XposedRuntime.log(6, "[$TAG] entry init failed: ${t.message}")
            XposedRuntime.log(6, android.util.Log.getStackTraceString(t))
            Heartbeat.write("entry init", "failed: ${t.message}")
        }
    }

    /**
     * 包就绪回调（Application 创建前的最后时机）。
     *
     * 部分 ROM 的包加载顺序会让 onPackageLoaded 早于 Instrumentation 实例可用，
     * 这里再补一次装配（[EntryBootstrap] 内部不重复挂界面，只补 Instrumentation 与启动）。
     */
    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != BuildConfig.HOST_PACKAGE) return
        Heartbeat.write("package_ready", "api=modern package=${param.packageName}")
        runCatching {
            if (!XposedRuntime.frameworkReady) {
                XposedRuntime.attach(this, param.defaultClassLoader)
            }
            EntryBootstrap.bootstrap("modern-ready", param.classLoader, withEntries = false)
        }.onFailure {
            XposedRuntime.log(6, "[$TAG] package ready bootstrap failed: ${it.message}")
        }
    }
}
