package com.johnny.wxscript.xposed

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Member

/**
 * legacy `XC_MethodHook` 的等价实现（迁移到 libxposed 102 用）。
 *
 * 语义与 legacy 完全一致：
 * - [beforeHookedMethod] 里 `param.setResult(x)` 会跳过原方法；
 * - [afterHookedMethod] 里可以改写返回值；
 * - `param.args` 是可变数组，before 里改参数会作用到原方法调用。
 *
 * 之所以自己实现一层而不是直接用 `XposedInterface.Hooker`，是因为迁移过来的
 * 脚本/微信 API 代码里有两百多处 `XC_MethodHook` 用法，保留同名同语义可以把
 * 改动收敛到「import 换一行」。
 */
abstract class XC_MethodHook @JvmOverloads constructor(
    val priority: Int = XCallback.PRIORITY_DEFAULT,
) {

    open class MethodHookParam internal constructor(
        @JvmField val method: Member,
        @JvmField val thisObject: Any?,
        @JvmField val args: Array<Any?>,
    ) {
        @JvmField
        var result: Any? = null

        @JvmField
        var throwable: Throwable? = null

        /** before 回调里调用 setResult / setThrowable 后置为 true，表示不再调用原方法。 */
        @JvmField
        var returnEarly: Boolean = false

        fun getResult(): Any? = result

        fun setResult(value: Any?) {
            result = value
            returnEarly = true
        }

        fun getThrowable(): Throwable? = throwable

        fun setThrowable(value: Throwable?) {
            throwable = value
            returnEarly = true
        }

        fun hasThrowable(): Boolean = throwable != null

        fun getResultOrThrowable(): Any? {
            throwable?.let { throw it }
            return result
        }
    }

    protected open fun beforeHookedMethod(param: MethodHookParam) {}

    protected open fun afterHookedMethod(param: MethodHookParam) {}

    internal fun runBefore(param: MethodHookParam) = beforeHookedMethod(param)

    internal fun runAfter(param: MethodHookParam) = afterHookedMethod(param)

    /** 与 legacy 同名：持有 hook 句柄，调用 [unhook] 取消。 */
    inner class Unhook internal constructor(private val handle: XposedInterface.HookHandle) {
        fun unhook() {
            runCatching { handle.unhook() }
        }
    }
}
