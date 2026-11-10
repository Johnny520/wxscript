package com.johnny.wxscript.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.loader.utils.ActivityLauncher
import com.johnny.wxscript.utils.WeLogger
import com.johnny.wxscript.utils.hostProcessName
import java.util.concurrent.TimeUnit

/**
 * 从模块进程把设置页「搬进」微信进程打开。
 *
 * 原理与 WCX 一致：启动微信的 SplashActivity 作为代理（它在微信清单里是合法组件），
 * 并在 Intent 里带上目标类名；微信进程内的 [ActivityLauncher] 钩子拦截 onCreate，
 * 换成真正的模块 Activity 启动，从而与脚本运行时处于同一进程、共享同一份配置。
 */
object HostLauncher {

    private const val TAG = "HostLauncher"
    private const val PROXY_ACTIVITY = "com.tencent.mm.app.WeChatSplashActivity"
    private const val SETTINGS_CLASS = "com.johnny.wxscript.activity.SettingsActivity"

    /** 「快速启动」的结果。 */
    data class RestartResult(
        /** 是否成功发出强停指令（需要 Root）。 */
        val stopped: Boolean,
        /** 是否成功拉起微信。 */
        val launched: Boolean,
        /** 面向用户的一句话说明。 */
        val message: String,
    )

    /** @return 是否成功发起了跳转（不代表微信进程内已成功打开）。 */
    fun openSettingsInHost(context: Context): Boolean {
        if (hostProcessName() == BuildConfig.HOST_PACKAGE) {
            // 已经在微信进程里，直接启动本模块 Activity。
            return runCatching {
                context.startActivity(
                    Intent(context, Class.forName(SETTINGS_CLASS)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }.getOrElse { false }
        }

        return runCatching {
            val intent = Intent().apply {
                component = ComponentName(BuildConfig.HOST_PACKAGE, PROXY_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ActivityLauncher.EXTRA_TARGET_CLASS, SETTINGS_CLASS)
            }
            context.startActivity(intent)
            WeLogger.i(TAG, "requested host-process settings activity")
            true
        }.getOrElse {
            WeLogger.e(TAG, "failed to launch host process settings", it)
            false
        }
    }

    /**
     * 强停微信再拉起：让框架重新走一遍注入流程。
     *
     * 强停必须 Root（普通应用无法 force-stop 别的应用），没有 Root 时退化成「只打开微信」。
     * 这个动作本身就是最常用的验证手段：装完新包 / 改完作用域后点一下，再看注入心跳。
     */
    fun forceRestart(context: Context): RestartResult {
        val pkg = BuildConfig.HOST_PACKAGE
        val stopped = stopPackage(pkg)
        if (stopped) {
            // 给系统一点时间回收进程，否则刚杀掉就可能被拉起
            runCatching { Thread.sleep(700L) }
        }

        val launched = runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return@runCatching false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            true
        }.onFailure { WeLogger.e(TAG, "launch host failed", it) }.getOrDefault(false)

        WeLogger.i(TAG, "forceRestart stopped=$stopped launched=$launched")

        val message = when {
            stopped && launched -> "已强停并重新打开微信，等 10~30 秒后回到这里点「注入自检」"
            !stopped && launched -> "未能强停微信（需要 Root），已直接打开微信；请手动在最近任务里划掉微信后重开"
            stopped && !launched -> "已强停微信，但拉起失败：请检查微信是否已安装"
            else -> "强停与拉起都失败：请检查微信是否已安装、Root 是否已授权"
        }
        return RestartResult(stopped, launched, message)
    }

    /** 通过 Root 执行 {@code am force-stop}；无 Root / 未授权 / 超时一律返回 false。 */
    private fun stopPackage(pkg: String): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", "am force-stop $pkg")
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(5, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            WeLogger.w(TAG, "force-stop timed out (su 授权弹窗未响应？)")
            return@runCatching false
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.exitValue()
        WeLogger.i(TAG, "force-stop exit=$code output=${output.take(200)}")
        code == 0
    }.getOrElse {
        WeLogger.w(TAG, "force-stop failed: ${it.message}")
        false
    }
}
