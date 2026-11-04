package com.johnny.wxscript.utils.reflection

import com.android.dx.stock.ProxyBuilder
import com.johnny.wxscript.utils.fs.createDirsSafe
import com.johnny.wxscript.utils.hookAfterDirectly
import dev.ujhhgtg.reflekt.reflekt
import java.lang.reflect.InvocationHandler
import kotlin.io.path.div

/**
 * dexmaker 的 ProxyBuilder 封装。
 *
 * 微信新版设置页的「设置项」是抽象类（BaseSettingItem），java.lang.reflect.Proxy 只能代理接口，
 * 所以必须用 ProxyBuilder 生成子类。生成的 class 缓存在模块代码缓存目录里，避免每次启动重新生成。
 */
fun <T : Any> createProxyBuilder(
    classLoader: ClassLoader,
    baseClass: Class<T>,
    constructorArgs: Array<Class<*>>,
    handler: InvocationHandler,
    interfaces: Array<Class<*>>,
): ProxyBuilder<T> {
    return ProxyBuilder.forClass(baseClass)
        .dexCache((KnownPaths.codeCacheDir / "generated_proxy_classes").createDirsSafe().toFile())
        .parentClassLoader(classLoader)
        .constructorArgTypes(*constructorArgs)
        .implementing(*interfaces)
        .handler(handler)
}

/**
 * 构建代理类并确保**新实例**也带上 handler。
 *
 * ProxyBuilder.buildProxyClass() 只生成 class，构造出来的对象默认没有 InvocationHandler，
 * 所以在构造器 after 回调里补一次 setInvocationHandler。
 */
fun ProxyBuilder<*>.buildClass(handler: InvocationHandler): Class<*> {
    return this.buildProxyClass()
        .also { proxyClass ->
            proxyClass.reflekt().firstConstructor().hookAfterDirectly {
                ProxyBuilder.setInvocationHandler(thisObject, handler)
            }
        }
}
