package com.johnny.wxscript.features.api.ui

import android.content.Context
import android.content.DialogInterface
import com.johnny.wxscript.dexkit.abc.IResolveDex
import com.johnny.wxscript.dexkit.dsl.dexClass
import com.johnny.wxscript.features.core.ApiFeature
import com.johnny.wxscript.features.core.Feature
import com.johnny.wxscript.utils.reflection.BString

@Feature(name = "对话框 API", categories = ["API"], description = "提供显示微信自带对话框的能力")
object WeAlertDialogApi : ApiFeature(), IResolveDex {

    private val classMmAlert by dexClass {
        matcher {
            usingEqStrings("MicroMsg.MMAlert")
        }
    }

    /**
     * NEVER use this API except for the TrollBan feature.
     */
    fun showAlertDialog(
        context: Context,
        content: String,
        title: String? = null,
        onClickOk: (DialogInterface) -> Unit = {},
        onClickCancel: (DialogInterface) -> Unit = {},
        okText: String = "确定",
        cancelText: String = "取消"
    ) {
        classMmAlert.reflekt()
            .firstMethod {
                parameters(Context::class, BString, BString, BString, BString, DialogInterface.OnClickListener::class, DialogInterface.OnClickListener::class)
            }.invokeStatic(
                context, content, title ?: "", okText, cancelText,
                DialogInterface.OnClickListener { di, _ -> onClickOk(di) },
                DialogInterface.OnClickListener { di, _ -> onClickCancel(di) })
    }
}
