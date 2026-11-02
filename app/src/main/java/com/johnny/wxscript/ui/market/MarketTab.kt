package com.johnny.wxscript.ui.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxscript.ui.components.DotState
import com.johnny.wxscript.ui.components.StatusDot
import h.Hchat.hooks.items.script.market.PluginMarketClient
import h.Hchat.hooks.items.script.market.PluginMarketSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「市场」Tab —— P3 阶段只做**服务可用性**：显示当前服务地址、探测 /health。
 *
 * 浏览 / 搜索 / 安装 / 详情页属 P4（服务端源码在 WCX 仓库根 plugin-market-server.zip）。
 * 这里不放出假数据，避免看起来能用其实不能。
 */
@Composable
fun MarketTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<Pair<DotState, String>?>(null) }
    var probing by remember { mutableStateOf(false) }
    val baseUrl = remember { runCatching { PluginMarketSettings.serviceUrl(context) }.getOrDefault("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("插件市场", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "浏览 / 搜索 / 安装 / 详情页将在 P4 接入。现在可以先确认服务端是否可达。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("服务端", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    baseUrl.ifBlank { "未配置" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !probing && baseUrl.isNotBlank(),
                        onClick = {
                            probing = true
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    runCatching { PluginMarketClient.probeHealth(context, baseUrl).getOrThrow() }
                                }
                                status = res.fold(
                                    onSuccess = { DotState.OK to "服务正常 · 数据库 ${it.database} · ${it.message}" },
                                    onFailure = { DotState.FAIL to ("不可达：" + (it.message ?: "未知原因")) },
                                )
                                probing = false
                            }
                        },
                    ) { Text(if (probing) "探测中…" else "探测服务") }
                    status?.let { (dot, text) ->
                        StatusDot(dot)
                        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
            }
            HorizontalDivider()
        }
        item {
            Text(
                "已安装 / 可更新列表、分类与排序、编辑精选、README 与版本历史，都会在 P4 一起做。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
