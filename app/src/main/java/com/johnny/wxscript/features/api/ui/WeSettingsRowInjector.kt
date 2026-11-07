package com.johnny.wxscript.features.api.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.johnny.wxscript.activity.SettingsActivity
import com.johnny.wxscript.constants.Preferences
import com.johnny.wxscript.ui.SettingsDialogHost
import com.johnny.wxscript.utils.WeLogger
import com.johnny.wxscript.utils.hookAfterDirectly

/**
 * 微信「我 → 设置」页里的「微信脚本」入口（View 级注入）。
 *
 * 参考 NukeApp-OSS 的做法（`app/tiktok/settings/SettingsEntry.kt` + `SettingsLayout.kt`）：
 * 它不碰宿主的设置项框架（不需要生成 SettingItem 子类、不需要往 Map 里注册 class），
 * 而是直接 hook **设置页 Activity 的 onCreate**，在布局里插一行自己的 View。
 *
 * 微信 8.0.79 / 8.0.80 的设置页 Activity 类名是**明文**的：
 * `com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI` /
 * `CommonSettingsUI` / `SearchSettingsUI`，共同基类
 * `...setting_new.base.BaseSettingPrefUI` 上有一个明文方法 `onCreate(Bundle)`。
 * hook 这个基类方法即可覆盖所有设置页，不依赖任何混淆名。
 */
object WeSettingsRowInjector {

    private const val TAG = "SettingsRow"
    private const val BASE_PREF_UI =
        "com.tencent.mm.plugin.setting.ui.setting_new.base.BaseSettingPrefUI"
    private const val VIEW_TAG = "wxscript_settings_row"
    private const val SETTINGS_CLASS = "com.johnny.wxscript.activity.SettingsActivity"

    @Volatile
    var status: String = "未执行"
        private set

    @Volatile
    var installed: Boolean = false
        private set

    private fun enabled(): Boolean =
        runCatching { Preferences.floatingEntryEnabled }.getOrDefault(true)

    fun install(classLoader: ClassLoader) {
        if (installed) return
        if (!enabled()) {
            status = "已关闭（首页 → 快捷入口可开启）"
            return
        }
        runCatching {
            val cls = Class.forName(BASE_PREF_UI, false, classLoader)
            val onCreate = cls.declaredMethods.firstOrNull {
                it.name == "onCreate" && it.parameterCount == 1
            } ?: error("BaseSettingPrefUI 没有 onCreate(Bundle)")

            onCreate.isAccessible = true
            onCreate.hookAfterDirectly {
                runCatching { attach(thisObject as? Activity) }
                    .onFailure { WeLogger.w(TAG, "attach failed: ${it.message}") }
            }
            installed = true
            status = "已挂载（设置页底部「微信脚本」卡片）"
        }.onFailure {
            status = "失败：" + (it.message ?: it.javaClass.simpleName)
            WeLogger.e(TAG, "install failed", it)
        }
        WeLogger.i(TAG, status)
    }

    private fun attach(activity: Activity?) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        val decor = activity.window?.decorView as? ViewGroup ?: return
        if (decor.findViewWithTag<View>(VIEW_TAG) != null) return

        val card = buildCard(activity)
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM
            val m = (12 * activity.resources.displayMetrics.density).toInt()
            setMargins(m, 0, m, m)
        }
        runCatching { decor.addView(card, lp) }
            .onFailure { WeLogger.w(TAG, "add row failed: ${it.message}") }
    }

    private fun buildCard(activity: Activity): View {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val card = LinearLayout(activity).apply {
            tag = VIEW_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = 14f * density
                setColor(Color.WHITE)
                setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#1A000000"))
            }
            elevation = 4f * density
            setOnClickListener { openSettings(activity) }
        }

        val icon = TextView(activity).apply {
            text = "脚"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                cornerRadius = 10f * density
                setColor(Color.parseColor("#07C160"))
            }
        }
        card.addView(icon, LinearLayout.LayoutParams(dp(30), dp(30)))

        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        texts.addView(TextView(activity).apply {
            text = "微信脚本"
            setTextColor(Color.parseColor("#1A1A1A"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        texts.addView(TextView(activity).apply {
            text = "点击进入脚本设置 · 插件 / 市场 / 日志"
            setTextColor(Color.parseColor("#8A8F99"))
            textSize = 11f
        })
        card.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        card.addView(TextView(activity).apply {
            text = "›"
            setTextColor(Color.parseColor("#B9BEC7"))
            textSize = 20f
        })

        return card
    }

    private fun openSettings(activity: Activity) {
        if (SettingsDialogHost.show(activity)) {
            WeLogger.i(TAG, "open settings via dialog host")
            return
        }
        runCatching {
            activity.startActivity(Intent(activity, SettingsActivity::class.java))
        }.onFailure {
            WeLogger.e(TAG, "open settings failed: ${it.message}", it)
            runCatching { activity.startActivity(Intent().setClassName(activity, SETTINGS_CLASS)) }
        }
    }
}
