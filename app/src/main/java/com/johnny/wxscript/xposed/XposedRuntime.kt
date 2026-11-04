package com.johnny.wxscript.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * libxposed 接口持有者。
 *
 * 入口（[com.johnny.wxscript.entry.LibXposedEntry]）在 `onPackageLoaded` 里调用 [attach]
 * 把当前模块实例交进来，之后所有 hook / 日志都通过这里转到 libxposed。
 */
object XposedRuntime {

    private const val TAG = "WxScript"

    @Volatile
    private var xposed: XposedInterface? = null

    @Volatile
    var hostClassLoader: ClassLoader? = null
        private set

    @Volatile
    var frameworkReady: Boolean = false
        private set

    fun attach(module: XposedInterface, classLoader: ClassLoader) {
        xposed = module
        hostClassLoader = classLoader
        frameworkReady = true
    }

    fun api(): XposedInterface? = xposed

    fun require(): XposedInterface = xposed
        ?: throw IllegalStateException("libxposed 未连接：模块入口没有执行（检查 LSPosed 是否支持 API 102）")

    fun log(priority: Int, message: String) {
        val module = xposed
        if (module != null) {
            runCatching { module.log(priority, TAG, message) }
                .onFailure { Log.println(priority, TAG, message) }
        } else {
            Log.println(priority, TAG, message)
        }
    }
}
