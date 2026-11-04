package com.johnny.wxscript.xposed

/**
 * legacy `XposedHelpers` 的等价实现。
 *
 * 只实现迁移代码真正用到的两个方法（findClass / findAndHookMethod），
 * 其余 legacy 辅助方法在 libxposed 下没有直接对应物，需要时用反射自行实现。
 */
object XposedHelpers {

    @JvmStatic
    fun findClass(className: String, classLoader: ClassLoader?): Class<*> {
        val loader = classLoader ?: XposedRuntime.hostClassLoader
            ?: throw ClassNotFoundException("没有可用的 ClassLoader 来加载 $className")
        return Class.forName(className, false, loader)
    }

    /**
     * 找到方法并 hook。
     *
     * `parameterTypesAndCallback` 的最后一项必须是 [XC_MethodHook]，前面是参数类型（Class）。
     */
    @JvmStatic
    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        vararg parameterTypesAndCallback: Any?,
    ): XC_MethodHook.Unhook {
        val target = findClass(className, classLoader)
        return findAndHookMethod(target, methodName, *parameterTypesAndCallback)
    }

    @JvmStatic
    fun findAndHookMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypesAndCallback: Any?,
    ): XC_MethodHook.Unhook {
        val callback = parameterTypesAndCallback.lastOrNull() as? XC_MethodHook
            ?: error("findAndHookMethod($methodName) 的最后一个参数必须是 XC_MethodHook")
        val parameterTypes = parameterTypesAndCallback
            .dropLast(1)
            .map { it as? Class<*> ?: error("findAndHookMethod($methodName) 的参数类型必须是 Class，实际是 $it") }
            .toTypedArray()
        val method = clazz.getDeclaredMethod(methodName, *parameterTypes)
        return XposedBridge.hookMethod(method, callback)
    }
}
