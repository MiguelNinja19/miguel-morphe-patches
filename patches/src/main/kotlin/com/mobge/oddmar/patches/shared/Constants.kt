package com.mobge.Oddmar.patches.shared

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType

val ODDMAR = Compatibility(
    name = "Oddmar",
    packageName = "com.mobge.Oddmar",
    apkFileType = ApkFileType.APKS,
    appIconColor = 0xC2185B,
    targets = listOf(
        AppTarget(version = "0.111"),
        AppTarget(version = null, isExperimental = true)
    )
)
