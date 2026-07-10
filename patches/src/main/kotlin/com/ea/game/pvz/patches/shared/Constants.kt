package com.ea.game.pvz.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val PVZ = Compatibility(
        name = "Plants vs. Zombies FREE",
        packageName = "com.ea.game.pvzfree_row",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x4CAF50,
        targets = listOf(
            AppTarget(version = "3.16.0"),
            AppTarget(version = null, isExperimental = true)
        )
    )
}
