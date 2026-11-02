package com.johnny.wxscript.features.items.scripting_java

import com.johnny.wxscript.dexkit.dsl.dexMethod
import com.johnny.wxscript.features.core.BaseFeature

/**
 * 聊天发送按钮的 DexKit 锚点。
 *
 * 原先借用 `ChatInputBarEnhancements.methodSendMessage`（WCX 的「聊天输入栏增强」功能），
 * 但那个功能整体依赖语音面板（VoicePanel / FunBox / EdgeTTS）一大套与脚本无关的东西。
 * WxScript 只需要这一个 hook 点，因此把锚点单独拎出来，匹配规则与原来完全一致。
 */
internal object ScriptSendButtonAnchor : BaseFeature() {

    /** 只提供锚点，没有自己的 hook 要装。 */
    override fun startup() = Unit

    val methodSendMessage by dexMethod {
        searchPackages("com.tencent.mm.pluginsdk.ui.chat")
        matcher {
            usingEqStrings("MicroMsg.ChatFooter", "send msg onClick")
        }
    }
}
