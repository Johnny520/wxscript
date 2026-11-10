package com.johnny.wxscript.entry

import android.app.Application
import android.app.Instrumentation
import android.os.Handler
import android.os.Looper
import com.johnny.wxscript.xposed.XC_MethodHook
import com.johnny.wxscript.xposed.XposedBridge
import com.johnny.wxscript.xposed.XposedRuntime

/**
 * 入口公共部分：两套 API（libxposed / legacy）拿到宿主 ClassLoader 后做的事完全一致。
 *
 * 顺序刻意这样排：
 * 1. 先挂入口界面（悬浮按钮 + 设置页卡片）——不依赖 Application 是否创建成功，
 *    这样即便微信的 Application 装配异常，用户也能看到入口；
 * 2. 再挂 Instrumentation，等 Application 创建后跑启动流程（脚本运行时、设置代理等）。
 */
object EntryBootstrap {

    private const val TAG = "WxScript"
    private const val ENTRY_DELAY_MS = 2_000L

    fun bootstrap(flavor: String, classLoader: ClassLoader, withEntries: Boolean = true) {
        XposedRuntime.log(4, "[$TAG] ($flavor) bootstrap start")

        if (withEntries) Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                com.johnny.wxscript.features.api.ui.WxScriptFloatingEntry.install(classLoader)
                val status = com.johnny.wxscript.features.api.ui.WxScriptFloatingEntry.status
                XposedRuntime.log(4, "[$TAG] floating entry: $status")
                Heartbeat.write("floating entry", status)
            }.onFailure {
                XposedRuntime.log(6, "[$TAG] floating entry failed: ${it.message}")
                Heartbeat.write("floating entry", "failed: ${it.message}")
            }

            runCatching {
                com.johnny.wxscript.features.api.ui.WeSettingsRowInjector.install(classLoader)
                val status = com.johnny.wxscript.features.api.ui.WeSettingsRowInjector.status
                XposedRuntime.log(4, "[$TAG] settings row: $status")
                Heartbeat.write("settings row", status)
            }.onFailure {
                XposedRuntime.log(6, "[$TAG] settings row failed: ${it.message}")
                Heartbeat.write("settings row", "failed: ${it.message}")
            }
        }, ENTRY_DELAY_MS)

        runCatching {
            XposedBridge.hookAllMethods(
                Instrumentation::class.java,
                "newApplication",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val application = param.result as? Application ?: return
                        ensureStartup(application, "newApplication")
                    }
                },
            )
            XposedBridge.hookAllMethods(
                Instrumentation::class.java,
                "callApplicationOnCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val application = param.args.getOrNull(0) as? Application ?: return
                        ensureStartup(application, "callApplicationOnCreate")
                    }
                },
            )
            XposedRuntime.log(4, "[$TAG] ($flavor) instrumentation hooks installed")
            Heartbeat.write("instrumentation hooks", "ok")
        }.onFailure {
            XposedRuntime.log(6, "[$TAG] instrumentation hook failed: ${it.message}")
            Heartbeat.write("instrumentation hooks", "failed: ${it.message}")
        }
    }

    /**
     * Application 创建后跑一次启动流程。
     *
     * 微信有多个进程共用同一个 Application 类，靠 [com.johnny.wxscript.startup.WxScriptStartup]
     * 内部判断主进程；这里不额外去重，重复进入由 Startup 自己保证幂等。
     */
    fun ensureStartup(application: Application, from: String) {
        runCatching {
            XposedRuntime.log(4, "[$TAG] startup via $from")
            Heartbeat.write("startup", "from=$from")
            com.johnny.wxscript.startup.WxScriptStartup.onApplicationCreated(application)
        }.onFailure {
            XposedRuntime.log(6, "[$TAG] startup failed: ${it.message}")
            XposedRuntime.log(6, android.util.Log.getStackTraceString(it))
            Heartbeat.write("startup", "failed: ${it.message}")
        }
    }
}
