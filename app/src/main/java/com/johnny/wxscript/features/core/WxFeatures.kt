package com.johnny.wxscript.features.core

import com.johnny.wxscript.features.api.core.WeAuthApi
import com.johnny.wxscript.features.api.core.WeContactApi
import com.johnny.wxscript.features.api.core.WeContactLabelApi
import com.johnny.wxscript.features.api.core.WeConversationApi
import com.johnny.wxscript.features.api.core.WeDatabaseApi
import com.johnny.wxscript.features.api.core.WeDatabaseListenerApi
import com.johnny.wxscript.features.api.core.WeGroupApi
import com.johnny.wxscript.features.api.core.WeMessageApi
import com.johnny.wxscript.features.api.core.WePaymentApi
import com.johnny.wxscript.features.api.core.WeServiceApi
import com.johnny.wxscript.features.items.scripting_java.BypassScriptsDrm
import com.johnny.wxscript.features.items.scripting_java.DecompileBeanShellSnapshot
import com.johnny.wxscript.features.items.scripting_java.JavaHookApi
import com.johnny.wxscript.features.items.scripting_java.JavaScriptingHook
import com.johnny.wxscript.features.items.scripting_java.ScriptSendButtonAnchor
import com.johnny.wxscript.features.items.scripting_js.JsScriptingHook
import com.johnny.wxscript.features.items.scripting_js.OnMessage
import com.johnny.wxscript.features.items.scripting_js.OnRequest
import com.johnny.wxscript.features.items.scripting_js.OnResponse

/**
 * 功能清单（替代 WCX 的 KSP 生成版 `FeaturesProvider`）。
 *
 * WCX 用 `libs/common/annotation-scanner` 在编译期扫描 `@Feature` 并生成 `FeaturesProvider`。
 * WxScript 只需要脚本相关的这十几个 Feature，为了不引入 KSP + 代码生成链路，
 * 这里手写清单，并在构造时用反射把注解里的 name / categories / description 写回对象
 * （`SwitchFeature` 的开关持久化 key 就是 `name`，漏注入会让所有开关共用一个空 key）。
 */
object WxFeatures {

    val ALL: List<BaseFeature> by lazy {
        listOf(
            // ── 微信 API 服务（脚本运行时的底座）──
            WeDatabaseApi,
            WeDatabaseListenerApi,
            WeMessageApi,
            WeContactApi,
            WeContactLabelApi,
            WeConversationApi,
            WeGroupApi,
            WeAuthApi,
            WePaymentApi,
            WeServiceApi,
            // ── 脚本引擎 ──
            ScriptSendButtonAnchor,
            JavaHookApi,
            JavaScriptingHook,
            BypassScriptsDrm,
            DecompileBeanShellSnapshot,
            JsScriptingHook,
            OnMessage,
            OnRequest,
            OnResponse,
        ).onEach { injectMeta(it) }
    }

    private fun injectMeta(feature: BaseFeature) {
        val annotation = feature.javaClass.getAnnotation(Feature::class.java) ?: return
        feature.name = annotation.name
        feature.categories = annotation.categories.toList()
        feature.description = annotation.description
    }
}
