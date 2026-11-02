package com.johnny.wxscript.activity

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.ui.WxTheme

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
                1 -> ScriptsPage()
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
    val rows = listOf(
        ReadyRow("脚本引擎", "Java (BeanShell) / JS 未加载", ReadyState.FAIL),
        ReadyRow("微信 API", "数据库 / 联系人 / 账号", ReadyState.FAIL),
        ReadyRow("DexKit 索引", "尚未建立", ReadyState.FAIL),
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("运行状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "P1 骨架阶段：引擎与微信 API 将在后续版本接入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    rows.forEach { row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusIcon(row.state)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
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
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("快捷操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("导入脚本 · 打开市场 · 运行诊断（P3 接入）", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ScriptsPage() {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            ListItem(
                headlineContent = { Text("尚未导入任何脚本") },
                supportingContent = { Text("支持 .bsh（Java/BeanShell）与 .js（JavaScript）两种脚本") },
            )
            HorizontalDivider()
        }
        item {
            ListItem(
                headlineContent = { Text("脚本插件列表将在此显示") },
                supportingContent = { Text("单列列表：名称 · 版本 · 作者 · 右侧启用开关 · 右滑操作") },
            )
        }
    }
}

@Composable
private fun MarketPage() {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            ListItem(
                headlineContent = { Text("在线插件市场") },
                supportingContent = { Text("P4 接入：搜索 · 分类 · 下载量排序 · 可更新徽标") },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun MinePage() {
    val entries = listOf(
        "微信宿主" to "com.tencent.mm",
        "模块版本" to BuildConfig.VERSION_NAME,
        "API 文档" to "P4 接入",
        "诊断中心" to "P4 接入",
        "导出日志" to "P4 接入",
    )
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(entries) { (title, value) ->
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = { Text(value) },
            )
            HorizontalDivider()
        }
        item {
            ListItem(
                headlineContent = { Text("关于") },
                supportingContent = {
                    Text("WxScript · 独立微信脚本模块\n作者：Johnny520\n与 WCX 主模块相互独立，可同时安装使用")
                },
            )
        }
    }
}
