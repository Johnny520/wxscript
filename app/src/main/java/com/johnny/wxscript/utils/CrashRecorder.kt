package com.johnny.wxscript.utils

import com.johnny.wxscript.BuildConfig
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
        val primary = File(KnownPaths.moduleData.toFile(), "crash")
        val ok = (primary.isDirectory || primary.mkdirs()) && probeWritable(primary)
        val dir = if (ok) primary else fallbackCrashDir() ?: return null
        runCatching {
            com.johnny.wxscript.preferences.WePrefs.putString(
                com.johnny.wxscript.constants.Preferences.HOST_CRASH_DIR,
                dir.absolutePath,
            )
        }
        dir
    }.getOrNull()

    /**
     * 兜底崩溃目录：宿主自己的 external files（微信进程必定可写）。
     *
     * 直拼 `/sdcard/Android/data/<pkg>/WxScript` 在部分 ROM / 高 targetSdk 上会被 scoped storage
     * 拒绝写入，导致「崩溃了但一条日志都没有」；这里保证至少有一份写得出来。
     */
    private fun fallbackCrashDir(): File? = runCatching {
        val base = HostInfo.application.getExternalFilesDir(null) ?: return@runCatching null
        File(base, BuildConfig.TAG + "/crash").apply { if (!isDirectory) mkdirs() }
            .takeIf { it.isDirectory }
    }.getOrNull()

    /** 能建目录不代表能写文件，真写一次探测。 */
    private fun probeWritable(dir: File): Boolean = runCatching {
        val probe = File(dir, ".probe")
        probe.writeText("1")
        probe.delete()
        true
    }.getOrDefault(false)

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
