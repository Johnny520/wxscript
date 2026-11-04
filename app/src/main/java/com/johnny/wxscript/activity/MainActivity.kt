package com.johnny.wxscript.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.ui.HostLauncher
import com.johnny.wxscript.ui.WxTheme
import com.johnny.wxscript.utils.FrameworkStatus

/**
 * 模块主界面（运行在 WxScript 自己的进程里）。
 *
 * 这里**只放激活信息**：框架是否绑定、框架版本与 API、作用域是否包含微信、
 * 模块/微信/系统版本，以及「在微信中打开设置」的入口。
 *
 * 脚本插件界面（4 个 Tab）全部在微信进程内渲染，从微信「我 → 设置 → 微信脚本」进入，
 * 因为它依赖微信进程里的运行时（Xposed hook、微信 API、DexKit）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FrameworkStatus.register()
        setContent {
            WxTheme { HomeScreen() }
        }
    }
}

private const val GITHUB_URL = "https://github.com/Johnny520/wxscript"
private const val AFDIAN_URL = "https://afdian.com/a/Johnny520"
/** LSPosed 管理器可能的包名（官方 / 魔改 / 隐藏管理器后自定义的包名无法枚举）。 */
private val LSPOSED_PACKAGES = listOf(
    "org.lsposed.manager",
    "com.lsposed.manager",
    "io.github.lsposed.manager",
)

private data class HostApp(val installed: Boolean, val versionName: String, val versionCode: Long)

@Composable
private fun HomeScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val status by FrameworkStatus.state
    val host = remember { readHostApp(context) }
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // ── 顶部渐变头 ──
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(scheme.primary, scheme.primary.copy(alpha = 0.72f), scheme.tertiary)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 26.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("W", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("WxScript", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "微信脚本模块 · ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    StatusPill(bound = status.bound)
                }
            }
        }

        // ── 框架状态 ──
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant.copy(alpha = 0.45f)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (status.bound) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (status.bound) Color(0xFF2E7D32) else scheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (status.bound) "已激活" else "未激活",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    if (status.bound) {
                        "已与框架建立连接，微信内的脚本功能可用。"
                    } else {
                        "没有连到 LSPosed 服务：请在 LSPosed 中启用本模块、勾选「微信」作用域，然后重启微信。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                HorizontalDivider()
                InfoRow("框架", status.frameworkName.ifBlank { "—" } + if (status.frameworkVersion.isNotBlank()) " ${status.frameworkVersion}" else "")
                InfoRow("框架 API", if (status.apiVersion > 0) "${status.apiVersion}" else "—")
                InfoRow(
                    "作用域",
                    when {
                        !status.bound -> "—"
                        status.scopeActive -> "已勾选微信"
                        else -> "未勾选微信（需要勾选后重启微信）"
                    },
                )
            }
        }

        // ── 环境信息 ──
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant.copy(alpha = 0.45f)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("环境", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                InfoRow("模块版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow(
                    "微信版本",
                    if (host.installed) "${host.versionName} (${host.versionCode})" else "未安装",
                )
                InfoRow("系统", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                InfoRow("设备", "${Build.MANUFACTURER} ${Build.MODEL}")
            }
        }

        // ── 操作 ──
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val ok = HostLauncher.openSettingsInHost(context)
                    if (!ok) Toast.makeText(context, "启动失败：请确认微信已安装且模块已激活", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("在微信中打开设置")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { openLsposed(context) },
                    modifier = Modifier.weight(1f),
                ) { Text("LSPosed") }
                OutlinedButton(
                    onClick = {
                        val text = buildStatusText(status, host)
                        copyText(context, text)
                        Toast.makeText(context, "已复制状态信息", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("复制状态") }
            }
        }

        // ── 关于 ──
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant.copy(alpha = 0.45f)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("关于作者", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Johnny520", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "独立微信脚本模块：BeanShell / JS 双引擎、插件热重载、在线插件市场。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { openUrl(context, GITHUB_URL) }) { Text("GitHub") }
                    TextButton(onClick = { openUrl(context, AFDIAN_URL) }) { Text("支持作者") }
                }
            }
        }

        // ── 提示 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                "脚本插件界面在微信内：微信 → 我 → 设置 → 微信脚本。\n" +
                    "那里才有微信进程的运行时（插件加载、微信 API、DexKit）。",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(bound: Boolean) {
    Surface(
        color = if (bound) Color(0xFF2E7D32) else Color(0xFFB3261E),
        shape = CircleShape,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(Color.White, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(if (bound) "已激活" else "未激活", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

private fun readHostApp(context: android.content.Context): HostApp = runCatching {
    val info = context.packageManager.getPackageInfo(BuildConfig.HOST_PACKAGE, 0)
    HostApp(true, info.versionName.orEmpty(), info.longVersionCode)
}.getOrDefault(HostApp(false, "", 0L))

private fun buildStatusText(status: FrameworkStatus.Snapshot, host: HostApp): String = buildString {
    appendLine("WxScript ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("框架：${status.frameworkName.ifBlank { "未连接" }} ${status.frameworkVersion}")
    appendLine("框架 API：${status.apiVersion}")
    appendLine("作用域含微信：${status.scopeActive}")
    appendLine("微信：${if (host.installed) host.versionName + " (" + host.versionCode + ")" else "未安装"}")
    appendLine("系统：Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
    append("设备：${Build.MANUFACTURER} ${Build.MODEL}")
}

private fun copyText(context: android.content.Context, text: String) {
    runCatching {
        val manager = context.getSystemService(ClipboardManager::class.java)
        manager?.setPrimaryClip(ClipData.newPlainText("wxscript", text))
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "没有可打开链接的应用", Toast.LENGTH_SHORT).show() }
}

private fun openLsposed(context: android.content.Context) {
    val pm = context.packageManager
    for (pkg in LSPOSED_PACKAGES) {
        // 1) 优先用启动 Intent（ACTION_MAIN 必须带 CATEGORY_LAUNCHER 才能匹配到桌面入口）
        pm.getLaunchIntentForPackage(pkg)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(launch) }.isSuccess) return
        }
        // 2) 兜底：自己拼一个带 CATEGORY_LAUNCHER 的 Intent
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
        "没找到 LSPosed 管理器：可能被隐藏了（LSPosed 里「隐藏应用」后包名会变），请从系统设置或快捷方式打开",
        Toast.LENGTH_LONG,
    ).show()
}
