package com.ivanaf.climbamiypfree.patches.shared

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType

val CLIMB = Compatibility(
    name = "Climb!",
    packageName = "com.IvanAF.ClimbAMIYPfree",
    apkFileType = ApkFileType.APKS,
    appIconColor = 0x4CAF50,
    targets = listOf(
        AppTarget(version = "6.0.6"),
        AppTarget(version = "6.0.5"),
        AppTarget(version = null, isExperimental = true)
    )
)
