package com.johnny.wxscript.utils

import android.app.Application

/**
 * 当前进程名。
 *
 * 模块自身的进程是 `com.johnny.wxscript`，注入微信后是 `com.tencent.mm`（或 :appbrand0 等子进程）。
 * 脚本运行时只在微信主进程内可用，界面据此区分「模块进程」与「宿主进程」。
 */
fun hostProcessName(): String = runCatching { Application.getProcessName().orEmpty() }.getOrDefault("")
