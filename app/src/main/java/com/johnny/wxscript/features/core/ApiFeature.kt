package com.johnny.wxscript.features.core

import com.johnny.wxscript.utils.TargetProcesses

abstract class ApiFeature : BaseFeature() {

    override fun startup() {
        if (!TargetProcesses.isInMain) return
        enable()
    }
}
