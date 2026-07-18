package com.mobge.oddmar.patches.shared

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType

val ODDMAR = Compatibility(
    name = "Oddmar",
    packageName = "com.mobge.Oddmar",
    apkFileType = ApkFileType.APK,
    appIconColor = 0xFF9800,
    targets = listOf(
        AppTarget(version = "0.111"),
        AppTarget(version = null, isExperimental = true)
    )
)
