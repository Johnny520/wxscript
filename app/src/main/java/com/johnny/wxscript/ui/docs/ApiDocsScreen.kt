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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnny.wxscript.ui.components.SearchField
import com.johnny.wxscript.ui.components.SectionHeader
import com.johnny.wxscript.ui.components.SegmentedTabs

/**
 * 插件 API 文档。
 *
 * API 清单来自 [ApiDocData]（由 tools/gen_apidoc.py 从桥接层源码生成），
 * 因此这里**只列真实存在的方法**；改桥接层后重新生成即可同步。
 *
 * 指南部分（目录结构 / info.prop / 脚本变量 / 回调）与运行时实现对得上：
 * 变量名取自 ScriptPluginRuntime 注入 prelude，回调名取自 useXxx / onXxx 约定。
 */
@Composable
fun ApiDocsScreen(onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("") }

    val tabs = listOf(
        "指南",
        "wa (${ApiDocData.waApis.size})",
        "bridge (${ApiDocData.bridgeApis.size})",
        "audio (${ApiDocData.audioApis.size})",
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text("插件 API", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        SegmentedTabs(tabs, tab, { tab = it })
        if (tab != 0) {
            SearchField(filter, "搜索方法名 / 参数 / 返回类型", { filter = it })
        }
        HorizontalDivider()

        if (tab == 0) {
            GuideContent()
        } else {
            val source = when (tab) {
                1 -> ApiDocData.waApis
                2 -> ApiDocData.bridgeApis
                else -> ApiDocData.audioApis
            }
            val matched = remember(source, filter) {
                val key = filter.trim()
                if (key.isEmpty()) source else source.filter { it.contains(key, ignoreCase = true) }
            }
            val grouped = remember(matched) { matched.groupBy { apiGroup(it) } }
            if (matched.isEmpty()) {
                Text(
                    "没有匹配的 API",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    grouped.forEach { (group, entries) ->
                        item(key = "h-$group") { SectionHeader("$group · ${entries.size}") }
                        entries.forEach { signature ->
                            item(key = "a-$group-$signature") {
                                Text(
                                    signature,
                                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 按方法名前缀分组，便于在 140+ 个签名里定位。 */
private fun apiGroup(signature: String): String {
    val name = signature.substringBefore('(')
    return when {
        name.startsWith("send") || name.startsWith("reply") || name.startsWith("upload") ||
            name.startsWith("forward") || name.startsWith("revoke") || name.startsWith("edit") ||
            name.startsWith("insert") || name.startsWith("transfer") -> "消息与发送"
        name.startsWith("http") -> "网络"
        name.startsWith("get") || name.startsWith("is") || name.startsWith("has") ||
            name.startsWith("count") || name.startsWith("read") || name.startsWith("query") ||
            name.startsWith("list") || name.startsWith("find") || name.startsWith("load") -> "查询与读取"
        name.startsWith("hook") || name.startsWith("use") || name.startsWith("unhook") -> "Hook 与回调"
        name.startsWith("to") || name.startsWith("from") || name.startsWith("mp3") ||
            name.startsWith("wav") || name.startsWith("flac") || name.startsWith("ogg") ||
            name.startsWith("pcm") || name.startsWith("silk") || name.startsWith("auto") ||
            name.startsWith("decode") || name.startsWith("encode") -> "音频转换"
        else -> "操作与设置"
    }
}

@Composable
private fun GuideContent() {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item { DocTitle("插件目录") }
        item {
            DocText(
                "每个插件是一个目录，至少两个文件：\n\n" +
                    "main.java    插件主体，BeanShell 解释执行（Java 语法，不需要编译）\n" +
                    "info.prop    元信息（Properties 格式）\n\n" +
                    "可选：README.md（脚本列表里可查看）、main.java.bshs（市场安装时生成的快照）、" +
                    "log.txt（加载失败时由运行时会写入原因）"
            )
        }

        item { DocTitle("info.prop") }
        item {
            DocCode(
                "name=示例插件\n" +
                    "author=作者名\n" +
                    "version=1.0.0\n" +
                    "updateTime=2026-11-02\n" +
                    "process=main\n" +
                    "description=一句话说明\n"
            )
        }
        item {
            DocText(
                "process：留空或 main 表示只在微信主进程加载；需要小程序进程时写 appbrand；" +
                    "多个用逗号分隔。description 会显示在脚本列表与市场详情里。"
            )
        }

        item { DocTitle("脚本内可直接使用的变量") }
        item {
            DocCode(
                "context / hostContext    宿主 Context\n" +
                    "classLoader              模块 ClassLoader\n" +
                    "scriptDir / scriptDirFile / pluginDir / pluginDirFile\n" +
                    "cacheDir / cacheDirFile  插件缓存目录（文件对象带 File 后缀）\n" +
                    "pluginId / pluginName / pluginAuthor / pluginVersion / pluginUpdateTime\n" +
                    "processName / pluginProcess / isMainProcess / isAppBrandProcess\n" +
                    "hostVerName / hostVerCode / hostVerClient / moduleVer\n" +
                    "wa / waBridge            微信能力桥（见 wa 页）\n" +
                    "bridge                   插件桥（弹窗 / 存储 / Hook / DexKit，见 bridge 页）\n" +
                    "audio / audioBridge      音频转换桥（见 audio 页）\n" +
                    "dexKit / dexFinder / dexBridgeHolder   DexKit 检索与 DexFinder\n" +
                    "apis / http / httpClient 桥接对象别名"
            )
        }

        item { DocTitle("回调（在 main.java 里定义同名方法即可）") }
        item {
            DocCode(
                "onLoad()                      插件加载完成\n" +
                    "onUnload()                    插件卸载\n" +
                    "onHandleMsg(MsgInfoBean)      收到消息（主进程）\n" +
                    "onClickSendBtn() / onLongClickSendBtn()   发送按钮点击 / 长按\n" +
                    "onImageDownload(...) / onVideoDownload(...) / onFinderMediaDownload(...)\n" +
                    "onMemberChange(type, groupWxid, userWxid, userName)\n" +
                    "onNewFriend(wxid, ticket, scene)\n" +
                    "onProtobufPacket(...)         数据包监听\n" +
                    "openSettings()                插件齿轮被点击"
            )
        }
        item {
            DocText(
                "如果回调定义在别的方法里，用 bridge.useOnLoad(\"方法名\") 这类 use 方法登记；" +
                    "onHandleMsg 之类按约定直接以 onXxx 命名即可。"
            )
        }

        item { DocTitle("加载与热更新") }
        item {
            DocText(
                "保存 main.java 后运行时会自动重载（约 500ms 防抖），前提是插件已启用。" +
                    "在编辑器里点 ▶ 等价于「保存 + 启用」，点 ■ 卸载该插件。\n\n" +
                    "加载失败的原因写在插件目录 log.txt，同时进入诊断页「本地插件」一项。"
            )
        }

        item { DocTitle("常见问题") }
        item {
            DocText(
                "1) 「Class: Xxx not found in namespace」——发布版开启了 R8，凡只被脚本按类名引用的类" +
                    "都要在 proguard 里 keep；模块内的脚本 ABI 已做锚点（ScriptPluginAbi）。\n" +
                    "2) 「微信数据库 API 未就绪」——DexKit 解析尚未完成或引导失败，去诊断页看 DexKit Hook 任务。\n" +
                    "3) 插件不加载——先确认总开关开启、插件已启用、process 与当前进程匹配。"
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
        item {
            DocText("wa / bridge / audio 三页为源码生成的完整签名列表（共 ${ApiDocData.waApis.size + ApiDocData.bridgeApis.size + ApiDocData.audioApis.size} 项）。")
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
