package com.Neurononfire.SupremeDuelist.patches.shared

import app.morphe.patcher.Compatibility
import app.morphe.patcher.AppTarget
import app.morphe.patcher.ApkFileType

val SUPREME_DUELIST = Compatibility(
    name = "Supreme Duelist Stickman",
    packageName = "com.Neurononfire.SupremeDuelist",
    apkFileType = ApkFileType.APKS,
    appIconColor = 0xFF5722,
    targets = listOf(
        AppTarget(version = "4.0.5"),
        AppTarget(version = "4.0.4"),
        AppTarget(version = null, isExperimental = true)
    )
)
