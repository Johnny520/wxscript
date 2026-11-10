package com.johnny.wxscript.xposed;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;

/**
 * legacy（{@code de.robv.android.xposed}）后端抽象。
 *
 * main 源集只依赖这个接口，具体实现放在 legacy flavor 的 {@code src/legacy/java} 下，
 * 因此 standard flavor 的 APK 里**不存在任何对 {@code de.robv} 类的引用**——
 * 这很重要：libxposed API 102 的框架会在 classloader 层封禁 legacy 包。
 */
public interface LegacyHookBackend {

    /** 把 [callbacks] 组合成一个 legacy callback 挂到 [executable] 上。 */
    XC_MethodHook.Unhook hook(Executable executable, XC_MethodHook[] callbacks);

    /** 调用原方法（绕过所有 hook）。legacy 里即 unhook 后再 hook 回去的实现细节由后端负责。 */
    Object invokeOriginal(Member member, Object thisObject, Object[] args) throws Throwable;
}
