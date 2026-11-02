package com.johnny.wxscript

import android.app.Application
import android.util.Log

/**
 * 模块自身的 Application（运行在 WxScript 自己的进程中）。
 *
 * 注意：微信进程内的初始化走 [com.johnny.wxscript.entry.XposedEntry]，
 * 不在这里做，避免把宿主进程当成模块进程处理。
 */
class WxScriptApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(BuildConfig.TAG, "WxScript app started, version=${BuildConfig.VERSION_NAME}")
    }
}
