package com.rubygames.assassin.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val HUNTER_ASSASSIN = Compatibility(
        name = "Hunter Assassin",
        packageName = "com.rubygames.assassin",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x1A1A1A,
        targets = listOf(
            AppTarget(version = "2.041"),
            AppTarget(version = null, isExperimental = true)
        )
    )
}
