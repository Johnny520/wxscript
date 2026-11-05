package com.johnny.wxscript.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.compose.ui.platform.ComposeView
import com.johnny.wxscript.activity.WxScriptRoot
import com.johnny.wxscript.utils.WeLogger

/**
 * 在**当前微信 Activity 之上**用全屏 Dialog 显示脚本主界面。
 *
 * 为什么不总是用 SettingsActivity：模块 Activity 在微信进程内运行依赖 ActivityProxy（hook
 * Instrumentation/IActivityManager）与微信代理组件，任一环节在特定微信版本/分身场景下失效，
 * 用户就会「点了没反应」。Dialog 只用到 `Activity` 本身，不经过 Activity 启动链路，成功率最高，
 * 因此作为悬浮入口的首选打开方式，启动 Activity 则作为次级方案。
 */
object SettingsDialogHost {

    private const val TAG = "SettingsDialog"

    @Volatile
    private var current: Dialog? = null

    fun isShowing(): Boolean = current?.isShowing == true

    fun show(activity: Activity?): Boolean {
        if (activity == null || activity.isFinishing) return false
        if (isShowing()) return true
        return runCatching {
            // ComponentDialog 自带 Lifecycle / SavedState / ViewModelStore 三个 owner，
            // 省掉手动给 decorView 挂 ViewTree*Owner（也避免依赖 lifecycle 的具体包路径）。
            val dialog = ComponentDialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
            val content = ComposeView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setContent { WxTheme { WxScriptRoot() } }
            }
            dialog.setContentView(content)
            dialog.setCancelable(true)
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            dialog.setOnDismissListener { current = null }
            dialog.show()

            current = dialog
            WeLogger.i(TAG, "settings dialog shown on ${activity.javaClass.name}")
            true
        }.getOrElse {
            WeLogger.e(TAG, "show settings dialog failed", it)
            false
        }
    }

    fun dismiss() {
        runCatching { current?.dismiss() }
        current = null
    }

    /** 便于在非 Activity 上下文里判断能否用 Dialog（必须 Activity）。 */
    fun isUsable(context: Context?): Boolean = context is Activity && !context.isFinishing
}
