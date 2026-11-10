# ══════════════════════════════════════════════════════════════════
# WxScript R8 规则
#
# 核心原则：脚本（BeanShell 前置代码 + 用户脚本）是按【原始类名】在运行时
# 解析类的，R8 静态分析看不到这些字符串引用。凡是脚本可见的类都必须 keep，
# 否则 release 包会出现 `Class: Xxx not found in namespace`。
# ══════════════════════════════════════════════════════════════════

# ─── Xposed 入口 ───────────────────────────────────────────────────
-keep class com.johnny.wxscript.entry.** { *; }
-keep class com.johnny.wxscript.startup.** { *; }
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# ─── 脚本 ABI：脚本直接按名字访问的包 ──────────────────────────────
-keep class bsh.** { *; }
-keep class me.hd.wauxv.** { *; }
-keep class me.hd.wauxv.plugin.api.callback.** { *; }
-keep class me.hd.wauxv.data.bean.** { *; }
-keep class me.hd.wauxv.data.bean.info.** { *; }
-keep class me.yun.silk.** { *; }
-keep class h.Hchat.hooks.items.script.ScriptPluginAbi { *; }
-keep class h.Hchat.hooks.items.script.ScriptPluginRuntime { *; }
-keep class h.Hchat.hooks.items.script.ScriptWaBridge { *; }

# 脚本里通过反射读微信内部字段名（单字母），必须保住成员名
-keepclassmembers class * {
    java.lang.String e;
    java.lang.String f;
    java.lang.String h;
    java.lang.String i;
    java.lang.String m;
    java.lang.String d;
}

# ─── DexKit ────────────────────────────────────────────────────────
-keep class org.luckypray.dexkit.DexKitBridge { *; }
-dontwarn org.luckypray.dexkit.**

# ─── XML / JSON / 反射辅助 ─────────────────────────────────────────
-keep class org.xmlpull.v1.** { *; }
-keep class androidx.lifecycle.viewmodel.compose.** { *; }
-keep class org.mozilla.javascript.** { *; }

# ─── Kotlin ────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keep class kotlin.coroutines.Continuation { *; }
-dontwarn kotlinx.coroutines.**
-dontwarn kotlin.metadata.**
-dontwarn org.mozilla.kotlin.**

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ─── 依赖噪音 ──────────────────────────────────────────────────────
-dontwarn com.google.protobuf.**
-dontwarn com.tencent.wcdb.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn top.yukonga.**
-dontwarn com.alibaba.fastjson2.**
-dontwarn javax.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn com.highcapable.kavaref.**
-dontwarn java.lang.invoke.**

# ── libxposed 102 ──
-dontwarn io.github.libxposed.annotation.**
-dontwarn io.github.libxposed.api.**
-dontwarn io.github.libxposed.service.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
# 兼容层类会被运行时按名/按签名引用（脚本 ABI 与反射）
-keep class com.johnny.wxscript.xposed.** { *; }
