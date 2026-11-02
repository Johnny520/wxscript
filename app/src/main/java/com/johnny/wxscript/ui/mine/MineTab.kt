package com.johnny.wxscript.ui.mine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.constants.Preferences
import com.johnny.wxscript.preferences.WePrefs
import com.johnny.wxscript.ui.components.DotState
import com.johnny.wxscript.ui.components.SectionHeader
import com.johnny.wxscript.ui.components.StatusDot
import com.johnny.wxscript.utils.CrashRecorder
import com.johnny.wxscript.utils.HostInfo
import com.johnny.wxscript.utils.WeLogger
import com.johnny.wxscript.utils.hostProcessName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 「我的」Tab：宿主信息 / 调试开关 / 诊断与日志入口 / 关于。 */
@Composable
fun MineTab(
    onOpenDiagnostics: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenApiDocs: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var verbose by remember {
        mutableStateOf(runCatching { WePrefs.getBoolOrFalse(Preferences.VERBOSE_LOG) }.getOrDefault(false))
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item { SectionHeader("宿主") }
        item {
            InfoRow("当前进程", hostProcessName())
            InfoRow("宿主包名", runCatching { HostInfo.packageName }.getOrDefault("未在微信进程内"))
            InfoRow(
                "微信版本",
                runCatching { "${HostInfo.versionName} (${HostInfo.versionCode})" }.getOrDefault("未知"),
            )
        }

        item { SectionHeader("调试") }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("详细日志", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "打开后 D/V 级日志也会写文件，排查脚本问题时更完整",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = verbose, onCheckedChange = {
                    verbose = it
                    runCatching { WePrefs.default.putBoolean(Preferences.VERBOSE_LOG, it) }
                })
            }
            HorizontalDivider()
        }

        item { SectionHeader("工具") }
        item { ActionRow("诊断中心", "三段式自检 + 一键修复（执行前先列出计划）", onOpenDiagnostics) }
        item { ActionRow("日志", "实时控制台 + 崩溃文件", onOpenLogs) }
        item { ActionRow("插件 API 文档", "脚本可用的 Java / JS 接口说明", onOpenApiDocs) }
        item {
            ActionRow("导出日志摘要", "复制最近日志的问题摘要到剪贴板") {
                scope.launch {
                    val text = withContext(Dispatchers.IO) {
                        runCatching { WeLogger.digest().toShareText() }.getOrElse { "读取日志失败：${it.message}" }
                    }
                    copyToClipboard(context, text)
                    Toast.makeText(context, "已复制日志摘要", Toast.LENGTH_SHORT).show()
                }
            }
        }
        item {
            ActionRow("复制模块信息", "版本 / 进程 / 崩溃文件数") {
                val info = buildString {
                    appendLine("WxScript ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("进程：${hostProcessName()}")
                    appendLine("崩溃文件：${CrashRecorder.list().size} 个")
                }
                copyToClipboard(context, info)
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
        }

        item { SectionHeader("关于") }
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(DotState.OK)
                    Spacer(Modifier.width(8.dp))
                    Text("WxScript", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "独立微信脚本模块 · 作者 Johnny520\n与 WCX 主模块相互独立，可同时安装。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        item {
            ActionRow("项目主页", "github.com/Johnny520/wxscript") {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Johnny520/wxscript"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("wxscript", text))
    }
}
