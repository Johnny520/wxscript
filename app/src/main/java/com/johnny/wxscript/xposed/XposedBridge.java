package com.johnny.wxscript.xposed;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

/**
 * legacy {@code XposedBridge} 的等价实现。
 *
 * 只实现迁移代码实际用到的成员：log / hookMethod / hookAllMethods /
 * hookAllConstructors / invokeOriginalMethod，内部全部转成
 * libxposed 的 {@code hook(...).intercept(Hooker)}。
 */
public final class XposedBridge {

    private XposedBridge() {
    }

    public static void log(String message) {
        XposedRuntime.INSTANCE.log(Log.INFO, message);
    }

    public static void log(Throwable throwable) {
        XposedRuntime.INSTANCE.log(Log.ERROR, Log.getStackTraceString(throwable));
    }

    /**
     * 挂一个或多个 callback。
     *
     * 多个 callback 时按 legacy 语义：before 按注册顺序、after 逆序；
     * 任一 before 里 setResult 即短路，不再调用原方法。
     */
    public static XC_MethodHook.Unhook hookMethod(Member member, XC_MethodHook... callbacks) {
        if (callbacks == null || callbacks.length == 0) {
            throw new IllegalArgumentException("hookMethod 至少需要一个 callback");
        }
        if (!(member instanceof Executable)) {
            throw new IllegalArgumentException("只支持 hook 方法/构造器: " + member);
        }
        final Executable executable = (Executable) member;
        final XC_MethodHook[] ordered = callbacks.clone();

        XposedInterface.Hooker hooker = new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam(
                        chain.getExecutable(),
                        chain.getThisObject(),
                        chain.getArgs().toArray()
                );

                for (XC_MethodHook callback : ordered) {
                    callback.runBefore(param);
                }

                if (!param.isReturnEarly()) {
                    try {
                        param.result = chain.proceed(param.args);
                    } catch (Throwable error) {
                        param.throwable = error;
                    }
                }

                for (int index = ordered.length - 1; index >= 0; index--) {
                    ordered[index].runAfter(param);
                }

                if (param.throwable != null) {
                    throw param.throwable;
                }
                return param.result;
            }
        };

        XposedInterface.HookHandle handle = XposedRuntime.INSTANCE.require()
                .hook(executable)
                .setPriority(ordered[0].priority)
                .intercept(hooker);
        return ordered[0].new Unhook(handle, executable);
    }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> clazz, String methodName, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> unhooks = new LinkedHashSet<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                unhooks.add(hookMethod(method, callback));
            }
        }
        if (unhooks.isEmpty()) {
            throw new IllegalArgumentException("在 " + clazz.getName() + " 中找不到方法 " + methodName);
        }
        return unhooks;
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> clazz, XC_MethodHook callback) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        if (constructors.length == 0) {
            throw new IllegalArgumentException(clazz.getName() + " 没有构造器");
        }
        Set<XC_MethodHook.Unhook> unhooks = new LinkedHashSet<>();
        for (Constructor<?> constructor : constructors) {
            unhooks.add(hookMethod(constructor, callback));
        }
        return unhooks;
    }

    /**
     * 调用原方法（绕过所有 hook）。
     *
     * libxposed 的 {@code getInvoker(...).invokeSpecial(...)} 正好是这个语义，
     * 不需要先取消 hook 再挂回去。
     */
    public static Object invokeOriginalMethod(Member member, Object thisObject, Object[] args) throws Throwable {
        Object[] actual = args == null ? new Object[0] : args;
        if (member instanceof Method) {
            XposedInterface.Invoker<?, Method> invoker = XposedRuntime.INSTANCE.require()
                    .getInvoker((Method) member);
            return invoker.invokeSpecial(thisObject, actual);
        }
        if (member instanceof Constructor) {
            XposedInterface.CtorInvoker<?> invoker = XposedRuntime.INSTANCE.require()
                    .getInvoker((Constructor<?>) member);
            return invoker.newInstance(actual);
        }
        throw new IllegalStateException("invokeOriginalMethod 只支持方法/构造器: " + member);
    }
}
