package com.johnny.wxscript.ui.docs

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 插件 API 速查（P3 版：只写**代码里真实存在的**东西，完整 API 文档随 P4 提供）。
 */
@Composable
fun ApiDocsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text("插件 API", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item { DocTitle("插件目录") }
            item {
                DocText(
                    "每个插件是一个目录，至少包含两个文件：\n\n" +
                        "main.java   插件主体，BeanShell 解释执行（Java 语法）\n" +
                        "info.prop   元信息（Properties 格式）\n\n" +
                        "可选：README.md（在列表里点开可看）、main.java.bshs（市场安装时的 BeanShell 快照）"
                )
            }

            item { DocTitle("info.prop 键") }
            item {
                DocCode(
                    "name=示例插件\n" +
                        "author=作者名\n" +
                        "version=1.0.0\n" +
                        "updateTime=2026-11-02\n" +
                        "process=main"
                )
            }
            item {
                DocText(
                    "process 为空或 main 时只在微信主进程加载；需要小程序进程时写 appbrand；" +
                        "多个用逗号分隔。"
                )
            }

            item { DocTitle("脚本内可直接使用的变量") }
            item {
                DocCode(
                    "context / hostContext    宿主 Context\n" +
                        "classLoader              模块 ClassLoader\n" +
                        "scriptDir / pluginDir    脚本根目录 / 当前插件目录\n" +
                        "cacheDir                 插件缓存目录\n" +
                        "pluginId / pluginName    当前插件标识 / 名称\n" +
                        "pluginAuthor / pluginVersion / pluginUpdateTime\n" +
                        "processName / pluginProcess / isMainProcess / isAppBrandProcess\n" +
                        "hostVerName / hostVerCode / hostVerClient / moduleVer\n" +
                        "wa / waBridge            微信能力桥（发消息、联系人、数据库等）\n" +
                        "audio / audioBridge      音频转换桥\n" +
                        "bridge                   插件桥（弹窗、日志等）"
                )
            }

            item { DocTitle("加载与热更新") }
            item {
                DocText(
                    "保存 main.java 后运行时会自动重载（约 500ms 防抖）；" +
                        "在编辑器里点 ▶ 等价于「保存 + 启用」，点 ■ 等价于卸载该插件。\n\n" +
                        "插件加载失败的原因会写到插件目录下的 log.txt，并在诊断页「本地插件」一项里体现。"
                )
            }

            item { DocTitle("完整文档") }
            item {
                DocText("完整的 wa 桥 API 列表（发送消息 / 联系人 / 群 / 朋友圈 / 数据包等）在 P4 随在线市场一起发布，\n" +
                    "当前可先参考 GitHub 仓库 docs/ 下的脚本相关说明。")
            }
            item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
        }
    }
}

@Composable
private fun DocTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun DocText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DocCode(code: String) {
    Text(
        code,
        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
    )
}
