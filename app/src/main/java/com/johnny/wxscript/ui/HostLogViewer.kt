package com.johnny.wxscript.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.utils.WeLogger
import java.io.File

/**
 * 读取**微信进程**里模块写下的运行日志。
 *
 * 为什么需要：模块是否真的被注入到微信进程，只有微信进程内的日志能证明。而所有「微信内入口」
 * 本身都可能因为入口代码没跑到而不可见，于是形成死角 —— 用户看不到入口、也就看不到诊断。
 * 这里让模块 App（独立进程）直接读微信的外部数据目录：
 *
 * `/storage/emulated/0/Android/data/com.tencent.mm/{TAG}/logs/*.log`
 *
 * 该目录属于微信，Android 11+ 需要「所有文件访问」权限（MANAGE_EXTERNAL_STORAGE），
 * 所以调用前先确认 [isGranted]，未授权时用 [requestGrant] 引导。
 */
object HostLogViewer {

    private const val TAG = "HostLogViewer"

    private fun logDir(): File = File(
        Environment.getExternalStorageDirectory(),
        "Android/data/${BuildConfig.HOST_PACKAGE}/${BuildConfig.TAG}/logs",
    )

    fun isGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun requestGrant(activity: Activity) {
        runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${activity.packageName}"),
                )
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
            }
            activity.startActivity(intent)
        }.onFailure { WeLogger.e(TAG, "request storage permission failed", it) }
    }

    /** 日志概要：目录是否存在、文件数、最后修改时间、是否出现入口日志。 */
    fun summary(): String {
        val dir = logDir()
        if (!dir.isDirectory) {
            return buildString {
                appendLine("日志目录不存在：")
                appendLine(dir.absolutePath)
                appendLine()
                appendLine("这说明模块**从未在微信进程里跑起来**（不是入口代码问题）。")
                appendLine("请确认：LSPosed 里模块已启用、作用域勾选了微信、并强停微信后重开。")
            }
        }
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        if (files.isEmpty()) {
            return "日志目录存在但没有日志文件：${dir.absolutePath}\n（模块可能刚装，重启微信后再看）"
        }
        val latest = files.first()
        val text = runCatching { latest.readText() }.getOrDefault("")
        val hasEntry = text.contains("entry loaded") || text.contains("libxposed entry")
        val hasStartup = text.contains("WxScript startup")
        return buildString {
            appendLine("日志目录：${dir.absolutePath}")
            appendLine("文件数：${files.size}，最新：${latest.name}（${latest.length() / 1024} KB）")
            appendLine("最后写入：${java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.CHINA).format(latest.lastModified())}")
            appendLine()
            appendLine(if (hasEntry) "✅ 发现模块入口日志（模块确实注入到微信进程了）" else "❌ 没有发现入口日志（模块没有被注入）")
            appendLine(if (hasStartup) "✅ 发现启动日志（脚本子系统已开始装配）" else "❌ 没有发现启动日志")
        }
    }

    /** 最近日志尾部（最多 [maxLines] 行，倒序文件合并）。 */
    fun tail(maxLines: Int = 250): String {
        val dir = logDir()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: return "（没有日志）"
        val sb = StringBuilder()
        for (file in files.take(2)) {
            sb.appendLine("===== ${file.name} =====")
            val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
            sb.appendLine(lines.takeLast(maxLines).joinToString("\n"))
            sb.appendLine()
        }
        return sb.toString()
    }

    fun logDirPath(): String = logDir().absolutePath
}
