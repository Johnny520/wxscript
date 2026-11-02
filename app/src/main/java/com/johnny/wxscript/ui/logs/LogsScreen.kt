package com.johnny.wxscript.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnny.wxscript.ui.components.SearchField
import com.johnny.wxscript.ui.components.StateBox
import com.johnny.wxscript.utils.CrashRecorder
import com.johnny.wxscript.utils.WeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志页：两个 Tab
 * 1. 实时控制台 —— 分级着色 + 级别筛选 + 关键字搜索 + 暂停自动滚动 + 清空；
 * 2. 崩溃文件 —— 未捕获异常写下的文本，可查看 / 删除。
 */
@Composable
fun LogsScreen(onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text("日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            FilterChip(tab == 0, { tab = 0 }, { Text("实时控制台") })
            Spacer(Modifier.width(6.dp))
            FilterChip(tab == 1, { tab = 1 }, { Text("崩溃文件") })
            Spacer(Modifier.width(8.dp))
        }
        if (tab == 0) ConsoleTab() else CrashTab()
    }
}

@Composable
private fun ConsoleTab() {
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var paused by remember { mutableStateOf(false) }
    var minLevel by remember { mutableIntStateOf(3) } // 0=V 1=D 2=I 3=W 4=E
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(paused) {
        while (true) {
            if (!paused) {
                lines = withContext(Dispatchers.IO) {
                    runCatching { WeLogger.readRecentLines(600).second }.getOrDefault(emptyList())
                }
                if (lines.isNotEmpty()) {
                    runCatching { listState.scrollToItem((lines.size - 1).coerceAtLeast(0)) }
                }
            }
            delay(1200)
        }
    }

    val filtered = remember(lines, minLevel, query) {
        lines.filter { line ->
            val level = line.getOrNull(24)
            val levelOk = when (level) {
                'V' -> minLevel <= 0
                'D' -> minLevel <= 1
                'I' -> minLevel <= 2
                'W' -> minLevel <= 3
                'E', 'A' -> minLevel <= 4
                else -> true
            }
            levelOk && (query.isBlank() || line.contains(query, true))
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(query, "按关键字 / tag 过滤", { query = it })
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("V", "D", "I", "W", "E").forEachIndexed { index, label ->
                FilterChip(minLevel == index, { minLevel = index }, { Text(label) })
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { paused = !paused }) { Text(if (paused) "继续" else "暂停") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                scope.launch { withContext(Dispatchers.IO) { runCatching { WeLogger.clearRunLogs() } } }
            }) { Text("清空运行日志") }
            TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { WeLogger.digest().toShareText() }
                    }
                }
            }) { Text("生成摘要") }
            Spacer(Modifier.weight(1f))
            Text("${filtered.size}/${lines.size}", style = MaterialTheme.typography.labelSmall)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(Color(0xFF101010))) {
            items(filtered) { line ->
                Text(
                    line,
                    color = levelColor(line),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun CrashTab() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var viewing by remember { mutableStateOf<Pair<File, String>?>(null) }
    val files = remember(tick) { CrashRecorder.list() }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.US) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "崩溃文件 · ${files.size} 个",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { tick++ }) { Text("刷新") }
        }
        if (files.isEmpty()) {
            StateBox("🧾", "没有崩溃记录", "未捕获异常会自动写到 " + crashDirHint())
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(files) { file ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                viewing = file to runCatching { file.readText().take(60_000) }.getOrDefault("读取失败")
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${fmt.format(Date(file.lastModified()))} · ${file.length() / 1024} KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { file.delete(); tick++ }) { Text("删除") }
                    }
                    androidx.compose.material3.HorizontalDivider()
                }
            }
        }
    }

    viewing?.let { (file, content) ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(file.name) },
            text = { Text(content, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp)) },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("关闭") } },
        )
    }
}

private fun crashDirHint(): String =
    runCatching { CrashRecorder.crashDir()?.absolutePath ?: "外部存储不可用" }.getOrDefault("外部存储不可用")

private fun levelColor(line: String): Color = when (line.getOrNull(24)) {
    'E', 'A' -> Color(0xFFFF6B6B)
    'W' -> Color(0xFFFFD54F)
    'I' -> Color(0xFFE0E0E0)
    'D' -> Color(0xFF8AB4F8)
    else -> Color(0xFF9E9E9E)
}
