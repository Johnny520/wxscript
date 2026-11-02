@file:Suppress("NOTHING_TO_INLINE")

package dev.ujhhgtg.reflekt.utils

import java.lang.reflect.AccessibleObject

/**
 * Android 适配版：`AccessibleObject.trySetAccessible()` 是 JDK 9+ 的 API，Android 平台没有，
 * 这里统一退回到 `isAccessible = true`（被隐藏 API 拦截时静默失败，调用方自行兜底）。
 */
fun <T : AccessibleObject> T.makeAccessible(): T {
    runCatching {
        @Suppress("DEPRECATION")
        if (!this.isAccessible) this.isAccessible = true
    }
    return this
}
