package com.johnny.wxscript.startup

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.johnny.wxscript.features.hchat.HchatScriptBootstrap
import com.johnny.wxscript.loader.utils.ActivityLauncher
import com.johnny.wxscript.loader.utils.ActivityProxy
import com.johnny.wxscript.loader.utils.HybridClassLoader
import com.johnny.wxscript.utils.CrashRecorder
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
            runCatching { CrashRecorder.install() }
                .onFailure { WeLogger.e(TAG, "CrashRecorder install failed", it) }
            ensureHiddenApiAccess()
            WeLogger.i(TAG, "WxScript startup in process=${TargetProcesses.currentName}")

            runCatching { ActivityProxy.init(application) }
                .onFailure { WeLogger.e(TAG, "ActivityProxy init failed", it) }
            runCatching { ActivityLauncher.init() }
                .onFailure { WeLogger.e(TAG, "ActivityLauncher init failed", it) }

            // 入口与脚本子系统解耦：入口在最外层先挂上，脚本运行时初始化失败也不影响用户进设置页。
            if (TargetProcesses.isInMain) {
                installEntries(application)
            }

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

    /**
     * 安装微信内入口（悬浮按钮 / 加号菜单）。
     *
     * 全部放在主线程延迟执行：DecorView 与 MMKV 都要等宿主初始化完；且这里不碰 DexKit，
     * 即使脚本子系统完全失败也能正常挂上。
     */
    private fun installEntries(application: Application) {
        Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching {
                com.johnny.wxscript.features.api.ui.WxScriptFloatingEntry.install(application.classLoader)
                WeLogger.i(TAG, "悬浮入口：" + com.johnny.wxscript.features.api.ui.WxScriptFloatingEntry.status)
            }.onFailure { WeLogger.e(TAG, "悬浮入口安装失败", it) }

            runCatching {
                com.johnny.wxscript.features.hchat.WxScriptPlusMenuEntry.install(application)
                WeLogger.i(TAG, "加号入口：" + com.johnny.wxscript.features.hchat.WxScriptPlusMenuEntry.status)
            }.onFailure { WeLogger.e(TAG, "加号入口安装失败", it) }
        }, 3_000L)
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
