package com.johnny.wxscript.features.items.scripting_js

import com.johnny.wxscript.features.core.Feature
import com.johnny.wxscript.features.core.SwitchFeature

@Feature(name = "触发器：发起请求 (JS)", categories = ["脚本 (JS)"], description = "发起请求时是否执行 onRequest()")
object OnRequest : SwitchFeature()
