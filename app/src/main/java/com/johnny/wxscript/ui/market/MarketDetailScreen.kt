package com.johnny.wxscript.ui.market

import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnny.wxscript.ui.components.LoadingBox
import com.johnny.wxscript.ui.components.SectionHeader
import com.johnny.wxscript.ui.components.SegmentedTabs
import com.johnny.wxscript.ui.components.StateBox
import h.Hchat.hooks.items.script.market.PluginMarketComment
import h.Hchat.hooks.items.script.market.PluginMarketHistoryVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 市场插件详情页：安装/更新 + 点赞/关注 + README/文件/版本历史/评论。
 *
 * 社交操作依赖当前微信账号（[MarketSocial.identity]），账号 API 未就绪时
 * 相关按钮禁用并在页面底部提示去诊断页，避免「点了没反应」。
 */
@Composable
fun MarketDetailScreen(remotePluginId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var item by remember { mutableStateOf<MarketData.Item?>(null) }
    var history by remember { mutableStateOf<List<PluginMarketHistoryVersion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var installing by remember { mutableStateOf(false) }
    var confirmOverwrite by remember { mutableStateOf(false) }

    var liked by remember(remotePluginId) { mutableStateOf(false) }
    var liking by remember { mutableStateOf(false) }
    var following by remember(remotePluginId) { mutableStateOf(false) }
    var socialError by remember { mutableStateOf<String?>(null) }

    var tab by remember { mutableIntStateOf(0) }
    var comments by remember { mutableStateOf<List<PluginMarketComment>>(emptyList()) }
    var commentTotal by remember { mutableIntStateOf(0) }
    var commentLoading by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<PluginMarketComment?>(null) }
    var sending by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PluginMarketComment?>(null) }

    fun reloadComments() {
        commentLoading = true
        scope.launch {
            val res = withContext(Dispatchers.IO) { MarketSocial.comments(context, remotePluginId, 50) }
            res.fold(
                onSuccess = { page ->
                    comments = page.items
                    commentTotal = page.total.toInt()
                },
                onFailure = { socialError = it.message ?: it.javaClass.simpleName },
            )
            commentLoading = false
        }
    }

    LaunchedEffect(remotePluginId, reloadToken) {
        loading = true
        error = null
        val detail = withContext(Dispatchers.IO) { MarketData.detail(context, remotePluginId) }
        detail.fold(
            onSuccess = { item = it },
            onFailure = { item = null; error = it.message ?: it.javaClass.simpleName },
        )
        history = withContext(Dispatchers.IO) {
            MarketData.history(context, remotePluginId).getOrDefault(emptyList())
        }
        loading = false

        // 社交状态是「附加信息」：失败不影响页面主体，只在按钮下方给一行提示。
        val like = withContext(Dispatchers.IO) { MarketSocial.likeStatus(context, remotePluginId) }
        like.fold(
            onSuccess = { liked = it.liked },
            onFailure = { socialError = it.message ?: it.javaClass.simpleName },
        )
        val follow = withContext(Dispatchers.IO) { MarketSocial.followStatus(context, remotePluginId) }
        follow.fold(
            onSuccess = { following = it },
            onFailure = { socialError = it.message ?: it.javaClass.simpleName },
        )
        reloadComments()
    }

    fun doInstall() {
        val target = item ?: return
        installing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                MarketData.install(context, target, overwrite = target.installed)
            }
            installing = false
            result.fold(
                onSuccess = { installResult ->
                    withContext(Dispatchers.IO) {
                        MarketData.reloadAfterInstall(context, installResult.localPluginId)
                    }
                    Toast.makeText(
                        context,
                        if (installResult.replacedExisting) "已更新并重载" else "已安装 ${installResult.localPluginId}",
                        Toast.LENGTH_SHORT,
                    ).show()
                    reloadToken++
                },
                onFailure = {
                    Toast.makeText(context, "安装失败：" + (it.message ?: it.javaClass.simpleName), Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text(
                item?.title ?: "插件详情",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()

        val current = item
        when {
            loading && current == null -> LoadingBox("正在读取插件详情…")
            current == null -> StateBox(
                emoji = "⚠️",
                title = "详情读取失败",
                detail = error,
                actionText = "重试",
                onAction = { reloadToken++ },
            )
            else -> {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    current.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                if (current.updateAvailable) Badge("可更新")
                                else if (current.installed) Badge("已安装", muted = true)
                            }
                            Text(
                                buildString {
                                    if (current.plugin.author.isNotBlank()) append(current.plugin.author)
                                    if (current.plugin.version.isNotBlank()) {
                                        if (isNotEmpty()) append(" · ")
                                        append("v").append(current.plugin.version)
                                    }
                                    current.installedVersion?.takeIf { it.isNotBlank() }?.let {
                                        append(" · 已安装 v").append(it)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                buildString {
                                    append("下载 ").append(current.plugin.downloadCount)
                                    append(" · 点赞 ").append(current.plugin.likeCount)
                                    append(" · 评论 ").append(maxOf(current.plugin.commentCount, commentTotal.toLong()))
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (current.plugin.description.isNotBlank()) {
                                Text(current.plugin.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = { if (current.installed) confirmOverwrite = true else doInstall() },
                                enabled = !installing && current.plugin.files.isNotEmpty(),
                            ) {
                                Text(
                                    when {
                                        installing -> "处理中…"
                                        current.updateAvailable -> "更新到 v${current.plugin.version}"
                                        current.installed -> "重新安装"
                                        else -> "安装"
                                    }
                                )
                            }
                            OutlinedButton(
                                enabled = !liking && socialError == null,
                                onClick = {
                                    val next = !liked
                                    liking = true
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            MarketSocial.setLiked(context, remotePluginId, next)
                                        }
                                        liking = false
                                        res.fold(
                                            onSuccess = { liked = it.liked },
                                            onFailure = { Toast.makeText(context, "点赞失败：" + (it.message ?: ""), Toast.LENGTH_SHORT).show() },
                                        )
                                    }
                                },
                            ) { Text(if (liked) "♥ 已赞" else "♡ 点赞") }
                            OutlinedButton(
                                enabled = socialError == null,
                                onClick = {
                                    val next = !following
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            MarketSocial.setFollowing(context, remotePluginId, next)
                                        }
                                        res.fold(
                                            onSuccess = { following = it },
                                            onFailure = { Toast.makeText(context, "关注失败：" + (it.message ?: ""), Toast.LENGTH_SHORT).show() },
                                        )
                                    }
                                },
                            ) { Text(if (following) "已关注" else "关注") }
                        }
                    }

                    socialError?.let { message ->
                        item {
                            Text(
                                "账号能力未就绪，点赞/关注/评论不可用：$message",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }

                    if (current.plugin.files.isEmpty()) {
                        item {
                            Text(
                                "该条目没有携带文件内容（可能服务端只返回了元信息），无法直接安装。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }

                    item {
                        SegmentedTabs(listOf("介绍", "评论 ($commentTotal)"), tab) { tab = it }
                    }

                    if (tab == 0) {
                        item { SectionHeader("README") }
                        item {
                            Text(
                                MarketData.readme(current.plugin),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                ),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            )
                        }

                        item { SectionHeader("文件清单") }
                        item {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                current.plugin.files.forEach { file ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                        Text(
                                            file.name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "${file.size} B",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        item { SectionHeader("版本历史") }
                        if (history.isEmpty()) {
                            item {
                                Text(
                                    "服务端没有返回历史版本（或这个服务器不提供历史接口）。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        } else {
                            items(history.size) { index ->
                                val version = history[index]
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        "v" + version.versionName.ifBlank { version.versionId.take(8) },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        buildString {
                                            if (version.createdAt.isNotBlank()) append(version.createdAt)
                                            if (version.totalSize > 0) {
                                                if (isNotEmpty()) append(" · ")
                                                append(version.totalSize).append(" B")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (version.releaseNotes.isNotBlank()) {
                                        Text(version.releaseNotes, style = MaterialTheme.typography.bodySmall)
                                    }
                                    HorizontalDivider(Modifier.padding(top = 6.dp))
                                }
                            }
                        }

                        item {
                            Text(
                                "兼容性：市场条目不携带微信版本范围，安装后请到「脚本」页确认插件是否正常加载，" +
                                    "加载失败时进入诊断页查看原因。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                OutlinedTextField(
                                    value = commentText,
                                    onValueChange = { commentText = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(if (replyTo == null) "写评论…" else "回复 ${replyTo?.userNickname}") },
                                    maxLines = 3,
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    enabled = !sending && commentText.isNotBlank() && socialError == null,
                                    onClick = {
                                        val text = commentText.trim()
                                        val parent = replyTo
                                        sending = true
                                        scope.launch {
                                            val res = withContext(Dispatchers.IO) {
                                                if (parent == null) MarketSocial.comment(context, remotePluginId, text)
                                                else MarketSocial.reply(context, remotePluginId, parent.commentId, text)
                                            }
                                            sending = false
                                            res.fold(
                                                onSuccess = {
                                                    commentText = ""
                                                    replyTo = null
                                                    reloadComments()
                                                },
                                                onFailure = {
                                                    Toast.makeText(context, "发送失败：" + (it.message ?: ""), Toast.LENGTH_SHORT).show()
                                                },
                                            )
                                        }
                                    },
                                ) { Text(if (sending) "发送中" else "发送") }
                            }
                        }

                        if (commentLoading && comments.isEmpty()) {
                            item { LoadingBox("正在读取评论…") }
                        } else if (comments.isEmpty()) {
                            item {
                                StateBox("💬", "还没有评论", "可以发第一条")
                            }
                        } else {
                            items(comments.size) { index ->
                                val comment = comments[index]
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            comment.userNickname.ifBlank { "匿名" },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            comment.createdAt,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(comment.content, style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { replyTo = comment }) { Text("回复") }
                                        if (comment.canDelete) {
                                            TextButton(onClick = { deleteTarget = comment }) { Text("删除") }
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }

                    if (error != null) {
                        item {
                            Text(
                                "刷新失败：" + error,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmOverwrite) {
        val current = item
        AlertDialog(
            onDismissRequest = { confirmOverwrite = false },
            title = { Text(if (current?.updateAvailable == true) "更新插件" else "重新安装") },
            text = {
                Text(
                    "将用远程版本 v${current?.plugin?.version.orEmpty()} 覆盖本地已安装的 " +
                        "${current?.localId.orEmpty()}（同名文件会被替换，插件目录里的其他文件保留）。确认继续？"
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmOverwrite = false; doInstall() }) { Text("确认覆盖") }
            },
            dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("取消") } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除评论") },
            text = { Text("确定删除这条评论？") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val res = withContext(Dispatchers.IO) {
                            MarketSocial.deleteComment(context, remotePluginId, target.commentId)
                        }
                        res.fold(
                            onSuccess = { reloadComments() },
                            onFailure = {
                                Toast.makeText(context, "删除失败：" + (it.message ?: ""), Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}
