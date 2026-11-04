package com.johnny.wxscript.features.hchat

import android.os.Handler
import android.os.HandlerThread
import com.johnny.wxscript.features.api.ui.WeSettingsInjector
import com.johnny.wxscript.utils.HostInfo
import com.johnny.wxscript.utils.WeLogger
import com.johnny.wxscript.utils.reflection.ClassLoaders
import h.Hchat.dexkit.DexBridgeHolder
import h.Hchat.dexkit.DexFinder
import h.Hchat.event.EventBus
import h.Hchat.hooks.api.core.WechatApiFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.items.fakevoiceduration.FakeVoiceDurationFeature
import h.Hchat.hooks.items.protobuf.ProtobufPacketFeature
import h.Hchat.hooks.items.script.ScriptPluginFeature
import h.Hchat.hooks.items.script.ScriptPluginRuntime
import h.Hchat.preferences.ConfigStore
import h.Hchat.ui.UIRegistry
import org.luckypray.dexkit.DexKitBridge

/**
 * 脚本子系统的引导层（从 WCX 迁移）。
 *
 * Hchat 原项目的功能模块由 `FeatureRegistry` 统一注册、由 `FeatureManager` 统一安装，其中
 * 包含大量与脚本无关的功能（防撤回、朋友圈、红包等）。WxScript 只保留脚本能力，因此这里
 * 按最小闭包手动装配 [FeatureContext] 并安装必需 Feature：
 *
 * 1. [WechatApiFeature] —— 初始化公共微信 API（`WeChatApis`）。脚本运行时依赖它读取联系人
 *    数据库（rcontact/chatroom）、会话列表等；不安装它，脚本总开关会一直提示
 *    「微信联系人数据库尚未就绪」。
 * 2. [ScriptPluginFeature] —— 脚本插件运行时本体（插件加载、菜单 / 消息 Hook 等）。
 *
 * 关键点：安装完必须调用 [DexInstallScheduler.markDexBridgeReady]，否则 BRIDGE 阶段的
 * 延迟任务（含微信 API 的 `resolveAll()` 预热）永远不会执行，数据库始终不可用。
 */
object HchatScriptBootstrap {

    private const val TAG = "HchatScriptBootstrap"

    /** 就绪自检总时长与轮询间隔。 */
    private const val READINESS_TIMEOUT_MS = 60_000L
    private const val READINESS_POLL_INTERVAL_MS = 2_000L

    @Volatile
    private var installed = false

    /** 最近一次安装链路状态；供诊断页展示。 */
    @Volatile
    var status: String = "未执行"
        private set

    /** 持有 DexKitBridge 强引用，避免被回收；长期存活供脚本运行时使用。 */
    @Volatile
    private var dexKitBridgeRef: DexKitBridge? = null

    /** 持有本次引导创建的 DexFinder，供诊断页读取解析产物。 */
    @Volatile
    private var dexFinderRef: DexFinder? = null

    /** 是否已经完成过一次安装。 */
    val isInstalled: Boolean get() = installed

    /** 诊断用：DexFinder 关键解析结果。 */
    fun dexParseInfo(): String {
        val dex = dexFinderRef ?: return "未初始化"
        return "coreStorage=" + (dex.coreStorageGetter?.name ?: "null") +
            " / sqliteDb=" + (dex.sqliteDbWrapperClass?.name ?: "null") +
            " / mmKernel=" + (dex.mmKernelClass?.name ?: "null")
    }

    /** 重新安装：重置状态后重跑引导，并强制推进就绪阶段、重排全部未装任务。 */
    @Synchronized
    fun retry() {
        installed = false
        status = "重新初始化中"
        install()
        runCatching { DexInstallScheduler.ensureWarmupReady() }
            .onFailure { WeLogger.e(TAG, "ensureWarmupReady 失败", it) }
        runCatching { DexInstallScheduler.retryAllPending() }
            .onFailure { WeLogger.e(TAG, "retryAllPending 失败", it) }
    }

    /** 彻底重新初始化：重建引导链路 + 强制推进阶段 + 重排任务，返回逐步日志。 */
    @Synchronized
    fun reinitializeFully(): List<String> {
        val log = mutableListOf<String>()
        installed = false
        status = "彻底重新初始化中"

        runCatching { install() }.fold(
            onSuccess = {
                log += "已重建引导链路（DexKitBridge + WechatApiFeature + ScriptPluginFeature）"
                log += "引导状态：$status"
            },
            onFailure = { log += "重建引导链路异常：" + (it.message ?: it.javaClass.simpleName) },
        )

        runCatching { DexInstallScheduler.ensureWarmupReady() }.fold(
            onSuccess = { log += "已强制推进就绪阶段到 " + DexInstallScheduler.readyLevelName() },
            onFailure = { log += "推进就绪阶段失败：" + (it.message ?: it.javaClass.simpleName) },
        )

        runCatching { DexInstallScheduler.retryAllPending() }.fold(
            onSuccess = {
                val tasks = DexInstallScheduler.snapshot()
                log += "已重排全部 Hook 安装任务（共 ${tasks.size} 个，待装 ${DexInstallScheduler.pendingCount()} 个）"
            },
            onFailure = { log += "重排 Hook 任务失败：" + (it.message ?: it.javaClass.simpleName) },
        )
        return log
    }

    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        status = "开始安装"

        runCatching {
            val hostContext = HostInfo.application
            val hostClassLoader = ClassLoaders.HOST

            val dexKitBridge = DexKitBridge.create(HostInfo.appInfo.sourceDir)
            dexKitBridgeRef = dexKitBridge
            status = "DexKitBridge 已创建"
            WeLogger.i(TAG, "DexKitBridge created: ${HostInfo.appInfo.sourceDir}")

            val dexFinder = DexFinder(dexKitBridge, hostClassLoader, hostContext)
            dexFinderRef = dexFinder
            val dexBridgeHolder = DexBridgeHolder(
                dexKitBridge,
                dexFinder,
                hostClassLoader,
                HostInfo.appInfo.sourceDir
            )

            // moduleContext：脚本资源随模块 ClassLoader 一起加载，这里直接用宿主 Context 兜底。
            val context = FeatureContext(
                hostContext,
                hostContext,
                hostClassLoader,
                null,
                dexKitBridge,
                dexFinder,
                EventBus.get(),
                ConfigStore(hostContext),
                dexBridgeHolder,
                UIRegistry.get()
            )

            // 与 Hchat 的 FeatureManager.installAll 保持一致：先 onInit（注册设置 provider 等），再 install。
            runCatching {
                WechatApiFeature().also { it.onInit(context) }.install(context)
            }.onSuccess {
                status = "WechatApiFeature 安装成功"
                WeLogger.i(TAG, "WechatApiFeature installed")
            }.onFailure {
                status = "WechatApiFeature 安装失败：" + (it.message ?: it.javaClass.simpleName)
                WeLogger.e(TAG, "WechatApiFeature 安装失败", it)
            }
            runCatching {
                ScriptPluginFeature().also { it.onInit(context) }.install(context)
            }.onSuccess {
                status += " / ScriptPluginFeature 安装成功"
                WeLogger.i(TAG, "ScriptPluginFeature installed")
            }.onFailure {
                status += " / ScriptPluginFeature 安装失败：" + (it.message ?: it.javaClass.simpleName)
                WeLogger.e(TAG, "ScriptPluginFeature 安装失败", it)
            }

            // 追加安装「脚本插件 API」依赖的 Feature：先 onInit，仅当启用时才 install。
            listOf(ProtobufPacketFeature(), FakeVoiceDurationFeature()).forEach { extra ->
                val extraName = runCatching { extra.name() }.getOrDefault(extra.javaClass.simpleName)
                runCatching {
                    extra.onInit(context)
                    if (extra.isEnabled(context)) {
                        extra.install(context)
                        status += " / $extraName 安装成功"
                    } else {
                        status += " / $extraName 未启用"
                    }
                }.onFailure {
                    status += " / $extraName 安装失败：" + (it.message ?: it.javaClass.simpleName)
                    WeLogger.e(TAG, "$extraName 安装失败", it)
                }
            }

            // 安装 WxScript 侧 Feature（微信 API 服务 + Java/JS 脚本引擎）。
            // 必须在 DexKitBridge 建好之后、脚本插件加载之前完成：脚本的 WeChatApis 依赖这批服务。
            runCatching { ScriptFeaturesInstaller.install(dexKitBridge) }
                .onSuccess { status += " / " + ScriptFeaturesInstaller.status }
                .onFailure {
                    status += " / 脚本引擎安装失败：" + (it.message ?: it.javaClass.simpleName)
                    WeLogger.e(TAG, "ScriptFeaturesInstaller 失败", it)
                }

            // 关键：推进调度器到 BRIDGE 阶段，触发 WechatApiFeature 注册的 warmup 任务
            // （resolveAll + 数据库 API 解析），数据库才会就绪。
            runCatching { DexInstallScheduler.markDexBridgeReady() }
                .onFailure { WeLogger.e(TAG, "markDexBridgeReady 失败", it) }

            scheduleReadyWatchdog()

            // 微信设置页入口：DexKit 查询较重，单独线程做，失败不影响脚本功能。
            val entryThread = HandlerThread("WxScript-SettingsEntry").apply { start() }
            Handler(entryThread.looper).postDelayed({
                runCatching { WeSettingsInjector.install(hostContext, dexKitBridge, hostClassLoader) }
                    .onSuccess { ok ->
                        status += if (ok) " / 设置入口已注入" else " / 设置入口未注入：" + (WeSettingsInjector.lastError ?: "未知原因")
                    }
                    .onFailure { WeLogger.e(TAG, "设置入口注入失败", it) }
                runCatching { entryThread.quitSafely() }
            }, 3_000L)

            WeLogger.i(TAG, "脚本子系统已安装（WechatApiFeature + ScriptPluginFeature + markDexBridgeReady）")

            // 自动初始化：延迟 5s 主动 ensureWarmupReady + retryAllPending（不阻塞主线程）。
            val autoInitThread = HandlerThread("WxScript-AutoInit").apply { start() }
            Handler(autoInitThread.looper).postDelayed({
                runCatching { DexInstallScheduler.ensureWarmupReady() }
                    .onFailure { WeLogger.e(TAG, "自动初始化 ensureWarmupReady 失败", it) }
                runCatching { DexInstallScheduler.retryAllPending() }
                    .onFailure { WeLogger.e(TAG, "自动初始化 retryAllPending 失败", it) }
                runCatching { autoInitThread.quitSafely() }
            }, 5_000L)

            // 就绪自检：把最终结果写进日志，避免「总开关一直提示未就绪」时无从定位。
            Thread({
                val startedAt = System.currentTimeMillis()
                var detail: String? = "自检未开始"
                while (System.currentTimeMillis() - startedAt < READINESS_TIMEOUT_MS) {
                    detail = runCatching { ScriptPluginRuntime.scriptApiReadinessDetail() }
                        .getOrElse { it.message ?: "自检异常" }
                    if (detail == null) break
                    Thread.sleep(READINESS_POLL_INTERVAL_MS)
                }
                val elapsed = System.currentTimeMillis() - startedAt
                if (detail == null) {
                    WeLogger.i(TAG, "脚本运行时就绪：微信数据库可用（耗时 ${elapsed}ms）")
                } else {
                    WeLogger.e(TAG, "脚本运行时未就绪（自检 ${elapsed}ms）：$detail")
                }
            }, "WxScript-SelfCheck").apply { isDaemon = true }.start()
        }.onFailure {
            status = "引导异常：" + (it.message ?: it.javaClass.simpleName)
            WeLogger.e(TAG, "脚本子系统安装失败", it)
        }
    }

    /**
     * 冷启动自愈看门狗：定时重排未装 Hook 任务；多轮仍未推进到 WARMUP 时强制推进。
     * 必须跑在独立 [HandlerThread]，否则在主线程做 dex 操作会卡微信 UI。
     */
    private fun scheduleReadyWatchdog() {
        val watchdogThread = HandlerThread("WxScript-Watchdog").apply { start() }
        val handler = Handler(watchdogThread.looper)
        var attempts = 0
        val task = object : Runnable {
            override fun run() {
                attempts++
                if (DexInstallScheduler.pendingCount() == 0 && DexInstallScheduler.failedCount() == 0) {
                    runCatching { watchdogThread.quitSafely() }
                    return
                }
                runCatching { DexInstallScheduler.retryAllPending() }
                    .onFailure { WeLogger.e(TAG, "看门狗重排失败", it) }
                if (attempts >= 2) {
                    runCatching { DexInstallScheduler.ensureWarmupReady() }
                        .onFailure { WeLogger.e(TAG, "看门狗强制推进失败", it) }
                }
                if (attempts < 6) handler.postDelayed(this, 8_000L)
                else runCatching { watchdogThread.quitSafely() }
            }
        }
        handler.postDelayed(task, 8_000L)
    }
}
