package com.johnny.wxscript.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * legacy `XposedBridge` 的等价实现。
 *
 * 只实现迁移代码实际用到的成员：log / hookMethod / hookAllMethods / hookAllConstructors /
 * invokeOriginalMethod。内部全部转成 libxposed 的 `hook(...).intercept(Hooker)`。
 */
object XposedBridge {

    @JvmStatic
    fun log(message: String) {
        XposedRuntime.log(Log.INFO, message)
    }

    @JvmStatic
    fun log(throwable: Throwable) {
        XposedRuntime.log(Log.ERROR, Log.getStackTraceString(throwable))
    }

    /**
     * 挂一个或多个 [XC_MethodHook]。
     *
     * 多个 callback 时按 legacy 语义：before 按注册顺序执行、after 逆序执行；
     * 任一 before 里 setResult 即短路，不再调用原方法。
     */
    @JvmStatic
    fun hookMethod(member: Member, vararg callbacks: XC_MethodHook): XC_MethodHook.Unhook {
        require(callbacks.isNotEmpty()) { "hookMethod 至少需要一个 callback" }
        val executable = member as? Executable
            ?: error("只支持 hook 方法/构造器: $member")
        val module = XposedRuntime.require()
        val ordered = callbacks.toList()

        val hooker = object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val param = XC_MethodHook.MethodHookParam(
                    method = chain.executable,
                    thisObject = chain.thisObject,
                    args = chain.args.toTypedArray(),
                )
                ordered.forEach { it.runBefore(param) }

                if (!param.returnEarly) {
                    try {
                        param.result = chain.proceed(param.args)
                    } catch (t: Throwable) {
                        param.throwable = t
                    }
                }

                for (index in ordered.indices.reversed()) {
                    ordered[index].runAfter(param)
                }

                param.throwable?.let { throw it }
                return param.result
            }
        }

        val handle = module.hook(executable)
            .setPriority(ordered.first().priority)
            .intercept(hooker)
        return ordered.first().Unhook(handle)
    }

    @JvmStatic
    fun hookAllMethods(clazz: Class<*>, methodName: String, callback: XC_MethodHook) {
        val matched = clazz.declaredMethods.filter { it.name == methodName }
        check(matched.isNotEmpty()) { "在 ${clazz.name} 中找不到方法 $methodName" }
        matched.forEach { hookMethod(it, callback) }
    }

    @JvmStatic
    fun hookAllConstructors(clazz: Class<*>, callback: XC_MethodHook) {
        val constructors = clazz.declaredConstructors
        check(constructors.isNotEmpty()) { "${clazz.name} 没有构造器" }
        constructors.forEach { hookMethod(it, callback) }
    }

    /**
     * 调用原方法（绕过所有 hook）。
     *
     * libxposed 的 `getInvoker(...).invokeSpecial(...)` 正好是这个语义，
     * 不需要先取消 hook 再挂回去。
     */
    @JvmStatic
    fun invokeOriginalMethod(member: Member, thisObject: Any?, args: Array<Any?>?): Any? {
        val method = member as? Method
            ?: throw IllegalStateException("invokeOriginalMethod 只支持方法: $member")
        val invoker = XposedRuntime.require().getInvoker(method)
        val actual = args ?: emptyArray()
        return invoker.invokeSpecial(thisObject, *actual)
    }

    /** 兼容个别调用点用到的构造器原样调用。 */
    @JvmStatic
    fun invokeOriginalMethod(member: Constructor<*>, thisObject: Any?, args: Array<Any?>?): Any? {
        val invoker = XposedRuntime.require().getInvoker(member)
        val actual = args ?: emptyArray()
        return invoker.newInstance(*actual)
    }
}
