package com.johnny.wxscript.entry

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.johnny.wxscript.BuildConfig

/**
 * WxScript 的 Xposed 入口。
 *
 * 与 WCX 主模块完全独立：不同的 APK、不同的 applicationId、不同的数据目录，
 * 两个模块可同时安装并在 LSPosed 中分别勾选。
 *
 * 当前阶段（P1）：只做加载确认与骨架初始化，脚本运行时在 P2 接入。
 */
class XposedEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "WxScript"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != BuildConfig.HOST_PACKAGE) return

        try {
            XposedBridge.log("[$TAG] loaded into ${lpparam.packageName} (process=${lpparam.processName})")
            // P2 起在这里装配：HostInfo / DexKit 桥 / 微信 API 层 / 脚本运行时
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] init failed: ${t.message}")
            XposedBridge.log(t)
        }
    }
}
