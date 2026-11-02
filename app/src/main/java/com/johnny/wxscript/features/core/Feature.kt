package com.johnny.wxscript.features.core

/**
 * WxScript 侧的功能注解。
 *
 * WCX 的实现由 KSP 处理器（libs/common/annotation-scanner）扫描后生成 `FeaturesProvider`。
 * WxScript 只需要安装「微信 API」「脚本插件」这两三个 Feature，因此不引入 KSP 代码生成，
 * 保留同名同结构的注解，供各 Feature 类标注元信息（名称 / 分类 / 描述）。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Feature(
    val name: String,
    val categories: Array<String>,
    val description: String = "",
)
