package com.johnny.wxscript.entry

import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.xposed.LegacyHookBackendImpl
import com.johnny.wxscript.xposed.XposedRuntime
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * WxScript 的 legacy 入口（{@code de.robv.android.xposed}，legacy flavor 使用）。
 *
 * 为什么需要它：libxposed modern API（101/102）要求 LSPosed 2.0+ / Vector 2.1+。
 * 官方 LSPosed 1.x 只支持 legacy API（53–93），modern 模块在那种框架上**完全不会被加载**，
 * 表现为「框架里模块开着、作用域也勾了，但微信里什么入口都没有」。
 * legacy flavor 用与 WCX 完全相同的机制，兼容性最好。
 *
 * 入口类名登记在 {@code assets/xposed_init} 里，一个类一行。
 */
class LegacyXposedEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Heartbeat.write(
            "module_loaded",
            "api=legacy process=${lpparam.processName} pkg=${lpparam.packageName}",
        )
        if (lpparam.packageName != BuildConfig.HOST_PACKAGE) return

        try {
            XposedRuntime.attachLegacy(lpparam.classLoader, LegacyHookBackendImpl())
            XposedRuntime.log(4, "[WxScript] legacy entry loaded: ${lpparam.packageName}")
            EntryBootstrap.bootstrap("legacy", lpparam.classLoader)
        } catch (t: Throwable) {
            XposedRuntime.log(6, "[WxScript] legacy entry init failed: ${t.message}")
            XposedRuntime.log(6, android.util.Log.getStackTraceString(t))
            Heartbeat.write("entry init", "failed: ${t.message}")
        }
    }
}
