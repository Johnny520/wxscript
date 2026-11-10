package com.johnny.wxscript.utils

import android.content.Context
import android.content.pm.PackageManager
import com.johnny.wxscript.BuildConfig
import com.johnny.wxscript.entry.Heartbeat
import java.util.zip.ZipFile

/**
 * 注入自检：把「框架为什么不加载这个模块」需要的信息一次性摆出来。
 *
 * 检查的不是猜测，而是四件硬事实：
 * 1. 装的是哪个 flavor（standard = libxposed modern API / legacy = de.robv API）；
 * 2. 框架实现的 API 版本够不够（modern 需要 ≥101，即 LSPosed 2.0+ / Vector 2.1+）；
 * 3. APK 里的入口元数据在不在、入口类能不能加载（R8 删类 / 资源没打进去都会在这里暴露）；
 * 4. 微信进程里的注入心跳有没有出现（[Heartbeat]）。
 */
object InjectSelfCheck {

    fun run(context: Context): String = buildString {
        val fw = FrameworkStatus.state.value
        val flavor = BuildConfig.FLAVOR_SLUG

        appendLine("═══ 注入自检 ═══")
        appendLine("模块版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})  flavor=$flavor")
        appendLine()

        // ── 框架 ──
        appendLine("【框架】")
        if (fw.bound) {
            appendLine("名称：${fw.frameworkName}")
            appendLine("版本：${fw.frameworkVersion}  API=${fw.apiVersion}")
            when {
                fw.apiVersion >= 101 -> appendLine("→ 支持 libxposed modern API（standard 可用）")
                fw.apiVersion > 0 -> appendLine("→ 只支持 legacy API（必须装 legacy 版！standard 永远不会被加载）")
                else -> appendLine("→ 未取到 API 版本")
            }
            appendLine("作用域：" + fw.scope.joinToString(", ").ifBlank { "（空）" } +
                if (fw.scopeActive) "  ✅ 含微信" else "  ❌ 不含微信")
        } else {
            appendLine("未绑定框架服务（XposedServiceHelper 没有回调）")
            appendLine("→ 可能是 LSPosed 1.x（无 modern service），也可能模块没被框架识别")
        }
        appendLine()

        // ── APK 元数据 ──
        appendLine("【APK 入口元数据】")
        val apk = context.applicationInfo.sourceDir
        val entries = readEntries(apk)
        entries.forEach { (name, content) ->
            appendLine("· $name：" + content.trim().ifBlank { "（空文件）" })
        }
        if (entries.isEmpty()) appendLine("· 没有任何 xposed 入口文件（打包异常）")
        appendLine()

        // ── 入口类 ──
        appendLine("【入口类可加载性】")
        val loader = InjectSelfCheck::class.java.classLoader
        val declared = entries
            .filterKeys { it.endsWith("java_init.list") || it.endsWith("xposed_init") }
            .values
            .flatMap { it.lines() }
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        if (declared.isEmpty()) {
            appendLine("（元数据里没有入口类名）")
        } else {
            declared.forEach { name ->
                val ok = runCatching { Class.forName(name, false, loader) }.isSuccess
                appendLine((if (ok) "✅ " else "❌ ") + name + if (ok) "" else "  （类不存在：被 R8 删了或名字被改）")
            }
        }
        appendLine()

        // ── 宿主 ──
        appendLine("【宿主】")
        runCatching {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(BuildConfig.HOST_PACKAGE, 0)
            appendLine("微信：${info.versionName} (${info.versionCode})")
        }.onFailure {
            appendLine("微信：未安装（${it.message}）")
        }
        appendLine("LSPosed 管理器：" + LSPOSED_PACKAGES.firstOrNull { isInstalled(context, it) }.orEmpty()
            .ifBlank { "未找到（隐藏管理器或用了别的框架）" })
        appendLine()

        // ── 注入心跳 ──
        appendLine("【微信侧注入心跳】")
        val heartbeat = Heartbeat.read()
        if (heartbeat.isNullOrBlank()) {
            appendLine("❌ 没有 module_loaded.txt —— 模块从未被装进微信进程")
            appendLine("请按顺序核对：")
            appendLine("1) LSPosed → 模块 → WxScript 开关是否打开")
            appendLine("2) 作用域是否勾选微信（分身是另一个条目）")
            appendLine("3) 框架版本与 flavor 是否匹配（上面【框架】一栏有结论）")
            appendLine("4) 改完强停微信再打开（可用下面的「快速启动」）")
        } else {
            appendLine("✅ 已注入，最近记录：")
            heartbeat.trim().lines().takeLast(12).forEach { appendLine("  " + it) }
        }
    }

    /** 读 APK 里所有 xposed 入口相关文件的原文。 */
    private fun readEntries(apkPath: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        runCatching {
            ZipFile(apkPath).use { zip ->
                listOf(
                    "META-INF/xposed/java_init.list",
                    "META-INF/xposed/module.prop",
                    "META-INF/xposed/scope.list",
                    "assets/xposed_init",
                ).forEach { name ->
                    val entry = zip.getEntry(name) ?: return@forEach
                    val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    result[name] = text
                }
            }
        }
        return result
    }

    private fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)

    private val LSPOSED_PACKAGES = listOf(
        "org.lsposed.manager",
        "com.lsposed.manager",
        "io.github.lsposed.manager",
    )
}
