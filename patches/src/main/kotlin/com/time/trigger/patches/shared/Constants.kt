package com.time.trigger.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val JOHNNY_TRIGGER = Compatibility(
        name = "Johnny Trigger",
        packageName = "com.time.trigger",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xFF5722,
        targets = listOf(
            AppTarget(version = "1.12.65"),
            AppTarget(version = null, isExperimental = true)
        )
    )
}
