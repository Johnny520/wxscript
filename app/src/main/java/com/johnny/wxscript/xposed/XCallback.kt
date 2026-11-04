package com.johnny.wxscript.xposed

/**
 * 与 legacy `XCallback` 对齐的优先级常量。
 *
 * 迁移到 libxposed 后数值含义是「越大越先执行」，这里给出等价档位。
 * 调用点只用到 [PRIORITY_LOWEST]（数据库监听需要最后执行）。
 */
object XCallback {
    const val PRIORITY_HIGHEST = 100
    const val PRIORITY_DEFAULT = 50
    const val PRIORITY_LOWEST = 0
}
