package com.johnny.wxscript.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.loader.utils.ActivityLauncher
import com.johnny.wxscript.utils.WeLogger
import com.johnny.wxscript.utils.hostProcessName

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
}
