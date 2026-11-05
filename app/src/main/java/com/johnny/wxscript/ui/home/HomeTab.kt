package com.johnny.wxscript.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxscript.constants.Preferences
import com.johnny.wxscript.features.api.ui.WxScriptFloatingEntry
import com.johnny.wxscript.features.hchat.WxScriptPlusMenuEntry
import com.johnny.wxscript.features.hchat.HchatScriptBootstrap
import com.johnny.wxscript.features.hchat.ScriptFeaturesInstaller
import com.johnny.wxscript.ui.components.DotState
import com.johnny.wxscript.ui.components.StatusDot
import com.johnny.wxscript.ui.rememberPluginSnapshot
import com.johnny.wxscript.ui.script.ScriptData
import com.johnny.wxscript.utils.WeLogger
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.items.script.ScriptPluginRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

/**
 * 「首页」Tab —— 三段就绪度 + DexKit 进度 + 运行中脚本 + 最近日志 + 快捷入口。
 *
 * 按设计取舍：引擎状态**不单独开 Tab**，这里做首页顶部卡片，另外在全局给一条细横幅。
 */
@Composable
fun HomeTab(
    onOpenScripts: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot = rememberPluginSnapshot()

    var tick by remember { mutableIntStateOf(0) }
    var expandedDex by remember { mutableStateOf(false) }
    var recentLog by remember { mutableStateOf<List<String>>(emptyList()) }

    val state = remember(tick) { collectState() }

    LaunchedEffect(Unit) {
        while (true) {
            tick++
            recentLog = withContext(Dispatchers.IO) {
                runCatching { WeLogger.readRecentLines(3).second }.getOrDefault(emptyList())
            }
            delay(3000)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("运行状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                state.rows.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(row.state)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(row.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    row.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (row.state == DotState.FAIL || row.state == DotState.RUNNING) {
                                TextButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            runCatching { HchatScriptBootstrap.retry() }
                                        }
                                        tick++
                                    }
                                }) { Text("重试") }
                            }
                        }
                        if (row.progress != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(
                                    progress = { row.progress },
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${(row.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Text(
                                if (expandedDex) row.detail else row.detail.take(60) + if (row.detail.length > 60) "…" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { expandedDex = !expandedDex },
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "运行中的脚本",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenScripts) { Text("全部") }
                }
                val running = snapshot.plugins.filter { it.running }
                if (running.isEmpty()) {
                    Text(
                        "当前没有脚本在运行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    running.forEach { plugin ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(DotState.OK)
                            Spacer(Modifier.width(8.dp))
                            Text(plugin.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { snapshot.toggle(plugin, false) }) { Text("停止") }
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("快捷入口", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpenScripts) { Text("脚本列表") }
                    TextButton(onClick = onOpenDiagnostics) { Text("诊断 / 一键修复") }
                    TextButton(onClick = onOpenLogs) { Text("日志") }
                }
            }
            HorizontalDivider()
        }

        item {
            var floating by remember { mutableStateOf(Preferences.floatingEntryEnabled) }
            var floatingStatus by remember { mutableStateOf(WxScriptFloatingEntry.status) }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "微信主界面悬浮入口",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "在微信主界面右侧显示「脚本」小按钮（可拖动），不依赖微信任何页面结构，最稳的一条入口",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = floating,
                        onCheckedChange = { checked ->
                            floating = checked
                            Preferences.floatingEntryEnabled = checked
                            floatingStatus = if (checked) "下次回到微信主界面时显示" else "已关闭（回到微信主界面后消失）"
                        },
                    )
                }
                Text(
                    floatingStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }

        item {
            var plusEntry by remember { mutableStateOf(Preferences.plusMenuEntryEnabled) }
            var plusStatus by remember { mutableStateOf(WxScriptPlusMenuEntry.status) }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "微信「+」菜单入口",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "在微信主界面右上角 + 里显示「微信脚本」，随时进入脚本设置页",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = plusEntry,
                        onCheckedChange = { checked ->
                            plusEntry = checked
                            Preferences.plusMenuEntryEnabled = checked
                            scope.launch {
                                withContext(Dispatchers.IO) { WxScriptPlusMenuEntry.refresh(context) }
                                plusStatus = WxScriptPlusMenuEntry.status
                            }
                        },
                    )
                }
                Text(
                    plusStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }

        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "最近日志",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenLogs) { Text("查看全部") }
                }
                if (recentLog.isEmpty()) {
                    Text(
                        "暂无日志",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    recentLog.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private data class ReadyRowUi(
    val name: String,
    val detail: String,
    val state: DotState,
    val progress: Float? = null,
)

private data class HomeState(val rows: List<ReadyRowUi>)

private fun collectState(): HomeState {
    val rows = ArrayList<ReadyRowUi>(3)

    // 1) 脚本引擎
    val bootstrap = runCatching { HchatScriptBootstrap.status }.getOrDefault("未执行")
    val features = runCatching { ScriptFeaturesInstaller.status }.getOrDefault("未执行")
    val engineOk = bootstrap.contains("安装成功") && features.startsWith("已启动")
    rows += ReadyRowUi(
        name = "脚本引擎",
        detail = "引导：$bootstrap\n功能：$features",
        state = when {
            engineOk -> DotState.OK
            bootstrap.contains("失败") || features.contains("失败") -> DotState.FAIL
            else -> DotState.RUNNING
        },
    )

    // 2) 微信 API
    val apiDetail = runCatching { ScriptPluginRuntime.scriptApiReadinessDetail() }.getOrNull()
    rows += ReadyRowUi(
        name = "微信 API",
        detail = apiDetail ?: "数据库 / 联系人 / 账号就绪",
        state = if (apiDetail == null) DotState.OK else DotState.FAIL,
    )

    // 3) DexKit
    val tasks = runCatching { DexInstallScheduler.snapshot() }.getOrDefault(emptyList())
    if (tasks.isEmpty()) {
        rows += ReadyRowUi("DexKit 索引", "调度器尚未启动", DotState.RUNNING, 0f)
    } else {
        val installed = tasks.count { it.installed }
        val runningTask = tasks.firstOrNull { it.running }
        val pending = runCatching { DexInstallScheduler.pendingCount() }.getOrDefault(0)
        val failed = runCatching { DexInstallScheduler.failedCount() }.getOrDefault(0)
        val ratio = installed.toFloat() / tasks.size.toFloat()
        val detail = buildString {
            append("阶段 ").append(runCatching { DexInstallScheduler.readyLevelName() }.getOrDefault("?"))
            append(" · 已装 ").append(installed).append("/").append(tasks.size)
            if (pending > 0) append(" · 待装 ").append(pending)
            if (failed > 0) append(" · 失败 ").append(failed)
            runningTask?.let { append("\n当前：").append(it.label) }
            append("\n").append(runCatching { HchatScriptBootstrap.dexParseInfo() }.getOrDefault(""))
        }
        rows += ReadyRowUi(
            name = "DexKit 索引",
            detail = detail,
            state = when {
                failed > 0 -> DotState.FAIL
                installed == tasks.size -> DotState.OK
                else -> DotState.RUNNING
            },
            progress = ratio,
        )
    }

    return HomeState(rows)
}

/** 全局细横幅：引擎没完全就绪时显示在每个 Tab 顶部。 */
@Composable
fun EngineBanner(onOpenDiagnostics: () -> Unit) {
    val bootstrap = runCatching { HchatScriptBootstrap.status }.getOrDefault("未执行")
    val apiDetail = runCatching { ScriptPluginRuntime.scriptApiReadinessDetail() }.getOrNull()
    val ready = bootstrap.contains("安装成功") && apiDetail == null
    if (ready) return
    val text = when {
        !ScriptData.isHostProcess() -> "未在微信进程内，脚本引擎不可用"
        bootstrap.contains("失败") -> "脚本引擎安装失败：$bootstrap"
        apiDetail != null -> "微信 API 未就绪：$apiDetail"
        else -> "脚本引擎初始化中…"
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(if (bootstrap.contains("失败")) DotState.FAIL else DotState.RUNNING)
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            TextButton(onClick = onOpenDiagnostics) { Text("诊断") }
        }
    }
}
