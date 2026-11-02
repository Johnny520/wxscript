package com.johnny.wxscript.utils.strings

val String.isGroupChatWxId
    get() =
        this.endsWith("@chatroom") || this.endsWith("@im.chatroom")
