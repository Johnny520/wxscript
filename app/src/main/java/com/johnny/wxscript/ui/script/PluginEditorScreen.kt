package com.johnny.wxscript.ui.script

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnny.wxscript.ui.components.StateBox
import com.johnny.wxscript.utils.WeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内置脚本编辑器（P3：内置为主，不跳外部编辑器，避免「改完回不来」）。
 *
 * 顶栏 = 文件名 + 未保存圆点 + ▶ / ■ + 格式化 + 更多；
 * 底部三档控制台（隐藏 / 半屏 / 全屏），日志行里的「第 N 行 / line N」可点击跳到对应位置。
 */
@Composable
fun PluginEditorScreen(
    pluginId: String,
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var item by remember(pluginId) { mutableStateOf<ScriptData.PluginItem?>(null) }
    var value by remember(pluginId) { mutableStateOf(TextFieldValue("")) }
    var savedText by remember(pluginId) { mutableStateOf("") }
    var consoleMode by remember { mutableIntStateOf(1) } // 0 隐藏 / 1 半屏 / 2 全屏
    var menuOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pluginId) {
        val loaded = withContext(Dispatchers.IO) {
            ScriptData.listPlugins(context).firstOrNull { it.id == pluginId }
        }
        item = loaded
        if (loaded != null) {
            val src = withContext(Dispatchers.IO) { ScriptData.readMainSource(loaded) }
            value = TextFieldValue(src)
            savedText = src
        }
    }

    val dirty = value.text != savedText
    val current = item

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        current?.displayName ?: pluginId,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (dirty) {
                        Spacer(Modifier.size(6.dp))
                        Box(
                            Modifier.size(7.dp).background(
                                MaterialTheme.colorScheme.error,
                                androidx.compose.foundation.shape.CircleShape,
                            )
                        )
                    }
                }
                Text("main.java", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = {
                val target = current ?: return@IconButton
                scope.launch {
                    val res = withContext(Dispatchers.IO) {
                        if (dirty) ScriptData.saveMainSource(context, target, value.text)
                        else Result.success(Unit)
                    }
                    res.fold(
                        onSuccess = {
                            savedText = value.text
                            val run = withContext(Dispatchers.IO) {
                                ScriptData.setPluginEnabled(context, target.id, true)
                            }
                            run.fold(
                                onSuccess = {
                                    message = "已保存并运行"
                                    if (consoleMode == 0) consoleMode = 1
                                },
                                onFailure = { message = "保存成功，但运行失败：" + (it.message ?: "未知原因") },
                            )
                        },
                        onFailure = { message = "保存失败：" + (it.message ?: "未知原因") },
                    )
                }
            }) { Icon(Icons.Filled.PlayArrow, contentDescription = "保存并运行") }

            IconButton(onClick = {
                val target = current ?: return@IconButton
                scope.launch {
                    val res = withContext(Dispatchers.IO) { ScriptData.setPluginEnabled(context, target.id, false) }
                    res.fold(
                        onSuccess = { message = "已停止" },
                        onFailure = { message = "停止失败：" + (it.message ?: "未知原因") },
                    )
                }
            }) { Icon(Icons.Filled.Stop, contentDescription = "停止") }

            TextButton(onClick = { value = TextFieldValue(formatSource(value.text)) }) { Text("格式化") }

            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "更多") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("保存") }, onClick = {
                        menuOpen = false
                        val target = current ?: return@DropdownMenuItem
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                ScriptData.saveMainSource(context, target, value.text)
                            }
                            res.fold(
                                onSuccess = {
                                    savedText = value.text
                                    message = "已保存"
                                },
                                onFailure = { message = "保存失败：" + (it.message ?: "未知原因") },
                            )
                        }
                    })
                    DropdownMenuItem(text = { Text("重新载入文件") }, onClick = {
                        menuOpen = false
                        val target = current ?: return@DropdownMenuItem
                        scope.launch {
                            val src = withContext(Dispatchers.IO) { ScriptData.readMainSource(target) }
                            value = TextFieldValue(src)
                            savedText = src
                            message = "已重新载入"
                        }
                    })
                    DropdownMenuItem(text = { Text("查看运行日志") }, onClick = {
                        menuOpen = false
                        onOpenLogs()
                    })
                }
            }
        }

        if (current == null) {
            StateBox("⚠️", "插件不存在", "它可能刚被卸载，返回列表刷新试试", "返回", onBack)
            return@Column
        }

        Box(
            Modifier.weight(1f).fillMaxWidth()
        ) {
            val scroll = rememberScrollState()
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(12.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${value.text.count { it == '\n' } + 1} 行 · ${value.text.length} 字符" +
                    if (dirty) " · 未保存" else "",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            FilterChip(consoleMode == 0, { consoleMode = 0 }, { Text("隐藏") })
            FilterChip(consoleMode == 1, { consoleMode = 1 }, { Text("半屏") })
            FilterChip(consoleMode == 2, { consoleMode = 2 }, { Text("全屏") })
        }
        if (consoleMode > 0) {
            ConsolePane(
                plugin = current,
                mode = consoleMode,
                onJumpToLine = { line ->
                    val offset = offsetOfLine(value.text, line)
                    value = value.copy(selection = TextRange(offset))
                },
            )
        }
    }

    message?.let {
        LaunchedEffect(it) {
            delay(1800)
            message = null
        }
        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(it, Modifier.fillMaxWidth().padding(10.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ConsolePane(plugin: ScriptData.PluginItem, mode: Int, onJumpToLine: (Int) -> Unit) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var paused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val height = if (mode == 2) 320.dp else 168.dp

    LaunchedEffect(plugin.id, paused) {
        while (true) {
            if (!paused) {
                val fresh = withContext(Dispatchers.IO) {
                    val (_, tail) = WeLogger.readRecentLines(400)
                    tail.filter { it.contains(plugin.name, true) || it.contains(plugin.id, true) }
                        .takeLast(200)
                }
                lines = fresh
                if (fresh.isNotEmpty()) {
                    listState.animateScrollToItem((fresh.size - 1).coerceAtLeast(0))
                }
            }
            delay(1500)
        }
    }

    Column(Modifier.fillMaxWidth().height(height).background(Color(0xFF141414))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "控制台 · ${lines.size} 行",
                color = Color(0xFF9CCC65),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { paused = !paused }) {
                Text(if (paused) "继续" else "暂停", style = MaterialTheme.typography.labelSmall)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(lines) { line ->
                val level = line.getOrNull(24)
                val color = when (level) {
                    'E', 'A' -> Color(0xFFFF6B6B)
                    'W' -> Color(0xFFFFD54F)
                    'I' -> Color(0xFFE0E0E0)
                    else -> Color(0xFF9E9E9E)
                }
                val lineNo = remember(line) { parseLineNumber(line) }
                Text(
                    line,
                    color = color,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = lineNo != null) { lineNo?.let(onJumpToLine) }
                        .padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** 从日志行里解析「第 12 行 / line 12 / .java:12:」这类行号，供点击跳转。 */
private fun parseLineNumber(line: String): Int? {
    val patterns = listOf(
        Regex("""(?i)\bline[:\s]+(\d+)"""),
        Regex("""第\s*(\d+)\s*行"""),
        Regex("""\.java:(\d+):"""),
    )
    return patterns.firstNotNullOfOrNull { it.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() }
}

private fun offsetOfLine(text: String, line: Int): Int {
    if (line <= 1) return 0
    var currentLine = 1
    text.forEachIndexed { index, c ->
        if (c == '\n') {
            currentLine++
            if (currentLine == line) return index + 1
        }
    }
    return text.length
}

/**
 * 极简「格式化」：去掉行尾空白 + 压缩连续空行。
 * 不做真正的语法解析（BeanShell 不是标准 Java），只处理最常见的格式问题。
 */
private fun formatSource(src: String): String {
    val out = StringBuilder()
    var blank = 0
    src.lines().forEach { raw ->
        val line = raw.trimEnd()
        if (line.isBlank()) {
            blank++
            if (blank <= 1) out.append('\n')
            return@forEach
        }
        blank = 0
        out.append(line).append('\n')
    }
    return out.toString()
}
