package com.johnny.wxscript.startup

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.johnny.wxscript.features.hchat.HchatScriptBootstrap
import com.johnny.wxscript.loader.utils.ActivityLauncher
import com.johnny.wxscript.loader.utils.ActivityProxy
import com.johnny.wxscript.loader.utils.HybridClassLoader
import com.johnny.wxscript.utils.HostInfo
import com.johnny.wxscript.utils.TargetProcesses
import com.johnny.wxscript.utils.WeLogger
import com.johnny.wxscript.utils.reflection.ClassLoaders
import dev.ujhhgtg.reflekt.utils.ReflectionClassLoader
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Field

/**
 * 宿主进程（微信）内的启动装配。
 *
 * 顺序（照抄 WCX 的 StartupAgent，去掉与 WxScript 无关的部分）：
 * 1. 建立 HybridClassLoader / reflekt 的宿主 ClassLoader —— 模块类里直接写 `com.tencent.mm.*`
 *    的地方全靠它解析；
 * 2. [HostInfo.init]；
 * 3. HiddenApi 豁免（反射微信私有成员需要）；
 * 4. ActivityProxy / ActivityLauncher（模块 Activity 在微信进程内打开）；
 * 5. 后台线程安装脚本运行时（[HchatScriptBootstrap]），**绝不能在主线程做 dex 解析**。
 */
object WxScriptStartup {

    private const val TAG = "Startup"

    @Volatile
    private var initialized = false

    fun onApplicationCreated(application: Application) {
        if (initialized) return
        initialized = true

        try {
            initClassLoaders(application)
            HostInfo.init(application)
            ensureHiddenApiAccess()
            WeLogger.i(TAG, "WxScript startup in process=${TargetProcesses.currentName}")

            runCatching { ActivityProxy.init(application) }
                .onFailure { WeLogger.e(TAG, "ActivityProxy init failed", it) }
            runCatching { ActivityLauncher.init() }
                .onFailure { WeLogger.e(TAG, "ActivityLauncher init failed", it) }

            if (!TargetProcesses.isInMain) {
                WeLogger.i(TAG, "skip script bootstrap in non-main process: ${TargetProcesses.currentName}")
                return
            }

            val thread = HandlerThread("WxScript-Bootstrap").apply { start() }
            Handler(thread.looper).post {
                try {
                    HchatScriptBootstrap.install()
                } catch (t: Throwable) {
                    WeLogger.e(TAG, "script bootstrap failed", t)
                }
            }
        } catch (t: Throwable) {
            WeLogger.e(TAG, "startup failed", t)
        }
    }

    /**
     * 让模块 ClassLoader 的父链挂上 HybridClassLoader，从而既能加载宿主类（com.tencent.mm.*），
     * 也能加载模块类。WCX 用 reflekt 直接改 `ClassLoader.parent`，这里用原生反射实现同样效果。
     */
    private fun initClassLoaders(application: Application) {
        val hostClassLoader = application.baseContext?.classLoader ?: application.classLoader
        HybridClassLoader.hostClassLoader = hostClassLoader
        ReflectionClassLoader.value = hostClassLoader

        val moduleClassLoader = ClassLoaders.MODULE
        val moduleParent = moduleClassLoader.parent
        if (moduleParent !== HybridClassLoader) {
            HybridClassLoader.moduleParentClassLoader = moduleParent ?: ClassLoader.getSystemClassLoader()
            runCatching {
                val parentField: Field = ClassLoader::class.java.getDeclaredField("parent")
                parentField.isAccessible = true
                parentField.set(moduleClassLoader, HybridClassLoader)
            }.onFailure {
                WeLogger.e(TAG, "failed to replace module ClassLoader parent", it)
            }
        }
    }

    private fun ensureHiddenApiAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (isHiddenApiAccessible()) return
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions("L")
            WeLogger.i(TAG, "HiddenApiBypass installed")
        }.onFailure { WeLogger.e(TAG, "HiddenApiBypass failed", it) }
    }

    private fun isHiddenApiAccessible(): Boolean {
        val kContextImpl = runCatching { Class.forName("android.app.ContextImpl") }.getOrElse { return false }
        val hasActivityToken = runCatching { kContextImpl.getDeclaredField("mActivityToken") }.isSuccess
        val hasToken = runCatching { kContextImpl.getDeclaredField("mToken") }.isSuccess
        return hasActivityToken || hasToken
    }
}
