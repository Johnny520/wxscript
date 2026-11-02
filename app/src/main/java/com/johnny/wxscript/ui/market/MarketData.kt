package com.johnny.wxscript.ui.market

import android.content.Context
import com.johnny.wxscript.ui.script.ScriptData
import h.Hchat.hooks.items.script.market.PluginMarketClient
import h.Hchat.hooks.items.script.market.PluginMarketHistoryVersion
import h.Hchat.hooks.items.script.market.PluginMarketInstallResult
import h.Hchat.hooks.items.script.market.PluginMarketInstaller
import h.Hchat.hooks.items.script.market.PluginMarketPlugin
import h.Hchat.hooks.items.script.market.PluginMarketSettings
import java.util.UUID

/**
 * 插件市场数据层。
 *
 * 界面只认这里的模型：把「远程条目」和「本地已安装插件」对齐后给出
 * installed / updateAvailable，界面不需要自己猜目录名或比版本号。
 *
 * 所有网络都在 IO 线程调用（界面侧 withContext(Dispatchers.IO)），
 * 这里不切线程，方便复用与测试。
 */
object MarketData {

    /** 列表排序：最新发布 / 下载最多。与服务端 `sort` 参数一一对应。 */
    enum class Sort(val apiValue: String, val label: String) {
        LATEST("latest", "最新"),
        DOWNLOADS("downloads", "最热"),
    }

    /** 列表筛选：全部 / 已安装 / 可更新。 */
    enum class Filter(val label: String) {
        ALL("全部"),
        INSTALLED("已安装"),
        UPDATABLE("可更新"),
    }

    data class Item(
        val plugin: PluginMarketPlugin,
        val localId: String,
        val installedVersion: String?,
        val updateAvailable: Boolean,
    ) {
        val installed: Boolean get() = installedVersion != null
        val title: String get() = plugin.name.ifBlank { plugin.sourcePluginId }
        val subtitle: String
            get() = buildString {
                if (plugin.author.isNotBlank()) append(plugin.author)
                if (plugin.version.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("v").append(plugin.version)
                }
                if (installedVersion != null && installedVersion.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append("已安装 ").append("v").append(installedVersion)
                }
            }
    }

    // ───────────────────────────── 服务器 ─────────────────────────────

    fun serverUrl(context: Context): String = runCatching {
        PluginMarketSettings.serviceUrl(context)
    }.getOrDefault("")

    fun presetUrls(): List<String> = runCatching { PluginMarketSettings.presetServiceUrls() }
        .getOrDefault(emptyList())

    fun presetLabel(url: String): String = runCatching { PluginMarketSettings.presetLabel(url) }
        .getOrDefault(url)

    fun saveServerUrl(context: Context, url: String): Result<Unit> = runCatching {
        val normalized = url.trim().trimEnd('/')
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "地址必须以 http:// 或 https:// 开头"
        }
        require(normalized.length <= 300) { "地址过长" }
        PluginMarketSettings.saveServiceUrl(context, normalized)
    }

    /** 探测当前服务器是否可用（/health）。 */
    fun probeHealth(context: Context): Result<String> = runCatching {
        val health = PluginMarketClient.health(context).getOrThrow()
        buildString {
            append(health.status.ifBlank { "unknown" })
            if (health.database.isNotBlank()) append(" · db=").append(health.database)
            if (health.message.isNotBlank()) append(" · ").append(health.message)
        }
    }

    // ───────────────────────────── 列表 / 详情 ─────────────────────────────

    fun load(context: Context, query: String, sort: Sort, limit: Int = 100): Result<List<Item>> =
        runCatching {
            val page = PluginMarketClient.list(context, query, sort.apiValue, limit).getOrThrow()
            val local = ScriptData.listPlugins(context).associateBy { it.id }
            page.items.map { plugin -> align(context, plugin, local) }
        }

    fun detail(context: Context, remotePluginId: String): Result<Item> = runCatching {
        val plugin = PluginMarketClient.detail(context, remotePluginId).getOrThrow()
        align(context, plugin, ScriptData.listPlugins(context).associateBy { it.id })
    }

    fun history(context: Context, remotePluginId: String): Result<List<PluginMarketHistoryVersion>> =
        runCatching { PluginMarketClient.history(context, remotePluginId).getOrThrow() }

    private fun align(
        context: Context,
        plugin: PluginMarketPlugin,
        local: Map<String, ScriptData.PluginItem>,
    ): Item {
        val localId = PluginMarketInstaller.defaultLocalPluginId(plugin)
        val legacyId = runCatching { PluginMarketInstaller.existingLocalPluginId(context, plugin) }.getOrNull()
        val installed = local[localId] ?: legacyId?.let { local[it] }
        val installedVersion = installed?.version?.takeIf { it.isNotBlank() }
        return Item(
            plugin = plugin,
            localId = installed?.id ?: localId,
            installedVersion = installedVersion,
            updateAvailable = installedVersion != null &&
                isNewer(plugin.version, installedVersion),
        )
    }

    /** 远程版本比本地新才提示更新；任一侧无法解析时保守按「不同即可更新」处理。 */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.trim().removePrefix("v").removePrefix("V")
        val l = local.trim().removePrefix("v").removePrefix("V")
        if (r.isBlank() || l.isBlank()) return false
        if (r == l) return false
        val rp = r.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        val lp = l.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        if (rp.isEmpty() || lp.isEmpty()) return true
        for (i in 0 until maxOf(rp.size, lp.size)) {
            val a = rp.getOrElse(i) { 0 }
            val b = lp.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** 详情页正文：优先 README.md / README，其次 description。 */
    fun readme(plugin: PluginMarketPlugin): String {
        val files = plugin.files
        val readme = files.firstOrNull { it.name.equals("README.md", true) }
            ?: files.firstOrNull { it.name.startsWith("README", true) }
        val text = readme?.content.orEmpty()
        return when {
            text.isBlank() && plugin.description.isNotBlank() -> plugin.description
            text.isBlank() -> "该插件没有提供 README"
            else -> text.take(40_000)
        }
    }

    // ───────────────────────────── 安装 ─────────────────────────────

    fun install(
        context: Context,
        item: Item,
        overwrite: Boolean,
    ): Result<PluginMarketInstallResult> = runCatching {
        val result = PluginMarketInstaller.install(
            context = context,
            plugin = item.plugin,
            overwrite = overwrite,
            localPluginId = item.localId.takeIf { it.isNotBlank() },
        ).getOrThrow()
        // 下载计数是「尽力而为」的埋点：失败不影响安装结果。
        runCatching {
            PluginMarketClient.recordDownload(
                context,
                item.plugin.remotePluginId,
                item.plugin.versionId,
                UUID.randomUUID().toString().replace("-", ""),
            )
        }
        result
    }

    /** 安装后重载插件，让新代码立即生效（失败不阻断安装流程）。 */
    fun reloadAfterInstall(context: Context, pluginId: String): Result<Unit> =
        ScriptData.reloadPlugin(context, pluginId)
}
