package com.johnny.wxscript.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.johnny.wxscript.ui.WxTheme
import com.johnny.wxscript.ui.diagnostics.DiagnosticsScreen
import com.johnny.wxscript.ui.docs.ApiDocsScreen
import com.johnny.wxscript.ui.home.EngineBanner
import com.johnny.wxscript.ui.home.HomeTab
import com.johnny.wxscript.ui.logs.LogsScreen
import com.johnny.wxscript.ui.market.MarketDetailScreen
import com.johnny.wxscript.ui.market.MarketNotificationsScreen
import com.johnny.wxscript.ui.market.MarketUploadScreen
import com.johnny.wxscript.ui.market.MarketTab
import com.johnny.wxscript.ui.mine.MineTab
import com.johnny.wxscript.ui.script.PluginEditorScreen
import com.johnny.wxscript.ui.script.ScriptPluginsPage
import com.johnny.wxscript.utils.hostProcessName

/** 页面路由（单层栈：主界面 ⇄ 二级页，二级页返回即回主界面）。 */
sealed interface WxRoute {
    data object Root : WxRoute
    data class Editor(val pluginId: String) : WxRoute
    data object Diagnostics : WxRoute
    data object Logs : WxRoute
    data object ApiDocs : WxRoute
    data class MarketDetail(val remotePluginId: String) : WxRoute
    data object MarketNotifications : WxRoute
    data object MarketUpload : WxRoute
}

/** WxScript 设置主界面：4 个底栏 Tab（首页 / 脚本 / 市场 / 我的），默认落在「脚本」。 */
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
    var tab by remember { mutableIntStateOf(1) }
    var route by remember { mutableStateOf<WxRoute>(WxRoute.Root) }

    val openDiagnostics = { route = WxRoute.Diagnostics }
    val openLogs = { route = WxRoute.Logs }
    val openApiDocs = { route = WxRoute.ApiDocs }

    BackHandler(enabled = route != WxRoute.Root) { route = WxRoute.Root }

    when (val current = route) {
        is WxRoute.Editor -> {
            PluginEditorScreen(
                pluginId = current.pluginId,
                onBack = { route = WxRoute.Root },
                onOpenLogs = { route = WxRoute.Logs },
            )
            return
        }
        WxRoute.Diagnostics -> {
            DiagnosticsScreen(onBack = { route = WxRoute.Root })
            return
        }
        WxRoute.Logs -> {
            LogsScreen(onBack = { route = WxRoute.Root })
            return
        }
        WxRoute.ApiDocs -> {
            ApiDocsScreen(onBack = { route = WxRoute.Root })
            return
        }
        WxRoute.MarketNotifications -> {
            MarketNotificationsScreen(
                onBack = { route = WxRoute.Root },
                onOpenPlugin = { id -> route = WxRoute.MarketDetail(id) },
            )
            return
        }
        WxRoute.MarketUpload -> {
            MarketUploadScreen(
                onBack = { route = WxRoute.Root },
                onOpenMarket = { tab = 2; route = WxRoute.Root },
            )
            return
        }
        is WxRoute.MarketDetail -> {
            MarketDetailScreen(
                remotePluginId = current.remotePluginId,
                onBack = { route = WxRoute.Root },
            )
            return
        }
        WxRoute.Root -> Unit
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(TABS[tab].title) }) },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                    )
                }
            }
        },
    ) { inner ->
        Surface(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.fillMaxSize()) {
                // 引擎未就绪时每个 Tab 顶部都有一条细横幅（引擎状态不单独开 Tab）
                EngineBanner(onOpenDiagnostics = openDiagnostics)
                when (tab) {
                    0 -> HomeTab(
                        onOpenScripts = { tab = 1 },
                        onOpenDiagnostics = openDiagnostics,
                        onOpenLogs = openLogs,
                    )
                    1 -> ScriptPluginsPage(
                        onEdit = { pluginId -> route = WxRoute.Editor(pluginId) },
                        onLogs = { route = WxRoute.Logs },
                        onDiagnostics = openDiagnostics,
                    )
                    2 -> MarketTab(
                        onOpenDetail = { id -> route = WxRoute.MarketDetail(id) },
                        onOpenNotifications = { route = WxRoute.MarketNotifications },
                        onOpenUpload = { route = WxRoute.MarketUpload },
                    )
                    else -> MineTab(
                        onOpenDiagnostics = openDiagnostics,
                        onOpenLogs = openLogs,
                        onOpenApiDocs = openApiDocs,
                    )
                }
            }
        }
    }
}

@Suppress("unused")
private fun currentProcessLabel(): String = hostProcessName()
