package com.johnny.wxscript.xposed;

import java.lang.reflect.Member;
import java.util.HashMap;
import java.util.Map;

import com.johnny.wxscript.xposed.callbacks.XCallback;
import io.github.libxposed.api.XposedInterface;

/**
 * legacy {@code XC_MethodHook} 的等价实现（迁移到 libxposed 102 用）。
 *
 * 语义与 legacy 完全一致：
 * <ul>
 *     <li>{@link #beforeHookedMethod} 里 {@code param.setResult(x)} 会跳过原方法；</li>
 *     <li>{@link #afterHookedMethod} 里可以改写返回值；</li>
 *     <li>{@code param.args} 是可变数组，before 里改参数会作用到原方法调用。</li>
 * </ul>
 *
 * 用 Java 实现是为了保留 legacy 的平台类型语义（{@code thisObject} / {@code result}
 * 在 legacy 里是 {@code Object} 而不是 {@code Object?}），这样迁移过来的两百多处调用点
 * 不需要加空安全断言。
 */
public abstract class XC_MethodHook {

    public final int priority;

    public XC_MethodHook() {
        this(XCallback.PRIORITY_DEFAULT);
    }

    public XC_MethodHook(int priority) {
        this.priority = priority;
    }

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        public Object result;
        public Throwable throwable;

        private Map<String, Object> extras;
        private boolean returnEarly;

        public MethodHookParam(Member method, Object thisObject, Object[] args) {
            this.method = method;
            this.thisObject = thisObject;
            this.args = args;
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object value) {
            result = value;
            returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable value) {
            throwable = value;
            returnEarly = true;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) {
                throw throwable;
            }
            return result;
        }

        /** legacy 的跨 callback 共享数据。 */
        public synchronized void setObjectExtra(String key, Object value) {
            if (extras == null) {
                extras = new HashMap<>();
            }
            extras.put(key, value);
        }

        public synchronized Object getObjectExtra(String key) {
            return extras == null ? null : extras.get(key);
        }

        public boolean isReturnEarly() {
            return returnEarly;
        }
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    /** 包内桥接：XposedBridge 组合多个 callback 时按顺序调用。 */
    void runBefore(MethodHookParam param) throws Throwable {
        beforeHookedMethod(param);
    }

    void runAfter(MethodHookParam param) throws Throwable {
        afterHookedMethod(param);
    }

    /** 与 legacy 同名：持有 hook 句柄，调用 {@link #unhook()} 取消。 */
    public class Unhook {
        public final Member hookedMethod;
        private final XposedInterface.HookHandle handle;

        public Unhook(XposedInterface.HookHandle handle, Member hookedMethod) {
            this.handle = handle;
            this.hookedMethod = hookedMethod;
        }

        public void unhook() {
            try {
                handle.unhook();
            } catch (Throwable ignored) {
                // 取消 hook 失败不影响调用方（legacy 同样忽略）
            }
        }
    }
}
