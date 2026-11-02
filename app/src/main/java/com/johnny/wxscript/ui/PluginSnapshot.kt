package com.johnny.wxscript.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.johnny.wxscript.ui.script.ScriptData
import h.Hchat.hooks.items.script.ScriptPluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 脚本页的界面状态 + 动作（remember 一次，多个页面共享）。
 *
 * 全部动作都在 IO 线程执行，失败一律 toast —— 不做静默吞异常，
 * 否者用户会觉得「点了没反应」。
 */
class PluginSnapshotState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var plugins by mutableStateOf<List<ScriptData.PluginItem>>(emptyList())
        private set
    var globalEnabled by mutableStateOf(false)
        private set
    var loading by mutableStateOf(true)
        private set

    /** inspectImport 的结果：待确认的导入会话 + 冲突插件 ID 列表。 */
    var pendingImport by mutableStateOf<Pair<ScriptPluginManager.ImportInspection, List<String>>?>(null)

    fun refresh() {
        scope.launch {
            loading = true
            val list = withContext(Dispatchers.IO) { ScriptData.listPlugins(context) }
            val global = withContext(Dispatchers.IO) { ScriptData.isGlobalEnabled(context) }
            plugins = list
            globalEnabled = global
            loading = false
        }
    }

    fun toggle(item: ScriptData.PluginItem, want: Boolean) {
        scope.launch {
            val res = withContext(Dispatchers.IO) { ScriptData.setPluginEnabled(context, item.id, want) }
            res.onFailure { toast("切换失败：" + (it.message ?: "未知原因")) }
            refresh()
        }
    }

    fun delete(item: ScriptData.PluginItem) {
        scope.launch {
            val res = withContext(Dispatchers.IO) { ScriptData.deletePlugin(context, item.id) }
            res.fold(
                onSuccess = { toast("已删除 ${item.displayName}") },
                onFailure = { toast("删除失败：" + (it.message ?: "未知原因")) },
            )
            refresh()
        }
    }

    fun openSettings(item: ScriptData.PluginItem) {
        val res = ScriptData.callOpenSettings(item.id)
        res.onFailure { toast("插件设置打开失败：" + (it.message ?: "插件未实现 openSettings")) }
    }

    fun toast(message: String) {
        runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}

@Composable
fun rememberPluginSnapshot(): PluginSnapshotState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { PluginSnapshotState(context, scope) }
    androidx.compose.runtime.LaunchedEffect(Unit) { state.refresh() }
    return state
}
