package com.johnny.wxscript.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.johnny.wxscript.ui.components.DotState
import com.johnny.wxscript.ui.components.LoadingBox
import com.johnny.wxscript.ui.components.SearchField
import com.johnny.wxscript.ui.components.SectionHeader
import com.johnny.wxscript.ui.components.StateBox
import com.johnny.wxscript.ui.components.StatusDot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 「市场」Tab：浏览 / 搜索 / 排序 / 已安装与可更新筛选，点条目进详情页安装。
 *
 * 服务器地址可切换（官方 / 备用 / 自定义），切换后立即重新拉列表；
 * 拉取失败时给出可操作入口（重试 / 换服务器），不显示假数据。
 */
@Composable
fun MarketTab(onOpenDetail: (String) -> Unit) {
    val context = LocalContext.current

    var serverUrl by remember { mutableStateOf(MarketData.serverUrl(context)) }
    var serverDialog by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(MarketData.Filter.ALL) }
    var sort by remember { mutableStateOf(MarketData.Sort.LATEST) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<MarketData.Item>>(emptyList()) }

    var health by remember { mutableStateOf<Pair<DotState, String>?>(null) }

    // 搜索输入防抖：250~400ms 内继续输入就不会打网络。
    LaunchedEffect(query, sort, reloadToken) {
        loading = true
        error = null
        delay(if (query.isBlank()) 0 else 400)
        val result = withContext(Dispatchers.IO) { MarketData.load(context, query, sort) }
        result.fold(
            onSuccess = {
                items = it
                error = null
                health = DotState.OK to "可用 · ${it.size} 个插件"
            },
            onFailure = {
                items = emptyList()
                error = it.message ?: it.javaClass.simpleName
                health = DotState.FAIL to "不可用"
            },
        )
        loading = false
    }

    val visible = remember(items, filter) {
        when (filter) {
            MarketData.Filter.ALL -> items
            MarketData.Filter.INSTALLED -> items.filter { it.installed }
            MarketData.Filter.UPDATABLE -> items.filter { it.updateAvailable }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            health?.let { (dot, text) ->
                StatusDot(dot)
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            } ?: run {
                Text("正在探测市场服务器…", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = { serverDialog = true }) { Text("服务器") }
        }
        Text(
            serverUrl.ifBlank { "未配置" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        SearchField(query, "搜索插件名称 / 作者 / 描述", { query = it })

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarketData.Filter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.label) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("排序", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MarketData.Sort.entries.forEach { option ->
                FilterChip(
                    selected = sort == option,
                    onClick = { sort = option },
                    label = { Text(option.label) },
                )
            }
        }
        HorizontalDivider()

        when {
            loading && items.isEmpty() -> LoadingBox("正在拉取插件列表…")
            error != null && items.isEmpty() -> StateBox(
                emoji = "📡",
                title = "市场暂时连不上",
                detail = error,
                actionText = "重试",
                onAction = { reloadToken++ },
            )
            visible.isEmpty() -> StateBox(
                emoji = if (items.isEmpty()) "🛒" else "🔍",
                title = if (items.isEmpty()) "市场里还没有插件" else "没有匹配的插件",
                detail = when {
                    items.isEmpty() -> "可以换一个服务器，或稍后再来"
                    filter == MarketData.Filter.UPDATABLE -> "当前没有可更新的插件"
                    filter == MarketData.Filter.INSTALLED -> "还没有从市场安装过插件"
                    else -> "换个关键词试试"
                },
                actionText = if (query.isNotBlank() || filter != MarketData.Filter.ALL) "清空筛选" else null,
                onAction = if (query.isNotBlank() || filter != MarketData.Filter.ALL) {
                    { query = ""; filter = MarketData.Filter.ALL }
                } else null,
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                item { SectionHeader("共 ${visible.size} 个插件") }
                items(visible, key = { it.plugin.remotePluginId }) { item ->
                    MarketRow(item) { onOpenDetail(item.plugin.remotePluginId) }
                    HorizontalDivider()
                }
            }
        }
    }

    if (serverDialog) {
        ServerDialog(
            current = serverUrl,
            onDismiss = { serverDialog = false },
            onSaved = { saved ->
                serverUrl = saved
                serverDialog = false
                reloadToken++
            },
        )
    }
}

@Composable
private fun MarketRow(item: MarketData.Item, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (item.updateAvailable) {
                Badge("可更新")
            } else if (item.installed) {
                Badge("已安装", muted = true)
            }
        }
        if (item.subtitle.isNotBlank()) {
            Text(
                item.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val desc = item.plugin.description
        if (desc.isNotBlank()) {
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            buildString {
                append("下载 ").append(item.plugin.downloadCount)
                append(" · 点赞 ").append(item.plugin.likeCount)
                if (item.plugin.updateTime.isNotBlank()) append(" · ").append(item.plugin.updateTime)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun Badge(text: String, muted: Boolean = false) {
    val bg = if (muted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
    val fg = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        Modifier
            .padding(start = 8.dp)
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun ServerDialog(
    current: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val presets = remember { MarketData.presetUrls() }
    var custom by remember { mutableStateOf(if (current in presets) "" else current) }
    var selected by remember { mutableStateOf(if (current in presets) current else "") }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("市场服务器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { url ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = url; custom = "" }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(if (selected == url) DotState.OK else DotState.IDLE)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(MarketData.presetLabel(url), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it; if (it.isNotBlank()) selected = "" },
                    label = { Text("自定义地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                message?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = custom.trim().ifBlank { selected }
                if (target.isBlank()) {
                    message = "请选择或填写服务器地址"
                    return@TextButton
                }
                MarketData.saveServerUrl(context, target).fold(
                    onSuccess = { onSaved(target.trim().trimEnd('/')) },
                    onFailure = { message = it.message ?: "保存失败" },
                )
            }) { Text("保存并刷新") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
