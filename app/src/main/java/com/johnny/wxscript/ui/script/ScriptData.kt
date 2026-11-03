package com.johnny.wxscript.ui.script

import android.content.Context
import android.net.Uri
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.utils.hostProcessName
import h.Hchat.hooks.items.script.ScriptPluginManager
import h.Hchat.hooks.items.script.ScriptPluginRuntime
import h.Hchat.hooks.items.script.ScriptPluginSettings
import h.Hchat.preferences.HchatStorage
import java.io.File

/**
 * 脚本页的数据层。
 *
 * 所有对「脚本运行时」的访问都集中在这里，界面只拿数据 + 调动作，
 * 这样进程判断（模块进程 / 微信进程）和异常兜底只有一处要维护。
 */
object ScriptData {

    /** 脚本运行时只能在微信进程内工作（插件加载与配置都绑在宿主进程）。 */
    fun isHostProcess(): Boolean = hostProcessName() == BuildConfig.HOST_PACKAGE

    data class PluginItem(
        val id: String,
        val name: String,
        val displayName: String,
        val version: String,
        val author: String,
        val description: String,
        val dir: File,
        val enabled: Boolean,
        val running: Boolean,
        val pinned: Boolean,
        /** 市场安装（有 main.java.bshs 快照）还是本地导入。 */
        val fromMarket: Boolean,
        val updatedAt: String,
    ) {
        val subtitle: String
            get() = buildString {
                if (version.isNotBlank()) append("v").append(version)
                if (author.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(author)
                }
                if (isNotEmpty()) append(" · ")
                append(if (fromMarket) "市场安装" else "本地导入")
            }
    }

    // ───────────────────────────── 读取 ─────────────────────────────

    fun isGlobalEnabled(context: Context): Boolean = runCatching {
        HchatStorage.preferences(context, ScriptPluginSettings.PREFS_NAME)
            .getBoolean(ScriptPluginSettings.KEY_ENABLE, ScriptPluginSettings.DEFAULT_ENABLE)
    }.getOrDefault(false)

    fun listPlugins(context: Context): List<PluginItem> = runCatching {
        ScriptPluginManager.listForDisplay(context).map { managed ->
            val plugin = managed.plugin
            val snapshot = runCatching { ScriptPluginRuntime.pluginRuntimeSnapshot(plugin.id) }.getOrNull()
            PluginItem(
                id = plugin.id,
                name = plugin.name,
                displayName = plugin.displayName ?: plugin.name,
                version = plugin.version,
                author = plugin.author,
                description = readDescription(plugin.dir),
                dir = plugin.dir,
                enabled = runCatching { ScriptPluginRuntime.isPluginEnabled(context, plugin.id) }.getOrDefault(false),
                running = snapshot?.loaded == true,
                pinned = managed.pinned,
                fromMarket = File(plugin.dir, "main.java.bshs").isFile,
                updatedAt = plugin.updateTime,
            )
        }
    }.getOrDefault(emptyList())

    fun readReadme(plugin: PluginItem): String = runCatching {
        val files = plugin.dir.listFiles().orEmpty()
        val file = files.firstOrNull { it.isFile && it.name.equals("README.md", true) }
            ?: files.firstOrNull { it.isFile && it.name.startsWith("README", true) }
        file?.readText()?.take(40_000) ?: "该插件没有 README"
    }.getOrDefault("读取失败")

    fun readMainSource(plugin: PluginItem): String = runCatching {
        File(plugin.dir, "main.java").readText()
    }.getOrDefault("")

    fun scriptDir(context: Context): String =
        runCatching { ScriptPluginRuntime.scriptDir(context).absolutePath }.getOrDefault("不可用")

    private fun readDescription(dir: File): String = runCatching {
        val prop = File(dir, "info.prop")
        if (!prop.isFile) return@runCatching ""
        val p = java.util.Properties()
        prop.inputStream().use { p.load(it) }
        p.getProperty("description").orEmpty()
    }.getOrDefault("")

    // ───────────────────────────── 动作 ─────────────────────────────

    fun setGlobalEnabled(context: Context, enabled: Boolean): Result<Unit> =
        runCatching { ScriptPluginRuntime.setGlobalEnabled(context, enabled) }.fold(
            onSuccess = { it },
            onFailure = { Result.failure(it) },
        )

    fun setPluginEnabled(context: Context, pluginId: String, enabled: Boolean): Result<Unit> =
        runCatching { ScriptPluginRuntime.setPluginEnabled(context, pluginId, enabled) }.fold(
            onSuccess = { it },
            onFailure = { Result.failure(it) },
        )

    fun deletePlugin(context: Context, pluginId: String): Result<Unit> =
        runCatching { ScriptPluginManager.deletePlugin(context, pluginId) }.fold(
            onSuccess = { it.map { } },
            onFailure = { Result.failure(it) },
        )

    fun reloadPlugin(context: Context, pluginId: String): Result<Unit> =
        runCatching { ScriptPluginRuntime.reloadPlugin(context, pluginId) }.fold(
            onSuccess = { it },
            onFailure = { Result.failure(it) },
        )

    fun callOpenSettings(pluginId: String): Result<Unit> =
        runCatching { ScriptPluginRuntime.callOpenSettings(pluginId) }.fold(
            onSuccess = { it },
            onFailure = { Result.failure(it) },
        )

    fun saveMainSource(context: Context, plugin: PluginItem, source: String): Result<Unit> = runCatching {
        File(plugin.dir, "main.java").writeText(source)
        // 保存后尝试热重载；未启用时 reload 会报「插件未开启」，忽略即可（文件已经落盘）
        runCatching { ScriptPluginRuntime.reloadPlugin(context, plugin.id) }
    }

    // ───────────────────────────── 导入 ─────────────────────────────

    fun inspectImport(context: Context, uri: Uri): Result<ScriptPluginManager.ImportInspection> = runCatching {
        val resolver = context.contentResolver
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取所选文件" }
            ScriptPluginManager.inspectImport(context, input).getOrThrow()
        }
    }

    fun applyImport(
        context: Context,
        sessionId: String,
        conflictPluginIds: Collection<String>,
        overwrite: Boolean,
    ): Result<ScriptPluginManager.ImportApplyResult> = runCatching {
        val action = if (overwrite) ScriptPluginManager.ImportConflictAction.OVERWRITE
        else ScriptPluginManager.ImportConflictAction.SKIP
        ScriptPluginManager.applyImport(context, sessionId, conflictPluginIds.associateWith { action }).getOrThrow()
    }

    // ───────────────────────────── 批量 / 重命名 / 导出 ─────────────────────────────

    fun renamePlugin(context: Context, pluginId: String, newName: String): Result<String> = runCatching {
        val plugin = ScriptPluginManager.renamePlugin(context, pluginId, newName).getOrThrow()
        plugin.displayName ?: plugin.name
    }

    fun setPinned(context: Context, pluginIds: Collection<String>, pinned: Boolean): Result<Unit> =
        runCatching { ScriptPluginManager.setPinned(context, pluginIds, pinned).getOrThrow() }

    /**
     * 批量启停。
     *
     * 逐个调用运行时的开关（它内部有按插件的锁与状态回写），
     * 只要有一个失败就整体返回失败，但已成功的不会回滚——把失败数量如实报给用户。
     */
    fun setPluginsEnabled(context: Context, pluginIds: Collection<String>, enabled: Boolean): Result<Int> =
        runCatching {
            var ok = 0
            val errors = ArrayList<String>()
            pluginIds.forEach { id ->
                runCatching { ScriptPluginRuntime.setPluginEnabled(context, id, enabled).getOrThrow(); ok++ }
                    .onFailure { errors += (id + ": " + (it.message ?: "未知原因")) }
            }
            if (errors.isNotEmpty()) {
                error("成功 $ok 个，失败 ${errors.size} 个：" + errors.take(3).joinToString("；"))
            }
            ok
        }

    fun deletePlugins(context: Context, pluginIds: Collection<String>): Result<Int> = runCatching {
        ScriptPluginManager.deletePlugins(context, pluginIds).getOrThrow().deletedPluginIds.size
    }

    fun exportPlugins(context: Context, pluginIds: Collection<String>, uri: Uri): Result<Int> = runCatching {
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "无法写入所选文件" }
            ScriptPluginManager.exportPlugins(context, pluginIds, output).getOrThrow().exportedPluginIds.size
        }
    }
}
