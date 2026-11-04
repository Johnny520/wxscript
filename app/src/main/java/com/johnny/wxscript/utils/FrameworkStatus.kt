package com.johnny.wxscript.utils

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import com.johnny.wxscript.BuildConfig

/**
 * 模块自身进程里的框架状态。
 *
 * 通过 libxposed 的 `service` 绑定 LSPosed（与模块用哪套 API 无关），
 * 拿到框架名 / 框架版本 / API 版本 / 作用域，用来在模块主界面显示「是否激活」。
 *
 * 绑定与解绑都在 Binder 线程回调，状态更新统一切回主线程。
 */
object FrameworkStatus {

    data class Snapshot(
        val bound: Boolean = false,
        val frameworkName: String = "",
        val frameworkVersion: String = "",
        val apiVersion: Int = 0,
        val scope: List<String> = emptyList(),
    ) {
        val scopeActive: Boolean get() = scope.contains(BuildConfig.HOST_PACKAGE)
    }

    private val _state = mutableStateOf(Snapshot())
    val state: State<Snapshot> get() = _state

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var registered = false

    /** 幂等：多次调用只会注册一次监听。 */
    fun register() {
        if (registered) return
        registered = true
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    val snapshot = Snapshot(
                        bound = true,
                        frameworkName = service.frameworkName.orEmpty(),
                        frameworkVersion = service.frameworkVersion.orEmpty(),
                        apiVersion = service.apiVersion,
                        scope = runCatching { service.scope.orEmpty() }.getOrDefault(emptyList()),
                    )
                    main.post { _state.value = snapshot }
                    WeLogger.i(TAG, "framework bound: ${snapshot.frameworkName} ${snapshot.frameworkVersion} api=${snapshot.apiVersion}")
                }

                override fun onServiceDied(service: XposedService) {
                    main.post { _state.value = Snapshot() }
                    WeLogger.w(TAG, "framework service died")
                }
            })
        }.onFailure { WeLogger.e(TAG, "register framework listener failed", it) }
    }

    private const val TAG = "FrameworkStatus"
}
