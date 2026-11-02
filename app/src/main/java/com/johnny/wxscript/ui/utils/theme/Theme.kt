package com.johnny.wxscript.ui.utils.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * 默认配色。
 *
 * WCX 里这两个 scheme 由一套 400+ 行的 Material3 调色板常量拼出来；WxScript 的模块 UI 自己
 * 用 material3 默认配色即可（自定义取色由 [ThemeSettings] + [SeedResolver] 覆盖），
 * 因此这里只保留默认色板，避免为了几个常量拖进整份调色板文件。
 */
val lightScheme = lightColorScheme()
val darkScheme = darkColorScheme()
