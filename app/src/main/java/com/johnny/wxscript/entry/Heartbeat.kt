package com.johnny.wxscript.entry

import android.app.ActivityThread
import android.os.Environment
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.constants.Preferences
import com.johnny.wxscript.preferences.WePrefs
import com.johnny.wxscript.xposed.XposedRuntime
import java.io.File

/**
 * 注入心跳。
 *
 * 模块被框架装进微信进程的第一时间写一行（modern 走 onModuleLoaded，legacy 走 handleLoadPackage），
 * 文件落在微信外部数据目录；模块 App 用「所有文件访问」权限读取。
 *
 * 作用：把「模块到底有没有被注入」与「入口代码有没有 bug」彻底分开——
 * 文件不存在 = 框架没装进来（作用域/框架版本/API 不匹配）；文件存在 = 装进来了，继续看后面几行。
 *
 * 写入路径有两个候选：优先宿主自己的 external files（微信进程必定可写），
 * 失败再退回直拼的 `Android/data/<pkg>/WxScript`（部分 ROM 的 scoped storage 会拒绝它）。
 * 实际可写的那个会回填到 prefs，供模块 App 直接定位。
 */
object Heartbeat {

    private const val FILE_NAME = "module_loaded.txt"

    /** 微信进程可能写心跳的目录（按可靠性排序）。 */
    private fun candidateDirs(): List<File> {
        val dirs = ArrayList<File>(2)
        runCatching {
            val app = ActivityThread.currentApplication()
            app?.getExternalFilesDir(null)?.let { dirs.add(File(it, BuildConfig.TAG + "/logs")) }
        }
        dirs.add(
            File(
                Environment.getExternalStorageDirectory(),
                "Android/data/" + BuildConfig.HOST_PACKAGE + "/" + BuildConfig.TAG + "/logs",
            )
        )
        return dirs
    }

    fun write(stage: String, detail: String) {
        val stamp = runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                .format(java.util.Date())
        }.getOrDefault("?")
        val line = "[$stamp] $stage | $detail | ${BuildConfig.FLAVOR_SLUG} v${BuildConfig.VERSION_NAME}\n"

        for (dir in candidateDirs()) {
            val ok = runCatching {
                if (!dir.isDirectory) dir.mkdirs()
                if (!dir.isDirectory) return@runCatching false
                File(dir, FILE_NAME).appendText(line)
                true
            }.getOrDefault(false)
            if (ok) {
                runCatching { WePrefs.putString(Preferences.HOST_LOG_DIR, dir.absolutePath) }
                return
            }
        }
        XposedRuntime.log(6, "[Heartbeat] write failed for: $stage ($detail)")
    }

    /** 当前心跳内容（诊断页直接展示，null 表示读不到）。 */
    fun read(): String? = candidateDirs()
        .map { File(it, FILE_NAME) }
        .firstOrNull { it.isFile }
        ?.let { runCatching { it.readText() }.getOrNull() }
        ?.takeIf { it.isNotBlank() }
}
