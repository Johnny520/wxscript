package com.johnny.wxscript.utils

import com.johnny.wxscript.utils.fs.KnownPaths
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃文件记录器。
 *
 * 只做一件事：把未捕获异常写一份文本到 `<moduleData>/crash/`，供「我的 → 日志 → 崩溃文件」查看。
 * 写完**继续交给上一个 handler**，绝不吞掉宿主原本的崩溃处理逻辑。
 */
object CrashRecorder {

    private const val MAX_FILES = 30
    private val timeFmt = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(thread, throwable) }
            if (previous != null) previous.uncaughtException(thread, throwable)
        }
    }

    fun crashDir(): File? = runCatching {
        val dir = File(KnownPaths.moduleData.toFile(), "crash")
        if (!dir.isDirectory && !dir.mkdirs()) return null
        dir
    }.getOrNull()

    fun list(): List<File> = runCatching {
        crashDir()?.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }.getOrDefault(emptyList())

    private fun write(thread: Thread, throwable: Throwable) {
        val dir = crashDir() ?: return
        val now = Date()
        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }
        val content = buildString {
            appendLine("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(now)}")
            appendLine("线程：${thread.name}（id=${thread.id}）")
            appendLine("进程：${hostProcessName()}")
            appendLine("异常：${throwable.javaClass.name}: ${throwable.message}")
            appendLine()
            append(sw.toString())
        }
        File(dir, "crash-${timeFmt.format(now)}.txt").writeText(content)

        // 只保留最近 MAX_FILES 份
        runCatching {
            dir.listFiles()?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_FILES)
                ?.forEach { it.delete() }
        }
    }
}
