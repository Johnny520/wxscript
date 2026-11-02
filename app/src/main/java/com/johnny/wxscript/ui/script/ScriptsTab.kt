package com.johnny.wxscript.ui.script

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.johnny.wxscript.ui.components.LoadingBox
import com.johnny.wxscript.ui.components.SectionHeader
import com.johnny.wxscript.ui.components.SearchField
import com.johnny.wxscript.ui.components.SegmentedTabs
import com.johnny.wxscript.ui.components.StateBox
import com.johnny.wxscript.ui.components.SwipeRevealRow
import com.johnny.wxscript.ui.rememberPluginSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「脚本」Tab —— 单列列表（不用卡片，卡片一屏只看 4 个）。
 *
 * 结构：总开关行 → 搜索 → 分段（已启用 / 全部）→ 按来源分组的 sticky header 列表。
 * 每行 = 图标 + 名称 + 副标题（版本 · 作者 · 来源）+ 右侧开关；左滑露出「编辑 / 日志 / 设置 / 卸载」。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScriptsTab(
    onEdit: (String) -> Unit,
    onLogs: (String) -> Unit,
    onDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot = rememberPluginSnapshot()

    var query by remember { mutableStateOf("") }
    var segment by remember { mutableIntStateOf(1) } // 0 已启用 / 1 全部
    var readmeTarget by remember { mutableStateOf<ScriptData.PluginItem?>(null) }
    var importResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { ScriptData.inspectImport(context, uri) }
            result.fold(
                onSuccess = { inspection ->
                    val conflicts = inspection.plugins.filter { it.conflict }.map { it.pluginId }
                    snapshot.pendingImport = inspection to conflicts
                },
                onFailure = { importResult = false to ("导入包解析失败：" + (it.message ?: "未知原因")) },
            )
        }
    }

    val all = snapshot.plugins
    val filtered = all
        .filter { segment == 1 || it.enabled }
        .filter { item ->
            query.isBlank() || listOf(item.displayName, item.name, item.author, item.description, item.id)
                .any { it.contains(query, ignoreCase = true) }
        }
    val localGroup = filtered.filter { !it.fromMarket }
    val marketGroup = filtered.filter { it.fromMarket }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            MasterSwitchRow(
                enabled = snapshot.globalEnabled,
                pluginCount = all.size,
                runningCount = all.count { it.running },
                onToggle = { want ->
                    scope.launch {
                        val res = withContext(Dispatchers.IO) { ScriptData.setGlobalEnabled(context, want) }
                        res.onFailure { snapshot.toast("切换失败：" + (it.message ?: "未知原因")) }
                        snapshot.refresh()
                    }
                },
            )
        }
        item {
            SearchField(query, "搜索名称 / 描述 / 作者 / 插件 ID", { query = it })
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SegmentedTabs(listOf("已启用 (${all.count { it.enabled }})", "全部 (${all.size})"), segment) { segment = it }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导入插件")
                }
                OutlinedButton(onClick = {
                    val path = ScriptData.scriptDir(context)
                    runCatching {
                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("pluginDir", path))
                    }
                    snapshot.toast("插件目录已复制：$path")
                }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("插件目录")
                }
                OutlinedButton(onClick = { snapshot.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (snapshot.loading && all.isEmpty()) {
            item { LoadingBox() }
        } else if (all.isEmpty()) {
            item {
                StateBox(
                    emoji = "📦",
                    title = "还没有脚本插件",
                    detail = "插件是一个目录，内含 main.java（BeanShell）与 info.prop。\n放到：${ScriptData.scriptDir(context)}",
                    actionText = "刷新",
                    onAction = { snapshot.refresh() },
                )
            }
        } else {
            if (localGroup.isNotEmpty()) {
                stickyHeader(key = "hdr-local") { SectionHeader("本地导入 · ${localGroup.size}") }
                items(localGroup, key = { "p-" + it.id }) { item ->
                    PluginRow(
                        item = item,
                        onToggle = { want -> snapshot.toggle(item, want) },
                        onEdit = { onEdit(item.id) },
                        onLogs = { onLogs(item.id) },
                        onReadme = { readmeTarget = item },
                        onSettings = { snapshot.openSettings(item) },
                        onDelete = { snapshot.delete(item) },
                    )
                }
            }
            if (marketGroup.isNotEmpty()) {
                stickyHeader(key = "hdr-market") { SectionHeader("市场安装 · ${marketGroup.size}") }
                items(marketGroup, key = { "m-" + it.id }) { item ->
                    PluginRow(
                        item = item,
                        onToggle = { want -> snapshot.toggle(item, want) },
                        onEdit = { onEdit(item.id) },
                        onLogs = { onLogs(item.id) },
                        onReadme = { readmeTarget = item },
                        onSettings = { snapshot.openSettings(item) },
                        onDelete = { snapshot.delete(item) },
                    )
                }
            }
            if (filtered.isEmpty()) {
                item {
                    StateBox(
                        emoji = "🔍",
                        title = if (segment == 0) "没有已启用的插件" else "没有匹配的插件",
                        detail = if (segment == 0) "切到「全部」看看，或先打开总开关" else "换个关键词试试",
                    )
                }
            }
        }

        item {
            HorizontalDivider(Modifier.padding(top = 12.dp))
            TextButton(
                onClick = onDiagnostics,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text("脚本功能诊断 / 一键修复") }
        }
    }

    readmeTarget?.let { item ->
        ReadmeDialog(name = item.displayName, content = remember(item.id) { ScriptData.readReadme(item) }) {
            readmeTarget = null
        }
    }

    snapshot.pendingImport?.let { (inspection, conflicts) ->
        ImportDialog(
            title = "导入 ${inspection.plugins.size} 个插件",
            names = inspection.plugins.map { it.name },
            conflictCount = conflicts.size,
            onDismiss = { snapshot.pendingImport = null },
            onConfirm = { overwrite ->
                snapshot.pendingImport = null
                scope.launch {
                    val res = withContext(Dispatchers.IO) {
                        ScriptData.applyImport(context, inspection.sessionId, conflicts, overwrite)
                    }
                    res.fold(
                        onSuccess = { r ->
                            importResult = true to "已导入 ${r.importedPluginIds.size} 个，覆盖 ${r.overwrittenPluginIds.size} 个，跳过 ${r.skippedPluginIds.size} 个"
                            snapshot.refresh()
                        },
                        onFailure = { importResult = false to ("导入失败：" + (it.message ?: "未知原因")) },
                    )
                }
            },
        )
    }

    importResult?.let { (ok, message) ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text(if (ok) "完成" else "失败") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { importResult = null }) { Text("知道了") } },
        )
    }
}

@Composable
private fun MasterSwitchRow(
    enabled: Boolean,
    pluginCount: Int,
    runningCount: Int,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("脚本插件总开关", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (enabled) "已启用 · 共 $pluginCount 个插件，$runningCount 个运行中"
                else "已关闭 · 不加载任何插件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun PluginRow(
    item: ScriptData.PluginItem,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onLogs: () -> Unit,
    onReadme: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    SwipeRevealRow(
        actions = {
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "编辑") }
            IconButton(onClick = onLogs) { Icon(Icons.Filled.List, contentDescription = "日志") }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "设置") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "卸载") }
        },
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            leadingContent = {
                Icon(Icons.Filled.Description, contentDescription = null)
            },
            headlineContent = {
                Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Column {
                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall)
                    if (item.running) {
                        Text(
                            "运行中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            trailingContent = {
                Switch(checked = item.enabled, onCheckedChange = onToggle)
            },
            modifier = Modifier.clickable(onClick = onReadme),
        )
        HorizontalDivider()
    }
}

@Composable
private fun ReadmeDialog(name: String, content: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(name) },
        text = {
            LazyColumn(Modifier.fillMaxWidth()) {
                item { Text(content, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } },
    )
}

@Composable
private fun ImportDialog(
    title: String,
    names: List<String>,
    conflictCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (overwrite: Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(names.take(20).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                if (conflictCount > 0) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "其中 $conflictCount 个与本机插件同名：选择「覆盖」会用导入包替换，"
                            + "选择「跳过」则保留本机版本。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(conflictCount > 0) }) {
                Text(if (conflictCount > 0) "覆盖导入" else "导入")
            }
        },
        dismissButton = {
            if (conflictCount > 0) {
                TextButton(onClick = { onConfirm(false) }) { Text("跳过冲突项") }
            } else {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
