package com.johnny.wxscript.constants

import com.johnny.wxscript.preferences.WePrefs.Companion.prefOption

object Preferences {

    const val VERBOSE_LOG = "verbose_log"
    const val NO_DEX_RESOLVE = "no_dex_resolve"
    const val SHOW_STARTUP_TOAST = "toast_startup"
    const val RESET_DEX_ON_HOT_UPDATE = "reset_dex_on_hot_upd"
    const val MATCH_GENERIC_WXID_EXP = "match_generic_wxid"

    /** 微信右上角「+」菜单里的「微信脚本」入口（默认开启，可在首页 → 快捷入口关闭）。 */
    const val PLUS_MENU_ENTRY = "plus_menu_entry_enabled"

    /** 微信「我 → 设置」页里的「微信脚本」入口（主入口，默认开启）。 */
    const val SETTINGS_ENTRY = "settings_entry_enabled"

    /**
     * 微信主界面悬浮入口「脚本」（**默认关闭**）。
     *
     * 悬浮球会挡在聊天列表右侧，多数用户更希望只有「微信 → 我 → 设置 → 微信脚本」这一条原生入口；
     * 需要时可在模块 App「首页 → 快捷入口」重新打开。
     */
    const val FLOATING_ENTRY = "floating_entry_enabled"
    const val FLOATING_ENTRY_Y = "floating_entry_y_percent"

    /** 微信启动时提示「WxScript 已就绪」（排查入口问题时很有用，可关）。 */
    const val ENTRY_READY_TOAST = "entry_ready_toast"

    /** 「密友功能」分类总开关：关闭时该分类下所有功能都不加载（各功能自身开关状态保留）。 */
    const val HIDDEN_CONTACTS_MASTER_ENABLED = "hidden_contacts_master_enabled"

    // Settings UI theming
    const val THEME_MODE = "settings_theme_mode"
    const val THEME_CUSTOM_COLOR = "settings_theme_custom_color"
    const val THEME_DYNAMIC_WALLPAPER = "settings_theme_dynamic_wallpaper"
    const val THEME_PALETTE_STYLE = "settings_theme_palette_style"
    const val THEME_COLOR_SPEC = "settings_theme_color_spec"
    const val THEME_SEED_COLOR = "settings_theme_seed_color"
    const val THEME_APPLY_TO_WECHAT = "settings_theme_apply_to_wechat"

    // Cross-process cached device info (written by main app, read by WeChat process)
    const val CACHED_LSP_ENVIRONMENT = "cached_lsp_environment"
    const val CACHED_LSP_API_VERSION = "cached_lsp_api_version"

    /** 微信进程实际使用的日志目录（写日志时探测后回填，模块 App 读它来展示）。 */
    const val HOST_LOG_DIR = "host_log_dir"
    const val HOST_CRASH_DIR = "host_crash_dir"

    var verboseLog by prefOption(VERBOSE_LOG, false)
    var noDexResolve by prefOption(NO_DEX_RESOLVE, false)
    var showStartupToast by prefOption(SHOW_STARTUP_TOAST, false)
    var resetDexCacheOnHotUpdate by prefOption(RESET_DEX_ON_HOT_UPDATE, false)

    // ALWAYS check whether sender is group chat!!!
    var matchGenericWxIdExp by prefOption(MATCH_GENERIC_WXID_EXP, true)

    // 「+」菜单入口（默认开启）
    var plusMenuEntryEnabled by prefOption(PLUS_MENU_ENTRY, true)

    // 微信设置页里的「微信脚本」入口（主入口，默认开启）
    var settingsEntryEnabled by prefOption(SETTINGS_ENTRY, true)

    // 微信主界面悬浮入口（默认关闭：入口统一走微信设置页）
    var floatingEntryEnabled by prefOption(FLOATING_ENTRY, false)

    /** 悬浮按钮纵向位置（0~100 百分比），拖动后记忆。 */
    var floatingEntryYPercent by prefOption(FLOATING_ENTRY_Y, 55)

    // 微信启动提示（默认开启）
    var entryReadyToast by prefOption(ENTRY_READY_TOAST, true)

    // 「密友功能」分类总开关（默认开启）
    var hiddenContactsMasterEnabled by prefOption(HIDDEN_CONTACTS_MASTER_ENABLED, true)
}
