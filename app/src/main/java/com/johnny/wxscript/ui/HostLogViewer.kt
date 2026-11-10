package com.johnny.wxscript.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.constants.Preferences
import com.johnny.wxscript.preferences.WePrefs
import com.johnny.wxscript.utils.WeLogger
import java.io.File

/**
 * 读取**微信进程**里模块写下的运行日志 / 崩溃文件 / 注入心跳。
 *
 * 为什么需要：模块是否真的被注入到微信进程、入口有没有挂上、打开设置页崩在哪一步，
 * 只有微信进程内的日志能证明。而所有「微信内入口」本身都可能失效，于是形成死角。
 *
 * 日志可能落在两个位置（微信进程写日志时会自动选择可写的那个）：
 * 1. `/storage/emulated/0/Android/data/com.tencent.mm/{TAG}/logs`（**主路径**）
 * 2. `/storage/emulated/0/Android/data/com.tencent.mm/files/{TAG}/logs`（主路径写不进去时的兜底）
 *
 * Android 11+ 读这些目录需要「所有文件访问」权限（MANAGE_EXTERNAL_STORAGE），
 * 所以调用前先确认 [isGranted]，未授权时用 [requestGrant] 引导。
 */
object HostLogViewer {

    private const val TAG = "HostLogViewer"

    /** 微信进程可能写入的目录（按优先级）。 */
    private fun candidateDirs(): List<File> {
        val root = Environment.getExternalStorageDirectory()
        val host = BuildConfig.HOST_PACKAGE
        val tag = BuildConfig.TAG
        val dirs = linkedSetOf<File>()
        // 微信进程写日志后会把自己实际用的目录回填到 prefs
        runCatching {
            WePrefs.getString(Preferences.HOST_LOG_DIR)?.takeIf { it.isNotBlank() }
        }.getOrNull()?.let { dirs.add(File(it)) }
        dirs.add(File(root, "Android/data/$host/files/$tag/logs"))
        dirs.add(File(root, "Android/data/$host/$tag/logs"))
        return dirs.toList()
    }

    /** 实际目录：第一个存在且含日志文件的；都不含则返回第一个存在的；都不存在返回主路径（用于展示）。 */
    private fun resolvedDir(): File {
        val dirs = candidateDirs()
        dirs.firstOrNull { it.isDirectory && hasLogFiles(it) }?.let { return it }
        dirs.firstOrNull { it.isDirectory }?.let { return it }
        return dirs.last()
    }

    private fun hasLogFiles(dir: File): Boolean =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }?.isNotEmpty() == true

    /** 所有存在的日志目录（用于展示「到底哪些路径写过东西」）。 */
    fun existingDirs(): List<Pair<File, Int>> = candidateDirs().mapNotNull { dir ->
        if (!dir.isDirectory) return@mapNotNull null
        val count = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }?.size ?: 0
        dir to count
    }

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

    /** 注入心跳文件（模块被框架装进微信进程的第一时间写），在所有候选目录里找。 */
    private fun heartbeatText(): String? = candidateDirs()
        .map { File(it.parentFile, "module_loaded.txt") }
        .firstOrNull { it.isFile }
        ?.let { runCatching { it.readText() }.getOrNull() }
        ?.takeIf { it.isNotBlank() }

    /** 微信进程侧崩溃目录。 */
    fun hostCrashDir(): File? = runCatching {
        WePrefs.getString(Preferences.HOST_CRASH_DIR)?.takeIf { it.isNotBlank() }
            ?.let { File(it).takeIf(File::isDirectory) }
            ?: File(Environment.getExternalStorageDirectory(), "Android/data/${BuildConfig.HOST_PACKAGE}/files/${BuildConfig.TAG}/crash")
                .takeIf { it.isDirectory }
    }.getOrNull()

    /** 微信进程侧崩溃文件（新的在前）。 */
    fun hostCrashFiles(limit: Int = 10): List<File> = runCatching {
        hostCrashDir()?.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.take(limit)
            .orEmpty()
    }.getOrDefault(emptyList())

    /** 日志概要：注入判定 + 目录命中情况 + 入口日志是否存在。 */
    fun summary(): String {
        val heartbeat = heartbeatText()
        val injected = heartbeat != null
        val header = buildString {
            appendLine("【注入判定】" + if (injected) "✅ 模块已被注入微信进程" else "❌ 没有找到注入心跳")
            if (injected) {
                appendLine(heartbeat!!.trim().lines().takeLast(8).joinToString("\n"))
            } else {
                appendLine("→ 说明框架没有把模块装进微信进程（或心跳文件所在目录读不到）。逐项核对：")
                appendLine("  1. LSPosed → 模块 → WxScript：开关是否打开")
                appendLine("  2. 作用域是否勾选了你正在用的那个微信（分身是另一个条目）")
                appendLine("  3. 框架版本与 flavor 是否匹配（standard 需要 LSPosed 2.0+）")
                appendLine("  4. 改完后用「快速启动」强停微信再打开")
            }
            appendLine()
            val dirs = existingDirs()
            if (dirs.isEmpty()) {
                appendLine("【日志目录】一个都不存在：")
                candidateDirs().forEach { appendLine("  · ${it.absolutePath}") }
                appendLine("（微信进程从没写成功过日志；也可能是没授予「所有文件访问」）")
                appendLine()
            } else {
                appendLine("【日志目录】")
                dirs.forEach { (dir, count) ->
                    appendLine("  · ${dir.absolutePath} —— $count 个日志文件")
                }
                appendLine()
            }
        }

        val dir = resolvedDir()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        if (files.isEmpty()) {
            return header + "日志目录里还没有日志文件：${dir.absolutePath}\n（模块刚装的话，重启微信后再看）"
        }
        val latest = files.first()
        val text = runCatching { latest.readText() }.getOrDefault("")
        val hasEntry = text.contains("entry loaded") || text.contains("libxposed entry") || text.contains("legacy entry")
        val hasStartup = text.contains("WxScript startup")
        return header + buildString {
            appendLine("最新日志：${latest.name}（${latest.length() / 1024} KB）")
            appendLine("最后写入：" + java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.CHINA)
                .format(latest.lastModified()))
            appendLine(if (hasEntry) "✅ 有入口日志" else "❌ 没有入口日志")
            appendLine(if (hasStartup) "✅ 有启动日志" else "❌ 没有启动日志")
        }
    }

    /** 最近日志尾部（合并最近两个日志文件）。 */
    fun tail(maxLines: Int = 250): String {
        val dir = resolvedDir()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: return "（没有日志）"
        if (files.isEmpty()) return "（没有日志）"
        val sb = StringBuilder()
        for (file in files.take(2)) {
            sb.appendLine("===== ${file.name} =====")
            val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
            sb.appendLine(lines.takeLast(maxLines).joinToString("\n"))
            sb.appendLine()
        }
        return sb.toString()
    }

    fun logDirPath(): String = resolvedDir().absolutePath
}
