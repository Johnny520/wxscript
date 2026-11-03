package com.johnny.wxscript.ui.market

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.johnny.wxscript.ui.components.LoadingBox
import com.johnny.wxscript.ui.components.SectionHeader
import com.johnny.wxscript.ui.components.StateBox
import com.johnny.wxscript.ui.script.ScriptData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 发布插件到市场。
 *
 * 只上传受管文件（main.java / main.java.bshs / info.prop / README.md），
 * 由 PluginMarketInstaller.collectUploadPackage 打包并校验；
 * 本地已有 ownerToken 时按「更新」提交（服务端据此覆盖同一插件）。
 */
@Composable
fun MarketUploadScreen(onBack: () -> Unit, onOpenMarket: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var plugins by remember { mutableStateOf<List<ScriptData.PluginItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<ScriptData.PluginItem?>(null) }
    var marketName by remember { mutableStateOf("") }
    var releaseNotes by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        plugins = withContext(Dispatchers.IO) { ScriptData.listPlugins(context) }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text("发布插件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider()

        if (loading) {
            LoadingBox("正在读取本地插件…")
            return@Column
        }
        if (plugins.isEmpty()) {
            StateBox("📦", "没有可发布的插件", "先在「脚本」页导入或新建一个插件", "返回", onBack)
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item { SectionHeader("选择要发布的插件") }
            items(plugins.size) { index ->
                val plugin = plugins[index]
                val isSelected = selected?.id == plugin.id
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = plugin
                            if (marketName.isBlank()) marketName = plugin.displayName
                        }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(plugin.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        plugin.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }

            val target = selected
            if (target != null) {
                item { SectionHeader("发布信息") }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = marketName,
                            onValueChange = { marketName = it },
                            label = { Text("市场显示名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = releaseNotes,
                            onValueChange = { releaseNotes = it },
                            label = { Text("更新说明（最多 500 字）") },
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "版本号取插件 info.prop 里的 version（当前 v${target.version.ifBlank { "未填写" }}），" +
                                "要改版本请先编辑 info.prop。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !uploading && marketName.isNotBlank(),
                                onClick = {
                                    uploading = true
                                    result = null
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            MarketSocial.upload(context, target.id, marketName.trim(), releaseNotes.trim())
                                        }
                                        uploading = false
                                        res.fold(
                                            onSuccess = { ownership ->
                                                result = buildString {
                                                    append("上传成功\n")
                                                    append("远程 ID：").append(ownership.remotePluginId).append("\n")
                                                    append("审核状态：").append(ownership.reviewStatus.name)
                                                }
                                                Toast.makeText(context, "已提交到市场", Toast.LENGTH_SHORT).show()
                                            },
                                            onFailure = {
                                                result = "上传失败：" + (it.message ?: it.javaClass.simpleName)
                                            },
                                        )
                                    }
                                },
                            ) { Text(if (uploading) "上传中…" else "上传到市场") }
                            Button(enabled = !uploading, onClick = onOpenMarket) { Text("去市场看看") }
                        }
                        result?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "先在上面选一个插件。市场只接收 main.java / main.java.bshs / info.prop / README.md，" +
                            "其他文件不会上传。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
