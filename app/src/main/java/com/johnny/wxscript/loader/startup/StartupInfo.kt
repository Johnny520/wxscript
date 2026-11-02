package com.johnny.wxscript.loader.startup

import com.johnny.wxscript.loader.abc.IHookBridge
import com.johnny.wxscript.loader.abc.ILoaderService

object StartupInfo {

    lateinit var loaderService: ILoaderService
    var hookBridge: IHookBridge? = null
}
