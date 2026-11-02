package com.johnny.wxscript.dexkit.cache

/**
 * 构建期生成的「resolveDex 实现哈希」表。
 *
 * WCX 里这张表由 KSP 处理器（annotation-scanner）在编译期写入，用于在功能实现发生变化时
 * 自动失效 DexKit 缓存。WxScript 不引入 KSP 代码生成，这里给出空表：
 * [DexCacheManager.calculateMethodHash] 拿到 null 会**降级**为「只按委托完整性判断缓存」，
 * 这是代码里已经处理好的分支，不会崩，只是微信版本不变时不会因为代码改动自动重扫。
 * 需要时可手工填 `类名 -> 哈希`。
 */
object GeneratedMethodHashes {
    @JvmField
    val HASHES: Map<String, String> = emptyMap()
}
