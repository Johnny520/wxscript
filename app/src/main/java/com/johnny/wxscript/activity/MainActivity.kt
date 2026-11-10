package com.johnny.wxscript.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.ui.HostLauncher
import com.johnny.wxscript.ui.WxTheme
import com.johnny.wxscript.utils.FrameworkStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.johnny.wxscript.ui.HostLogViewer
import com.johnny.wxscript.utils.InjectSelfCheck

/**
 * 模块主界面（运行在 WxScript 自己的进程里）。
 *
 * 只显示激活与环境信息，脚本 4 Tab 在微信进程内（微信 → 我 → 设置 → 微信脚本）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FrameworkStatus.register()
        setContent { WxTheme { HomeScreen() } }
    }
}

// ── 配色（与设计稿一致：绿主色 + 浅灰背景 + pastel 卡片）──
private val BG = Color(0xFFF2F5F8)
private val GREEN = Color(0xFF07C160)
private val CARD_WHITE = Color(0xFFFBFCFD)
private val PINK_BG = Color(0xFFFFF2F2)
private val PINK_FG = Color(0xFFE5484D)
private val BLUE_BG = Color(0xFFEAF3FF)
private val BLUE_FG = Color(0xFF3B82F6)
private val GREEN_BG = Color(0xFFE8F7EE)
private val PURPLE_BG = Color(0xFFF3EEFF)
private val PURPLE_FG = Color(0xFF8B5CF6)
private val TEXT_MAIN = Color(0xFF1A1A1A)
private val TEXT_SUB = Color(0xFF8A8F99)

private const val GITHUB_URL = "https://github.com/Johnny520/wxscript"
private const val AFDIAN_URL = "https://afdian.com/a/Johnny520"
private const val DOCS_URL = "https://github.com/Johnny520/wxscript/blob/main/docs/js-scripting/README.md"

/** LSPosed 管理器可能的包名（官方 / 魔改；隐藏管理器后自定义的包名无法枚举）。 */
private val LSPOSED_PACKAGES = listOf(
    "org.lsposed.manager",
    "com.lsposed.manager",
    "io.github.lsposed.manager",
)

private data class HostApp(val installed: Boolean, val versionName: String, val versionCode: Long, val lastUpdate: Long)

@Composable
private fun HomeScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val status by FrameworkStatus.state
    val host = remember { readHostApp(context) }
    var menuOpen by remember { mutableStateOf(false) }
    var launcherHidden by remember { mutableStateOf(!isLauncherEnabled(context)) }

    Column(
        Modifier
            .fillMaxSize()
            .background(BG)
            // Android 15+ 强制 edge-to-edge，这里自己留出状态栏/手势条安全区，
            // 否则标题会被状态栏压住、右上角「⋯」点不到。
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp),
    ) {
        // ── 顶部标题 ──
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "WxScript",
                color = GREEN,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "v${BuildConfig.VERSION_NAME.removePrefix("v")}",
                color = TEXT_SUB,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f).padding(top = 6.dp),
            )
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(34.dp).clickable { menuOpen = true },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = "更多", tint = TEXT_SUB, modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── 激活状态卡 ──
        PastelCard(
            bg = if (status.bound) GREEN_BG else PINK_BG,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(
                    icon = if (status.bound) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    tint = if (status.bound) GREEN else PINK_FG,
                    bg = if (status.bound) Color(0xFFD6F2E0) else Color(0xFFFFDCDC),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (status.bound) "已激活" else "未激活",
                        color = if (status.bound) Color(0xFF0B7A3E) else PINK_FG,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Text(
                        if (status.bound) {
                            "${status.frameworkName.ifBlank { "LSPosed" }} ${status.frameworkVersion} · API ${status.apiVersion}" +
                                if (status.scopeActive) " · 已勾选微信" else " · 未勾选微信"
                        } else {
                            "请在 Xposed 框架中勾选并重启生效"
                        },
                        color = if (status.bound) Color(0xFF0B7A3E).copy(alpha = 0.8f) else PINK_FG.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // ── 两个并排卡 ──
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PastelCard(bg = BLUE_BG, modifier = Modifier.weight(1f), onClick = { openUrl(context, GITHUB_URL) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIcon(Icons.Filled.PlayArrow, BLUE_FG, Color(0xFFD6E8FF))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("GitHub 仓库", color = TEXT_MAIN, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                        Text("获取最新版本", color = TEXT_SUB, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            PastelCard(bg = GREEN_BG, modifier = Modifier.weight(1f), onClick = { openUrl(context, AFDIAN_URL) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIcon(Icons.Filled.Chat, GREEN, Color(0xFFD6F2E0))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("支持作者", color = TEXT_MAIN, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                        Text("爱发电赞助", color = TEXT_SUB, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        // ── 开发文档 ──
        PastelCard(
            bg = PURPLE_BG,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            onClick = { openUrl(context, DOCS_URL) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(Icons.Filled.Description, PURPLE_FG, Color(0xFFE6DBFF))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("开发文档", color = TEXT_MAIN, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("查看脚本编写指南与 API 接口说明", color = TEXT_SUB, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFB9BEC7))
            }
        }

        GroupTitle("快捷操作")

        SectionCard {
            ActionRow(
                icon = Icons.Filled.RestartAlt,
                title = "快速启动",
                subtitle = "强停微信并重新打开（需要 Root），让框架重新注入模块",
                onClick = {
                    val result = HostLauncher.forceRestart(context)
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                },
            )
            Divider()
            ActionRow(
                icon = Icons.Filled.BugReport,
                title = "注入自检",
                subtitle = "检查 flavor / 框架 API / APK 入口元数据 / 注入心跳",
                onClick = {
                    val text = InjectSelfCheck.run(context)
                    android.app.AlertDialog.Builder(context)
                        .setTitle("注入自检")
                        .setMessage(text)
                        .setPositiveButton("关闭", null)
                        .setNeutralButton("复制") { _, _ ->
                            runCatching {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("wxscript-selfcheck", text))
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .show()
                },
            )
            Divider()
            ActionRow(
                icon = Icons.Filled.PlayArrow,
                title = "跳转至宿主",
                subtitle = "在微信中打开脚本设置（微信进程内）",
                onClick = {
                    val ok = HostLauncher.openSettingsInHost(context)
                    if (!ok) Toast.makeText(context, "启动失败：请确认微信已安装且模块已激活", Toast.LENGTH_LONG).show()
                },
            )
            Divider()
            ActionRow(
                icon = Icons.Filled.Info,
                title = "微信侧日志",
                subtitle = if (HostLogViewer.isGranted()) {
                    "查看模块是否真的注入了微信进程（含入口状态）"
                } else {
                    "需要授予「所有文件访问」权限后才能读取"
                },
                onClick = {
                    if (!HostLogViewer.isGranted()) {
                        val activity = context as? android.app.Activity
                        if (activity != null) {
                            HostLogViewer.requestGrant(activity)
                            Toast.makeText(
                                context,
                                "请允许「所有文件访问」，返回后再次点击",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            Toast.makeText(context, "当前界面无法申请权限", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val text = HostLogViewer.summary() + "\n\n" + HostLogViewer.tail()
                        android.app.AlertDialog.Builder(context)
                            .setTitle("微信侧日志")
                            .setMessage(text)
                            .setPositiveButton("关闭", null)
                            .setNeutralButton("复制") { _, _ ->
                                runCatching {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("wxscript-host-log", text))
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .show()
                    }
                },
            )
            Divider()
            ActionRow(
                icon = if (launcherHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                title = if (launcherHidden) "恢复桌面图标" else "隐藏桌面图标",
                subtitle = if (launcherHidden) "从 LSPosed 打开本模块后点这里恢复" else "从桌面启动器中移除该应用图标",
                onClick = {
                    val wantHidden = !launcherHidden
                    if (setLauncherVisible(context, !wantHidden)) {
                        launcherHidden = wantHidden
                        Toast.makeText(
                            context,
                            if (wantHidden) "已隐藏桌面图标（可从 LSPosed 模块页进入本界面）" else "已恢复桌面图标",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        Toast.makeText(context, "操作失败：系统不允许修改组件状态", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            Divider()
            ActionRow(
                icon = Icons.Filled.Update,
                title = "打开 LSPosed",
                subtitle = "框架 ${status.frameworkName.ifBlank { "LSPosed" }} ${status.frameworkVersion}".trim(),
                onClick = { openLsposed(context) },
            )
        }

        GroupTitle("环境信息")

        SectionCard {
            InfoRow(Icons.Filled.Info, "模块版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Divider()
            InfoRow(
                Icons.Filled.Android,
                "宿主版本",
                if (host.installed) "${host.versionName} (${host.versionCode})" else "未安装",
            )
            Divider()
            InfoRow(Icons.Filled.PhoneAndroid, "系统版本", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            Divider()
            InfoRow(
                Icons.Filled.AccessTime,
                "构建时间",
                if (host.lastUpdate > 0) formatTime(host.lastUpdate) else "—",
            )
            Divider()
            InfoRow(Icons.Filled.Download, "设备", "${Build.MANUFACTURER} ${Build.MODEL}")
        }

        Text(
            "本插件仅供学习交流使用，请勿用于非法用途",
            color = Color(0xFFA9AEB8),
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }

    if (menuOpen) {
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text("WxScript") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("作者：Johnny520", style = MaterialTheme.typography.bodySmall)
                    Text("独立微信脚本模块 · BeanShell / JS 双引擎", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "脚本界面在微信内：我 → 设置 → 微信脚本",
                        style = MaterialTheme.typography.bodySmall,
                        color = TEXT_SUB,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    menuOpen = false
                    val text = buildStatusText(status, host, launcherHidden)
                    copyText(context, text)
                    Toast.makeText(context, "已复制状态信息", Toast.LENGTH_SHORT).show()
                }) { Text("复制状态") }
            },
            dismissButton = { TextButton(onClick = { menuOpen = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        color = GREEN,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun PastelCard(
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .background(bg, RoundedCornerShape(16.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 14.dp, vertical = 12.dp)
    Box(base) { content() }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CARD_WHITE, RoundedCornerShape(16.dp))
            .padding(vertical = 2.dp)
    ) { content() }
}

@Composable
private fun CircleIcon(icon: ImageVector, tint: Color, bg: Color) {
    Box(
        Modifier.size(34.dp).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIcon(icon, GREEN, GREEN_BG)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TEXT_MAIN, fontSize = 14.sp)
            Text(subtitle, color = TEXT_SUB, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFB9BEC7))
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIcon(icon, GREEN, GREEN_BG)
        Spacer(Modifier.width(12.dp))
        Text(label, color = TEXT_MAIN, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TEXT_SUB, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 60.dp).background(Color(0xFFEFF1F4)).size(1.dp))
}

private fun readHostApp(context: Context): HostApp = runCatching {
    val info = context.packageManager.getPackageInfo(BuildConfig.HOST_PACKAGE, 0)
    HostApp(true, info.versionName.orEmpty(), info.longVersionCode, info.lastUpdateTime)
}.getOrDefault(HostApp(false, "", 0L, 0L))

private fun formatTime(millis: Long): String =
    runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis)) }
        .getOrDefault("—")

private fun buildStatusText(status: FrameworkStatus.Snapshot, host: HostApp, launcherHidden: Boolean): String = buildString {
    appendLine("WxScript ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("框架：${status.frameworkName.ifBlank { "未连接" }} ${status.frameworkVersion}")
    appendLine("框架 API：${status.apiVersion}")
    appendLine("作用域含微信：${status.scopeActive}")
    appendLine("微信：${if (host.installed) host.versionName + " (" + host.versionCode + ")" else "未安装"}")
    appendLine("系统：Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine("桌面图标已隐藏：$launcherHidden")
    append("设备：${Build.MANUFACTURER} ${Build.MODEL}")
}

private fun copyText(context: Context, text: String) {
    runCatching {
        val manager = context.getSystemService(ClipboardManager::class.java)
        manager?.setPrimaryClip(ClipData.newPlainText("wxscript", text))
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "没有可打开链接的应用", Toast.LENGTH_SHORT).show() }
}

private fun openLsposed(context: Context) {
    val pm = context.packageManager
    for (pkg in LSPOSED_PACKAGES) {
        pm.getLaunchIntentForPackage(pkg)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(launch) }.isSuccess) return
        }
        // getLaunchIntentForPackage 拿不到时，自己拼带 CATEGORY_LAUNCHER 的 Intent
        val fallback = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (pm.resolveActivity(fallback, 0) != null &&
            runCatching { context.startActivity(fallback) }.isSuccess
        ) {
            return
        }
    }
    Toast.makeText(
        context,
        "没找到 LSPosed 管理器：可能被隐藏了（隐藏后包名会变），请从系统设置或快捷方式打开",
        Toast.LENGTH_LONG,
    ).show()
}

// ── 桌面图标显示/隐藏（activity-alias）──

private fun launcherAlias(context: Context): ComponentName =
    ComponentName(context.packageName, "${context.packageName}.LauncherAlias")

private fun isLauncherEnabled(context: Context): Boolean = runCatching {
    context.packageManager.getComponentEnabledSetting(launcherAlias(context)) !=
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
}.getOrDefault(true)

private fun setLauncherVisible(context: Context, visible: Boolean): Boolean = runCatching {
    context.packageManager.setComponentEnabledSetting(
        launcherAlias(context),
        if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )
    true
}.getOrDefault(false)
