package com.johnny.wxscript.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.johnny.wxscript.features.hchat.HchatScriptBootstrap
import com.johnny.wxscript.features.hchat.ScriptFeaturesInstaller
import com.johnny.wxscript.ui.components.DotState
import com.johnny.wxscript.features.api.ui.WeSettingsInjector
import com.johnny.wxscript.ui.market.MarketData
import com.johnny.wxscript.ui.components.StatusDot
import com.johnny.wxscript.ui.script.ScriptData
import com.johnny.wxscript.utils.WeLogger
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.items.script.ScriptPluginRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.johnny.wxscript.constants.Preferences
import com.johnny.wxscript.features.hchat.WxScriptPlusMenuEntry
import com.johnny.wxscript.features.api.ui.WxScriptFloatingEntry

/** 诊断项：名称 / 状态 / 详情。 */
private data class DiagItem(val name: String, val state: DotState, val detail: String)

/**
 * 诊断页。
 *
 * 设计取舍：**「一键修复」执行前先列出将要做什么**，用户点确认才动手；
 * 执行时逐项显示结果，最后给纯文本报告（可复制）。
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tick by remember { mutableIntStateOf(0) }
    var plan by remember { mutableStateOf<List<String>?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<String?>(null) }
    var probing by remember { mutableStateOf(false) }
    var marketProbe by remember { mutableStateOf<String?>(null) }

    val items = remember(tick) { collectDiagnostics(context) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text("脚本诊断", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(items.size) { index ->
                val item = items[index]
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    StatusDot(item.state, Modifier.padding(top = 5.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            item.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { tick++ }) { Text("重新检测") }
                    OutlinedButton(
                        enabled = !running,
                        onClick = { plan = REPAIR_PLAN },
                    ) { Text(if (running) "修复中…" else "一键修复") }
                    OutlinedButton(
                        enabled = !probing,
                        onClick = {
                            probing = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { MarketData.probeHealth(context) }
                                marketProbe = result.fold(
                                    onSuccess = { "市场可用：$it" },
                                    onFailure = { "市场不可用：" + (it.message ?: it.javaClass.simpleName) },
                                )
                                probing = false
                            }
                        },
                    ) { Text(if (probing) "探测中…" else "探测市场") }
                }
                if (progress.isNotBlank()) {
                    Text(
                        progress,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                marketProbe?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                TextButton(onClick = {
                    scope.launch {
                        val text = withContext(Dispatchers.IO) { buildReport(context, items) }
                        report = text
                    }
                }, modifier = Modifier.padding(top = 6.dp)) { Text("生成诊断报告") }
            }
        }
    }

    plan?.let { steps ->
        AlertDialog(
            onDismissRequest = { plan = null },
            title = { Text("将要执行以下操作") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                    Text(
                        "修复过程不会删除任何插件文件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    plan = null
                    running = true
                    scope.launch {
                        val lines = ArrayList<String>()
                        steps.forEach { step ->
                            progress = "执行中：$step"
                            val result = runCatching { runRepairStep(context, step) }
                            lines += (if (result.isSuccess) "✅ " else "❌ ") + step +
                                (result.exceptionOrNull()?.let { "（${it.message ?: it.javaClass.simpleName}）" } ?: "")
                        }
                        progress = ""
                        running = false
                        tick++
                        report = "修复结果：\n" + lines.joinToString("\n")
                    }
                }) { Text("开始修复") }
            },
            dismissButton = { TextButton(onClick = { plan = null }) { Text("取消") } },
        )
    }

    report?.let { text ->
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text("诊断报告") },
            text = { Text(text, style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { report = null }) { Text("关闭") } },
        )
    }
}

private val REPAIR_PLAN = listOf(
    "补建插件目录",
    "打开脚本插件总开关",
    "重跑脚本运行时引导（DexKit + 微信 API）",
    "强制推进就绪阶段并重排未装 Hook 任务",
    "重载已启用的插件",
)

private fun runRepairStep(context: android.content.Context, step: String) {
    when (step) {
        "补建插件目录" -> {
            val dir = ScriptPluginRuntime.scriptDir(context)
            if (!dir.isDirectory) {
                check(dir.mkdirs()) { "创建失败：${dir.absolutePath}" }
            }
        }
        "打开脚本插件总开关" -> {
            ScriptPluginRuntime.setGlobalEnabled(context, true).getOrThrow()
        }
        "重跑脚本运行时引导（DexKit + 微信 API）" -> {
            HchatScriptBootstrap.retry()
        }
        "强制推进就绪阶段并重排未装 Hook 任务" -> {
            DexInstallScheduler.ensureWarmupReady()
            DexInstallScheduler.retryAllPending()
        }
        "重载已启用的插件" -> {
            ScriptData.listPlugins(context).filter { it.enabled }.forEach { plugin ->
                ScriptData.reloadPlugin(context, plugin.id)
            }
        }
        else -> error("未知修复步骤：$step")
    }
}

private fun collectDiagnostics(context: android.content.Context): List<DiagItem> {
    val items = ArrayList<DiagItem>()

    val host = ScriptData.isHostProcess()
    items += DiagItem(
        "运行进程",
        if (host) DotState.OK else DotState.FAIL,
        if (host) "微信进程内，脚本运行时可访问" else "当前在模块自身进程，所有运行时能力都不可用",
    )

    val dirResult = runCatching {
        val dir = ScriptPluginRuntime.scriptDir(context)
        if (!dir.isDirectory) dir.mkdirs()
        dir
    }
    items += DiagItem(
        "插件目录",
        if (dirResult.getOrNull()?.isDirectory == true) DotState.OK else DotState.FAIL,
        dirResult.fold(
            onSuccess = { "${it.absolutePath}（${it.listFiles()?.size ?: 0} 项）" },
            onFailure = { it.message ?: "不可用" },
        ),
    )

    val global = runCatching { ScriptData.isGlobalEnabled(context) }.getOrDefault(false)
    items += DiagItem(
        "插件总开关",
        if (global) DotState.OK else DotState.RUNNING,
        if (global) "已开启" else "已关闭（修复会自动打开）",
    )

    val bootstrap = runCatching { HchatScriptBootstrap.status }.getOrDefault("未执行")
    items += DiagItem(
        "脚本运行时引导",
        when {
            bootstrap.contains("失败") -> DotState.FAIL
            bootstrap.contains("安装成功") -> DotState.OK
            else -> DotState.RUNNING
        },
        bootstrap,
    )

    items += DiagItem(
        "脚本引擎功能",
        if (runCatching { ScriptFeaturesInstaller.status }.getOrDefault("").startsWith("已启动")) DotState.OK else DotState.RUNNING,
        runCatching { ScriptFeaturesInstaller.status }.getOrDefault("未执行"),
    )

    val apiDetail = runCatching { ScriptPluginRuntime.scriptApiReadinessDetail() }.getOrNull()
    items += DiagItem(
        "微信数据库 API",
        if (apiDetail == null) DotState.OK else DotState.FAIL,
        apiDetail ?: "rcontact / chatroom 等表可用",
    )

    val tasks = runCatching { DexInstallScheduler.snapshot() }.getOrDefault(emptyList())
    val pending = runCatching { DexInstallScheduler.pendingCount() }.getOrDefault(0)
    val failed = runCatching { DexInstallScheduler.failedCount() }.getOrDefault(0)
    val installed = tasks.count { it.installed }
    items += DiagItem(
        "DexKit Hook 任务",
        when {
            tasks.isEmpty() -> DotState.RUNNING
            failed > 0 -> DotState.FAIL
            pending > 0 -> DotState.RUNNING
            else -> DotState.OK
        },
        if (tasks.isEmpty()) "调度器尚未启动"
        else "已装 $installed/${tasks.size} · 待装 $pending · 失败 $failed · " +
            runCatching { DexInstallScheduler.readyLevelName() }.getOrDefault("?"),
    )

    val plugins = runCatching { ScriptData.listPlugins(context) }.getOrDefault(emptyList())
    val enabledCount = plugins.count { it.enabled }
    val runningCount = plugins.count { it.running }
    items += DiagItem(
        "本地插件",
        when {
            plugins.isEmpty() -> DotState.IDLE
            runningCount < enabledCount -> DotState.RUNNING
            else -> DotState.OK
        },
        if (plugins.isEmpty()) {
            "插件目录下没有插件"
        } else {
            "${plugins.size} 个（启用 $enabledCount · 运行中 $runningCount · 市场安装 ${plugins.count { it.fromMarket }}）"
        },
    )

    items += DiagItem(
        "微信设置入口",
        when {
            WeSettingsInjector.installedKey != null -> DotState.OK
            WeSettingsInjector.status.startsWith("失败") -> DotState.FAIL
            else -> DotState.RUNNING
        },
        WeSettingsInjector.status,
    )

    items += DiagItem(
        "微信悬浮入口",
        when {
            WxScriptFloatingEntry.installed -> DotState.OK
            !Preferences.floatingEntryEnabled -> DotState.IDLE
            else -> DotState.RUNNING
        },
        WxScriptFloatingEntry.status,
    )

    items += DiagItem(
        "微信 + 菜单入口",
        when {
            WxScriptPlusMenuEntry.installed -> DotState.OK
            !Preferences.plusMenuEntryEnabled -> DotState.IDLE
            WxScriptPlusMenuEntry.status.startsWith("失败") -> DotState.FAIL
            else -> DotState.RUNNING
        },
        if (!Preferences.plusMenuEntryEnabled) {
            "已关闭（首页 → 快捷入口可开启）"
        } else {
            WxScriptPlusMenuEntry.status + "｜设置项开、但 + 菜单里看不到时，多半是 PlusSubMenuHelper 锚点没解析到，看日志 tag=PlusMenuEntry"
        },
    )

    val serverUrl = runCatching { MarketData.serverUrl(context) }.getOrDefault("")
    items += DiagItem(
        "市场服务器",
        DotState.IDLE,
        serverUrl.ifBlank { "未配置" } + "（点下方「探测市场」检查连通性）",
    )

    val digest = runCatching { WeLogger.digest() }.getOrNull()
    items += DiagItem(
        "运行日志",
        when {
            digest == null -> DotState.IDLE
            digest.errorCount > 0 -> DotState.FAIL
            digest.warnCount > 0 -> DotState.RUNNING
            else -> DotState.OK
        },
        digest?.let { "${it.fileName ?: "无"}（${it.fileSizeText}）· 错误 ${it.errorCount} · 警告 ${it.warnCount}" }
            ?: "日志不可用",
    )

    return items
}

private fun buildReport(context: android.content.Context, items: List<DiagItem>): String = buildString {
    appendLine("========== WxScript 诊断报告 ==========")
    items.forEach { item ->
        val mark = when (item.state) {
            DotState.OK -> "[OK]"
            DotState.RUNNING -> "[..]"
            DotState.FAIL -> "[NG]"
            DotState.IDLE -> "[--]"
        }
        appendLine("$mark ${item.name}：${item.detail}")
    }
    appendLine("---------- 插件 ----------")
    runCatching { ScriptData.listPlugins(context) }.getOrDefault(emptyList()).forEach { p ->
        appendLine("· ${p.displayName} (${p.id}) v${p.version} 启用=${p.enabled} 运行=${p.running}")
    }
    appendLine("---------- 日志摘要 ----------")
    appendLine(runCatching { WeLogger.digest().toShareText() }.getOrElse { "读取失败" })
}
