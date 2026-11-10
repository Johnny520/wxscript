package com.johnny.wxscript.features.api.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.johnny.wxscript.activity.SettingsActivity
import com.johnny.wxscript.constants.Preferences
import com.johnny.wxscript.loader.utils.ActivityLauncher
import com.johnny.wxscript.ui.SettingsDialogHost
import com.johnny.wxscript.utils.HostInfo
import com.johnny.wxscript.utils.WeLogger
import com.johnny.wxscript.utils.hookAfterDirectly

/**
 * 微信主界面悬浮入口。
 *
 * 为什么要这一条：设置页注入（[WeSettingsInjector]）、右上角「+」菜单（[com.johnny.wxscript.features.hchat.WxScriptPlusMenuEntry]）、
 * 模块 App 的「跳转至宿主」都依赖微信某个页面/组件结构，任一锚点漂移就整条失效，用户会「什么入口都没有」。
 *
 * 这里的做法不依赖任何微信页面结构：Hook `com.tencent.mm.ui.LauncherUI` 的 onResume（这个类名从 7.x 到 8.0.8x 一直没变），
 * 直接把一个自绘的小按钮塞进当前 Activity 的 decorView —— 不需要悬浮窗权限（不是 WindowManager 全局窗，只是 Activity 内的普通子 View），
 * 模块只要在微信进程里加载成功，这个按钮就一定看得到。
 *
 * 按钮可拖动、位置记忆，点击直接进脚本设置页；默认开启，可在模块 App「首页 → 快捷入口」关闭。
 */
object WxScriptFloatingEntry {

    private const val TAG = "FloatingEntry"
    private const val LAUNCHER_UI = "com.tencent.mm.ui.LauncherUI"
    private const val SETTINGS_CLASS = "com.johnny.wxscript.activity.SettingsActivity"
    private const val VIEW_TAG = "wxscript_floating_entry"
    private const val POS_X_KEY = "floating_entry_x_ratio"
    private const val POS_Y_KEY = "floating_entry_y_ratio"

    @Volatile
    var status: String = "未执行"
        private set

    @Volatile
    var installed: Boolean = false
        private set

    @Volatile
    private var toastShown = false

    /** 开关读取容错：微信进程早期 MMKV 可能还没初始化，读配置会抛异常，此时按「开启」处理。 */
    private fun enabled(): Boolean =
        runCatching { Preferences.floatingEntryEnabled }.getOrDefault(true)

    fun install(classLoader: ClassLoader) {
        if (!enabled()) {
            status = "已关闭（首页 → 快捷入口可开启）"
            return
        }
        if (installed) return
        runCatching {
            val cls = Class.forName(LAUNCHER_UI, false, classLoader)
            var hooked = false
            // onResume 每次回前台都会走，比 onCreate 更可靠（微信常驻后台、冷启动路径也可能变）。
            // onResume 可能声明在父类里，所以沿父类链找。
            var cursor: Class<*>? = cls
            while (cursor != null && !hooked) {
                cursor.declaredMethods
                    .filter { it.name == "onResume" && it.parameterCount == 0 }
                    .forEach { method ->
                        if (hooked) return@forEach
                        runCatching {
                            method.isAccessible = true
                            method.hookAfterDirectly {
                                runCatching { attachTo(thisObject as? Activity) }
                            }
                            hooked = true
                        }
                    }
                cursor = cursor.superclass
            }
            if (!hooked) {
                status = "失败：LauncherUI 没有可 hook 的 onResume"
            } else {
                installed = true
                status = "已挂载（微信主界面右侧「脚本」按钮）"
            }
        }.onFailure {
            status = "失败：" + (it.message ?: it.javaClass.simpleName)
            WeLogger.e(TAG, "install floating entry failed", it)
        }
        WeLogger.i(TAG, status)
    }

    private fun attachTo(activity: Activity?) {
        if (activity == null || activity.isFinishing) return
        if (!enabled()) return
        val decor = activity.window?.decorView as? ViewGroup ?: return
        if (decor.findViewWithTag<View>(VIEW_TAG) != null) return

        val ctx = activity
        val density = ctx.resources.displayMetrics.density
        val button = TextView(ctx).apply {
            tag = VIEW_TAG
            text = "脚本"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 18f * density
                setColor(Color.parseColor("#07C160"))
            }
            val px = (10 * density).toInt()
            val py = (6 * density).toInt()
            setPadding(px, py, px, py)
            elevation = 6f * density
            setOnClickListener { openSettings(activity) }
        }

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            rightMargin = (6 * density).toInt()
            topMargin = (Preferences.floatingEntryYPercent / 100f * decor.height).toInt()
        }

        button.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startTop = 0
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startTop = lp.topMargin
                        moved = false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - downY
                        if (kotlin.math.abs(dy) > 8 * density || kotlin.math.abs(event.rawX - downX) > 8 * density) {
                            moved = true
                        }
                        if (moved) {
                            val maxTop = (decor.height - v.height).coerceAtLeast(0)
                            lp.topMargin = (startTop + dy.toInt()).coerceIn(0, maxTop)
                            v.layoutParams = lp
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            Preferences.floatingEntryYPercent =
                                if (decor.height > 0) (lp.topMargin.toFloat() / decor.height * 100).toInt().coerceIn(0, 100) else 55
                            return true
                        }
                    }
                }
                return false
            }
        })

        button.setOnLongClickListener {
            showDiagnostics(activity)
            true
        }

        runCatching { decor.addView(button, lp) }
            .onFailure { WeLogger.w(TAG, "add floating view failed: ${it.message}") }

        if (!toastShown) {
            toastShown = true
            runCatching {
                Toast.makeText(ctx, "WxScript 入口已就绪（右侧「脚本」按钮，长按看诊断）", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 长按悬浮按钮：在没有入口/进不去脚本页时也能看到诊断（可复制发反馈）。 */
    private fun showDiagnostics(activity: Activity) {
        val text = buildString {
            appendLine("WxScript 诊断")
            appendLine("悬浮入口：" + status)
            appendLine("+ 菜单入口：" + runCatching { com.johnny.wxscript.features.hchat.WxScriptPlusMenuEntry.status }.getOrDefault("?"))
            appendLine("设置页入口：" + runCatching { WeSettingsInjector.status }.getOrDefault("?"))
            appendLine("脚本引擎：" + runCatching { com.johnny.wxscript.features.hchat.HchatScriptBootstrap.status }.getOrDefault("?"))
            appendLine("日志目录：/storage/emulated/0/Android/data/com.tencent.mm/" + com.johnny.wxscript.BuildConfig.TAG + "/logs")
        }
        runCatching {
            android.app.AlertDialog.Builder(activity)
                .setTitle("WxScript 诊断")
                .setMessage(text)
                .setPositiveButton("知道了", null)
                .setNeutralButton("复制") { _, _ ->
                    runCatching {
                        val cm = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("wxscript", text))
                        Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }.onFailure { WeLogger.e(TAG, "show diagnostics failed", it) }
    }

    /** 统一走 [ScriptUiLauncher]（真 Activity 优先，Dialog 兜底）。 */
    private fun openSettings(activity: Activity) {
        com.johnny.wxscript.ui.ScriptUiLauncher.open(activity, "floating-button")
    }
}
