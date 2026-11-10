package com.johnny.wxscript.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * hook 后端持有者：同时支持 libxposed（modern API）与 legacy {@code de.robv} API。
 *
 * 两条路线只允许**同一时刻**有一条生效：
 * - standard flavor：框架调用 [com.johnny.wxscript.entry.LibXposedEntry]，走 [attach]；
 * - legacy flavor：框架调用 {@code LegacyXposedEntry}，走 [attachLegacy]。
 *
 * 模块内部两百多处 hook 全部经由 [XposedBridge] 兼容层，所以只要这里选对后端，
 * 上层代码完全不用关心跑在哪套 API 上。
 */
object XposedRuntime {

    private const val TAG = "WxScript"

    @Volatile
    private var xposed: XposedInterface? = null

    @Volatile
    private var legacy: LegacyHookBackend? = null

    @Volatile
    var hostClassLoader: ClassLoader? = null
        private set

    @Volatile
    var frameworkReady: Boolean = false
        private set

    /** 是否运行在 legacy（{@code de.robv}）后端。 */
    val isLegacy: Boolean
        get() = legacy != null

    fun attach(module: XposedInterface, classLoader: ClassLoader) {
        xposed = module
        legacy = null
        hostClassLoader = classLoader
        frameworkReady = true
    }

    fun attachLegacy(classLoader: ClassLoader, backend: LegacyHookBackend) {
        legacy = backend
        xposed = null
        hostClassLoader = classLoader
        frameworkReady = true
    }

    fun api(): XposedInterface? = xposed

    /** legacy 后端；modern 模式为 null。 */
    fun legacy(): LegacyHookBackend? = legacy

    fun require(): XposedInterface = xposed
        ?: throw IllegalStateException("libxposed 未连接：模块入口没有执行（legacy 模式下请勿直接调用 require）")

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
