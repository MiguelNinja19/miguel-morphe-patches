package com.Neurononfire.SupremeDuelist.patches.shared

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType

val SUPREME_DUELIST = Compatibility(
    name = "Supreme Duelist Stickman",
    packageName = "com.Neurononfire.SupremeDuelist",
    apkFileType = ApkFileType.APKS,
    appIconColor = 0xE53935,
    targets = listOf(
        AppTarget(version = "4.0.5"),
        AppTarget(version = null, isExperimental = true)
    )
)
