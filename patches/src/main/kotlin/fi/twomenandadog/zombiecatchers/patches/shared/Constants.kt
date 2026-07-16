package fi.twomenandadog.zombiecatchers.patches.shared

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType

val ZOMBIE_CATCHERS = Compatibility(
    name = "Zombie Catchers",
    packageName = "fi.twomenandadog.zombiecatchers",
    apkFileType = ApkFileType.APKS,
    appIconColor = 0x8BC34A,
    targets = listOf(
        AppTarget(version = "1.67.15"),
        AppTarget(version = null, isExperimental = true)
    )
)
