package air.com.midjiwan.polytopia.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

val POLYTOPIA = Compatibility(
    name = "The Battle of Polytopia",
    packageName = "air.com.midjiwan.polytopia",
    apkFileType = ApkFileType.APKS,
    appIconColor = 0x3B5998,
    targets = listOf(
        AppTarget(version = "2.17.2.16299"),
        AppTarget(version = null, isExperimental = true)
    )
)
