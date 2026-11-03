package com.johnny.wxscript.ui.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxscript.ui.components.LoadingBox
import com.johnny.wxscript.ui.components.StateBox
import h.Hchat.hooks.items.script.market.PluginMarketNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 市场通知：别人回复了我的评论 / 点赞关注等。点条目跳到对应插件详情。 */
@Composable
fun MarketNotificationsScreen(onBack: () -> Unit, onOpenPlugin: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var notifications by remember { mutableStateOf<List<PluginMarketNotification>>(emptyList()) }
    var unread by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        error = null
        val res = withContext(Dispatchers.IO) { MarketSocial.notifications(context) }
        res.fold(
            onSuccess = {
                notifications = it.items
                unread = it.unreadCount.toInt()
            },
            onFailure = { error = it.message ?: it.javaClass.simpleName },
        )
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text("市场通知", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (unread > 0) {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { MarketSocial.markNotificationsRead(context) }
                        reload++
                    }
                }) { Text("全部已读（$unread）") }
            }
        }
        HorizontalDivider()

        when {
            loading && notifications.isEmpty() -> LoadingBox("正在读取通知…")
            error != null -> StateBox(
                emoji = "🔔",
                title = "通知读取失败",
                detail = error,
                actionText = "重试",
                onAction = { reload++ },
            )
            notifications.isEmpty() -> StateBox("🔔", "没有新通知", "别人回复你的评论时会出现在这里")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(notifications.size) { index ->
                    val item = notifications[index]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = item.remotePluginId.isNotBlank()) {
                                if (item.remotePluginId.isNotBlank()) onOpenPlugin(item.remotePluginId)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.pluginName.ifBlank { "插件" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            if (!item.read) Badge("未读")
                            Text(
                                item.createdAt,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        Text(
                            item.actorNickname.ifBlank { "有人" } + "：" + item.content,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (item.originalContent.isNotBlank()) {
                            Text(
                                "原评论：" + item.originalContent,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
