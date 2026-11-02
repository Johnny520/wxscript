package com.johnny.wxscript.features.items.scripting_java

import androidx.activity.ComponentActivity
import com.johnny.wxscript.activity.TransparentActivity
import com.johnny.wxscript.features.core.ClickableFeature
import com.johnny.wxscript.features.core.Feature
import com.johnny.wxscript.utils.registerBshSnapshotDecompileLaunchers

@Feature(name = "反编译 BeanShell 快照", categories = ["脚本 (Java)"], description = "不知道这是干啥的就别管了")
object DecompileBeanShellSnapshot : ClickableFeature() {

    override val noSwitchWidget = true

    override fun onClick(context: ComponentActivity) {
        TransparentActivity.launch(context) {
            val selectFileLauncher = registerBshSnapshotDecompileLaunchers { finish() }
            selectFileLauncher.launch("*/*")
        }
    }
}
