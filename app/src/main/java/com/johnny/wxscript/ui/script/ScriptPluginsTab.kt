package com.johnny.wxscript.ui.script

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.ui.HostLauncher
import com.johnny.wxscript.utils.hostProcessName
import h.Hchat.hooks.items.script.ScriptPluginManager
import h.Hchat.hooks.items.script.ScriptPluginRuntime
import h.Hchat.hooks.items.script.ScriptPluginSettings
import h.Hchat.preferences.HchatStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「脚本」Tab。
 *
 * 脚本运行时只能在微信进程内工作（插件加载、微信 API、FastKV 配置都绑定在宿主进程），
 * 因此在模块自身的进程里只提示「在微信中打开」，不展示任何会读到错误状态的数据。
 */
@Composable
fun ScriptPluginsTab() {
    val context = LocalContext.current
    val isHost = hostProcessName() == BuildConfig.HOST_PACKAGE

    if (!isHost) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "需要在微信进程中运行",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "脚本引擎与微信 API 只在微信进程内可用。点下面的按钮会在微信里打开同一个设置页（共享同一份插件与配置）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = {
                        val ok = HostLauncher.openSettingsInHost(context)
                        if (!ok) toast(context, "无法启动微信，请确认微信已安装、模块已激活并勾选作用域")
                    }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("在微信中打开设置")
                    }
                }
            }
        }
        return
    }

    ScriptPluginList()
}

@Composable
private fun ScriptPluginList() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plugins by remember { mutableStateOf<List<ScriptPluginManager.ManagedPlugin>>(emptyList()) }
    var masterEnabled by remember { mutableStateOf(readMasterSwitch(context)) }
    var loading by remember { mutableStateOf(true) }
    var readmeTarget by remember { mutableStateOf<ScriptPluginRuntime.ScriptPlugin?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = true
        plugins = withContext(Dispatchers.IO) {
            runCatching { ScriptPluginManager.listForDisplay(context) }.getOrDefault(emptyList())
        }
        masterEnabled = readMasterSwitch(context)
        loading = false
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "脚本插件总开关",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (masterEnabled) "已启用：插件会随微信启动加载" else "已关闭：不加载任何插件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = masterEnabled,
                        onCheckedChange = { want ->
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { ScriptPluginRuntime.setGlobalEnabled(context, want) }
                                }
                                result.fold(
                                    onSuccess = { inner ->
                                        inner.fold(
                                            onSuccess = { masterEnabled = want },
                                            onFailure = { e -> toast(context, "切换失败：" + (e.message ?: "未知原因")) },
                                        )
                                    },
                                    onFailure = { e -> toast(context, "切换失败：" + (e.message ?: "未知原因")) },
                                )
                                masterEnabled = readMasterSwitch(context)
                            }
                        },
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { reloadTick++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("刷新")
                }
                OutlinedButton(onClick = { toast(context, "插件目录：" + scriptDirOf(context)) }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("插件目录")
                }
            }
        }

        if (loading) {
            item { Text("读取中…", style = MaterialTheme.typography.bodySmall) }
        } else if (plugins.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "还没有安装插件",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "把插件目录（包含 main.java 与 info.prop）放到：\n${scriptDirOf(context)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(plugins, key = { it.plugin.id }) { managed ->
                PluginRow(
                    context = context,
                    plugin = managed.plugin,
                    onToggle = { want ->
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { ScriptPluginRuntime.setPluginEnabled(context, managed.plugin.id, want) }
                            }
                            result.fold(
                                onSuccess = { inner ->
                                    inner.fold(
                                        onSuccess = { reloadTick++ },
                                        onFailure = { e -> toast(context, "切换失败：" + (e.message ?: "未知原因")) },
                                    )
                                },
                                onFailure = { e -> toast(context, "切换失败：" + (e.message ?: "未知原因")) },
                            )
                        }
                    },
                    onReadme = { readmeTarget = managed.plugin },
                    onSettings = {
                        val result = runCatching { ScriptPluginRuntime.callOpenSettings(managed.plugin.id) }
                        val failure = result.getOrNull()?.exceptionOrNull() ?: result.exceptionOrNull()
                        if (failure != null) {
                            toast(context, "插件设置打开失败：" + (failure.message ?: "插件未实现 openSettings"))
                        }
                    },
                    onDelete = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { ScriptPluginManager.deletePlugin(context, managed.plugin.id) }
                            }
                            result.fold(
                                onSuccess = { inner ->
                                    inner.fold(
                                        onSuccess = {
                                            toast(context, "已删除")
                                            reloadTick++
                                        },
                                        onFailure = { e -> toast(context, "删除失败：" + (e.message ?: "未知原因")) },
                                    )
                                },
                                onFailure = { e -> toast(context, "删除失败：" + (e.message ?: "未知原因")) },
                            )
                        }
                    },
                )
            }
        }
    }

    readmeTarget?.let { plugin ->
        ReadmeDialog(context = context, plugin = plugin, onClose = { readmeTarget = null })
    }
}

@Composable
private fun PluginRow(
    context: Context,
    plugin: ScriptPluginRuntime.ScriptPlugin,
    onToggle: (Boolean) -> Unit,
    onReadme: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    var enabled by remember(plugin.id) {
        mutableStateOf(runCatching { ScriptPluginRuntime.isPluginEnabled(context, plugin.id) }.getOrDefault(false))
    }
    Card(colors = CardDefaults.cardColors()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        plugin.displayName ?: plugin.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "v${plugin.version} · ${plugin.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { want ->
                        enabled = want
                        onToggle(want)
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onReadme) {
                    Icon(Icons.Filled.Description, contentDescription = "说明")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "插件设置")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

@Composable
private fun ReadmeDialog(context: Context, plugin: ScriptPluginRuntime.ScriptPlugin, onClose: () -> Unit) {
    val text = remember(plugin.id) {
        runCatching {
            val files = plugin.dir.listFiles().orEmpty()
            val f = files.firstOrNull { it.isFile && it.name.equals("README.md", true) }
                ?: files.firstOrNull { it.isFile && it.name.startsWith("README", true) }
            f?.readText()?.take(20_000) ?: "该插件没有 README"
        }.getOrDefault("读取失败")
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(plugin.displayName ?: plugin.name) },
        text = { Text(text, style = MaterialTheme.typography.bodySmall) },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } },
    )
}

private fun readMasterSwitch(context: Context): Boolean = runCatching {
    HchatStorage.preferences(context, ScriptPluginSettings.PREFS_NAME)
        .getBoolean(ScriptPluginSettings.KEY_ENABLE, ScriptPluginSettings.DEFAULT_ENABLE)
}.getOrDefault(false)

private fun scriptDirOf(context: Context): String =
    runCatching { ScriptPluginRuntime.scriptDir(context).absolutePath }.getOrDefault("不可用")

private fun toast(context: Context, message: String) {
    runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
}
