package com.johnny.wxscript.activity

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.ui.WxTheme
import com.johnny.wxscript.ui.script.ScriptPluginsTab
import com.johnny.wxscript.utils.hostProcessName

/** WxScript 设置主界面：4 个底栏 Tab（首页 / 脚本 / 市场 / 我的）。 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WxTheme { WxScriptRoot() }
        }
    }
}

private data class TabItem(val title: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem("首页", Icons.Filled.Home),
    TabItem("脚本", Icons.Filled.Terminal),
    TabItem("市场", Icons.Filled.Storefront),
    TabItem("我的", Icons.Filled.Person),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WxScriptRoot() {
    var current by remember { mutableIntStateOf(1) } // 默认落在「脚本」：主战场

    Scaffold(
        topBar = { TopAppBar(title = { Text(TABS[current].title) }) },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = current == index,
                        onClick = { current = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { inner ->
        Surface(modifier = Modifier.fillMaxSize().padding(inner)) {
            when (current) {
                0 -> HomePage()
                1 -> ScriptPluginsTab()
                2 -> MarketPage()
                else -> MinePage()
            }
        }
    }
}

/** 就绪度状态 */
private enum class ReadyState { OK, RUNNING, FAIL }

private data class ReadyRow(val name: String, val detail: String, val state: ReadyState)

@Composable
private fun StatusIcon(state: ReadyState) {
    val (icon, color) = when (state) {
        ReadyState.OK -> Icons.Filled.CheckCircle to Color(0xFF2E7D32)
        ReadyState.RUNNING -> Icons.Filled.Refresh to Color(0xFFF9A825)
        ReadyState.FAIL -> Icons.Filled.Error to Color(0xFFC62828)
    }
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
}

@Composable
private fun HomePage() {
    var tick by remember { mutableIntStateOf(0) }
    val rows = remember(tick) { collectReadyRows() }
    val taskSummary = remember(tick) { collectTaskSummary() }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "运行状态",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { tick++ }) { Text("刷新") }
                    }
                    rows.forEach { row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusIcon(row.state)
                            Spacer(Modifier.size(10.dp))
                            Column {
                                Text(row.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    row.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DexKit 安装任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        taskSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketPage() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        Text("插件市场", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "在线插件市场将在 P4 接入（服务端源码已就绪：plugin-market-server.zip）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MinePage() {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("WxScript", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
                Text("当前进程：${currentProcessName()}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "独立微信脚本模块，作者 Johnny520。与 WCX 主模块相互独立，可同时安装。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Card(colors = CardDefaults.cardColors()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("诊断信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    collectDiagnostics(context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(onClick = { openUrl(context, "https://github.com/Johnny520/wxscript") }) {
            Text("项目主页")
        }
    }
}

// ───────────────────────────── 数据采集（全部走 runCatching，避免界面进程缺 API 时崩溃） ─────────────────────────────

private fun collectReadyRows(): List<ReadyRow> {
    if (!isHostLoaded()) {
        return listOf(
            ReadyRow("脚本引擎", "未在微信进程内加载，请从「脚本」页打开微信内界面", ReadyState.FAIL),
            ReadyRow("微信 API", "同上", ReadyState.FAIL),
            ReadyRow("DexKit 索引", "同上", ReadyState.FAIL),
        )
    }
    val engine = runCatching { com.johnny.wxscript.features.hchat.HchatScriptBootstrap.status }
        .getOrDefault("状态未知")
    val engineRow = if (engine.contains("安装成功")) ReadyRow("脚本引擎", engine, ReadyState.OK)
    else ReadyRow("脚本引擎", engine, ReadyState.RUNNING)

    val apiDetail = runCatching { h.Hchat.hooks.items.script.ScriptPluginRuntime.scriptApiReadinessDetail() }.getOrNull()
    val apiRow = if (apiDetail == null) ReadyRow("微信 API", "数据库 / 联系人 / 账号就绪", ReadyState.OK)
    else ReadyRow("微信 API", apiDetail, ReadyState.FAIL)

    val dexInfo = runCatching { com.johnny.wxscript.features.hchat.HchatScriptBootstrap.dexParseInfo() }
        .getOrDefault("未初始化")
    val dexRow = if (dexInfo == "未初始化" || dexInfo.contains("null")) ReadyRow("DexKit 索引", dexInfo, ReadyState.RUNNING)
    else ReadyRow("DexKit 索引", dexInfo, ReadyState.OK)

    return listOf(engineRow, apiRow, dexRow)
}

private fun collectTaskSummary(): String = runCatching {
    val tasks = h.Hchat.hooks.core.DexInstallScheduler.snapshot()
    if (tasks.isEmpty()) return@runCatching "调度器尚未启动"
    val installed = tasks.count { it.installed }
    val running = tasks.count { it.running }
    val pending = h.Hchat.hooks.core.DexInstallScheduler.pendingCount()
    val failed = h.Hchat.hooks.core.DexInstallScheduler.failedCount()
    "阶段 ${h.Hchat.hooks.core.DexInstallScheduler.readyLevelName()} · 共 ${tasks.size} 项 · 已装 $installed · 进行中 $running · 待装 $pending · 失败 $failed"
}.getOrElse { "不可用（${it.message ?: it.javaClass.simpleName}）" }

private fun collectDiagnostics(context: Context): String = buildString {
    appendLine("进程：${currentProcessName()}")
    appendLine("宿主包名：${runCatching { com.johnny.wxscript.utils.HostInfo.packageName }.getOrDefault("未初始化")}")
    appendLine("微信版本：${runCatching { com.johnny.wxscript.utils.HostInfo.versionName }.getOrDefault("未知")}")
    appendLine("引导状态：${runCatching { com.johnny.wxscript.features.hchat.HchatScriptBootstrap.status }.getOrDefault("未执行")}")
    appendLine("DexKit：${runCatching { com.johnny.wxscript.features.hchat.HchatScriptBootstrap.dexParseInfo() }.getOrDefault("未初始化")}")
    appendLine("任务：${collectTaskSummary()}")
    appendLine("插件目录：${runCatching { h.Hchat.hooks.items.script.ScriptPluginRuntime.scriptDir(context).absolutePath }.getOrDefault("不可用")}")
}

private fun isHostLoaded(): Boolean = hostProcessName() == BuildConfig.HOST_PACKAGE

private fun currentProcessName(): String = hostProcessName()

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Suppress("unused")
private fun applicationName(app: Application): String = app.packageName
