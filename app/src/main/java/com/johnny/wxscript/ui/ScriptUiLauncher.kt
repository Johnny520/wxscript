package com.johnny.wxscript.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.johnny.wxscript.activity.SettingsActivity
import com.johnny.wxscript.loader.utils.ActivityLauncher
import com.johnny.wxscript.utils.WeLogger

/**
 * 打开「脚本设置」界面的唯一入口（微信设置页的卡片、+ 菜单、悬浮球全部走这里）。
 *
 * 顺序与 WCX 一致：**先真 Activity，再全屏 Dialog**。
 *
 * 为什么真 Activity 优先：脚本界面是 Compose 写的，在微信自己的 Activity 上挂一个全屏
 * Compose Dialog，任何一个 Tab 的初始化异常都会让微信进程直接崩掉（用户看到「黑屏闪退」）；
 * 而真 Activity 有完整的生命周期与主题环境，稳定性高得多（ActivityProxy 负责绕过
 * 「模块 Activity 不在微信清单里」的系统校验）。
 *
 * 每一步都写日志：即便最后还是崩，日志里也能看到走到了哪一步。
 */
object ScriptUiLauncher {

    private const val TAG = "ScriptUiLauncher"
    private const val SETTINGS_CLASS = "com.johnny.wxscript.activity.SettingsActivity"

    /** @return 是否成功发起了打开动作（不代表界面已经画出来）。 */
    fun open(activity: Activity?, from: String): Boolean {
        if (activity == null || activity.isFinishing) {
            WeLogger.w(TAG, "open($from): activity unavailable")
            return false
        }
        WeLogger.i(TAG, "open from=$from on ${activity.javaClass.name}")

        // 1) 直接在本进程内启动模块 Activity —— 由 ActivityProxy 拦截 manifest 校验。
        runCatching {
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
            WeLogger.i(TAG, "opened via direct activity")
            return true
        }.onFailure { WeLogger.e(TAG, "direct activity failed", it) }

        // 2) 微信自己的 Activity 作代理（SplashActivity 在微信清单里合法）。
        runCatching {
            ActivityLauncher.launch(activity, SETTINGS_CLASS)
            WeLogger.i(TAG, "opened via proxy activity")
            return true
        }.onFailure { WeLogger.e(TAG, "proxy activity failed", it) }

        // 3) 兜底：全屏 Dialog。
        if (SettingsDialogHost.show(activity)) {
            WeLogger.i(TAG, "opened via dialog host")
            return true
        }

        WeLogger.e(TAG, "all open strategies failed")
        runCatching {
            Toast.makeText(activity, "打开脚本设置失败，请到 WxScript App 查看日志", Toast.LENGTH_LONG).show()
        }
        return false
    }
}
