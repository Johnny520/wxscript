package com.johnny.wxscript.ui.script

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnny.wxscript.ui.HostLauncher

/**
 * 「脚本」Tab 入口。
 *
 * 脚本运行时（插件加载、微信 API、FastKV 配置）全部绑定在微信进程，因此：
 * - 在微信进程内 → 直接渲染 [ScriptsTab]；
 * - 在模块自身进程 → 只给一张「在微信中打开」的说明卡，不展示任何读不到的数据。
 */
@Composable
fun ScriptPluginsPage(
    onEdit: (String) -> Unit,
    onLogs: (String) -> Unit,
    onDiagnostics: () -> Unit,
) {
    if (!ScriptData.isHostProcess()) {
        val context = LocalContext.current
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "脚本引擎只在微信进程内可用",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "插件列表、开关、日志都绑定在微信进程里（FastKV 配置与微信 API 都在那边）。" +
                    "点下面的按钮会在微信里打开同一个设置页，数据完全一致。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { HostLauncher.openSettingsInHost(context) }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("在微信中打开设置")
            }
        }
        return
    }
    ScriptsTab(onEdit = onEdit, onLogs = onLogs, onDiagnostics = onDiagnostics)
}
