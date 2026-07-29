package com.jeffprod.cubesolver.patches.shared

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType

val CUBE_SOLVER = Compatibility(
    name = "Cube Solver",
    packageName = "com.jeffprod.cubesolver",
    apkFileType = ApkFileType.APKS,
    appIconColor = 0xF57C00,
    targets = listOf(
        AppTarget(version = "5.0.3"),
        AppTarget(version = null, isExperimental = true)
    )
)
