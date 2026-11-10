package com.johnny.wxscript.xposed

import java.lang.reflect.Executable
import java.lang.reflect.Member

/**
 * legacy {@code de.robv.android.xposed} 后端实现（只在 legacy flavor 里编译进 APK）。
 *
 * 设计要点：把「我们的 [XC_MethodHook]」桥接成 legacy callback。
 * 这样 main 源集里两百多处 hook 调用点完全不用改，
 * 只是底层从 libxposed 换成 legacy XposedBridge。
 */
class LegacyHookBackendImpl : LegacyHookBackend {

    override fun hook(executable: Executable, callbacks: Array<XC_MethodHook>): XC_MethodHook.Unhook {
        val ordered = callbacks.clone()
        val bridge = object : de.robv.android.xposed.XC_MethodHook(ordered[0].priority) {

            override fun beforeHookedMethod(param: MethodHookParam) {
                val ours = XC_MethodHook.MethodHookParam(
                    param.method,
                    param.thisObject,
                    param.args ?: emptyArray(),
                )
                param.setObjectExtra(EXTRA_PARAM, ours)
                for (callback in ordered) {
                    callback.runBefore(ours)
                }
                // before 里可能整体替换 args 数组，必须回写
                param.args = ours.args
                if (ours.isReturnEarly) {
                    if (ours.throwable != null) {
                        param.setThrowable(ours.throwable)
                    } else {
                        param.setResult(ours.result)
                    }
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val ours = param.getObjectExtra(EXTRA_PARAM) as? XC_MethodHook.MethodHookParam ?: return
                ours.result = param.result
                ours.throwable = param.throwable
                for (index in ordered.indices.reversed()) {
                    runCatching { ordered[index].runAfter(ours) }
                        .onFailure { XposedRuntime.log(6, "[legacy] after callback failed: ${it.message}") }
                }
                if (ours.throwable != null) {
                    param.setThrowable(ours.throwable)
                } else {
                    // after 里改返回值：只有显式 setResult 过才覆盖，否则保留原结果
                    if (ours.isReturnEarly) param.setResult(ours.result)
                }
            }
        }

        de.robv.android.xposed.XposedBridge.hookMethod(executable, bridge)
        return XC_MethodHook.Unhook(
            { de.robv.android.xposed.XposedBridge.unhookMethod(executable, bridge) },
            executable,
        )
    }

    override fun invokeOriginal(member: Member, thisObject: Any?, args: Array<Any?>?): Any? =
        de.robv.android.xposed.XposedBridge.invokeOriginalMethod(
            member,
            thisObject,
            args ?: emptyArray(),
        )

    private companion object {
        const val EXTRA_PARAM = "WxScript.MethodHookParam"
    }
}
