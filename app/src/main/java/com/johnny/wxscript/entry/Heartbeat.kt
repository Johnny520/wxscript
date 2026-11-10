package com.johnny.wxscript.entry

import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.xposed.XposedRuntime

/**
 * 注入心跳。
 *
 * 模块被框架装进微信进程的第一时间写一行（modern 走 onModuleLoaded，legacy 走 handleLoadPackage），
 * 文件落在微信自己的外部数据目录，模块 App 用「所有文件访问」权限读取。
 *
 * 作用：把「模块到底有没有被注入」与「入口代码有没有 bug」这两件事彻底分开——
 * 文件不存在 = 框架没装进来（作用域/框架版本/API 不匹配）；文件存在 = 装进来了，继续看后面几行。
 */
object Heartbeat {

    private const val FILE_NAME = "module_loaded.txt"

    fun write(stage: String, detail: String) {
        runCatching {
            val dir = java.io.File(
                android.os.Environment.getExternalStorageDirectory(),
                "Android/data/" + BuildConfig.HOST_PACKAGE + "/" + BuildConfig.TAG,
            )
            if (!dir.isDirectory) dir.mkdirs()
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                .format(java.util.Date())
            java.io.File(dir, FILE_NAME).appendText(
                "[$stamp] $stage | $detail | ${BuildConfig.FLAVOR_SLUG} v" +
                    BuildConfig.VERSION_NAME + "\n",
            )
        }.onFailure {
            XposedRuntime.log(6, "[Heartbeat] write failed: ${it.message}")
        }
    }

    /** 当前心跳文件（用于诊断页直接展示，null 表示读不到）。 */
    fun read(): String? = runCatching {
        val dir = java.io.File(
            android.os.Environment.getExternalStorageDirectory(),
            "Android/data/" + BuildConfig.HOST_PACKAGE + "/" + BuildConfig.TAG,
        )
        val file = java.io.File(dir, FILE_NAME)
        if (file.isFile) file.readText() else null
    }.getOrNull()
}
