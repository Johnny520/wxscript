package com.johnny.wxscript.features.hchat

import com.johnny.wxscript.dexkit.abc.IResolveDex
import com.johnny.wxscript.dexkit.cache.DexCacheManager
import com.johnny.wxscript.features.core.BaseFeature
import com.johnny.wxscript.features.core.WxFeatures
import com.johnny.wxscript.utils.HostInfo
import com.johnny.wxscript.utils.WeLogger
import org.luckypray.dexkit.DexKitBridge

/**
 * 安装 WxScript 侧的 Feature（微信 API 服务 + Java/JS 脚本引擎）。
 *
 * WCX 里这一步由 `FeaturesLoader.loadFeatures()` 完成，它顺带做了设置项注入、启动 toast、
 * DexKit 修复弹窗等一堆 UI 相关工作；WxScript 只要「解析 Dex → 启动功能」这两步，
 * 因此这里重写为精简版：
 *
 * 1. [DexCacheManager.init] —— 微信版本变化时清空旧缓存（否则旧 descriptor 会指向不存在的类）；
 * 2. 对每个 [IResolveDex]：先尝试从缓存恢复委托，缺失才走 DexKit 扫描，成功后回写缓存；
 * 3. [BaseFeature.startup] —— 开关打开的功能在这里装 hook。
 *
 * 全程逐个 runCatching 隔离：任何一个 Feature 挂掉都不能影响后面的（尤其是脚本引擎）。
 */
object ScriptFeaturesInstaller {

    private const val TAG = "ScriptFeatures"

    @Volatile
    var status: String = "未执行"
        private set

    @Synchronized
    fun install(dexKit: DexKitBridge) {
        val startedAt = System.currentTimeMillis()
        status = "开始安装"

        runCatching { DexCacheManager.init(HostInfo.versionName) }
            .onFailure { WeLogger.e(TAG, "DexCacheManager.init 失败", it) }

        val features = WxFeatures.ALL
        var resolvedCount = 0
        var startedCount = 0
        val failures = mutableListOf<String>()

        features.forEach { feature ->
            runCatching {
                if (feature is IResolveDex) {
                    val cache = runCatching { DexCacheManager.loadItemCache(feature) }.getOrNull()
                    val missing = if (cache != null) {
                        feature.loadFromCache(cache)
                    } else {
                        feature.dexDelegates.map { it.key }.toSet()
                    }
                    if (missing.isNotEmpty()) {
                        WeLogger.i(TAG, "${feature.displayName}: 缓存缺失 ${missing.size} 项，重新扫描 dex")
                        feature.resolveInlineDex(dexKit)
                        runCatching { DexCacheManager.saveItemCache(feature) }
                            .onFailure { WeLogger.e(TAG, "保存 ${feature.displayName} 缓存失败", it) }
                        resolvedCount++
                    }
                }
                feature.startup()
                startedCount++
            }.onFailure {
                failures += feature.name.ifBlank { feature.javaClass.simpleName }
                WeLogger.e(TAG, "安装 ${feature.displayName} 失败", it)
            }
        }

        val elapsed = System.currentTimeMillis() - startedAt
        status = "已启动 $startedCount/${features.size} 项（重新解析 $resolvedCount 项，耗时 ${elapsed}ms）" +
            if (failures.isEmpty()) "" else "，失败：${failures.joinToString("、")}"
        WeLogger.i(TAG, status)
    }
}
