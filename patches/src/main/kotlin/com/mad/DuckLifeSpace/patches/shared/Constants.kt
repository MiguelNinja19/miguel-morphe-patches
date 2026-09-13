/*
 * Copyright 2025 Miguel's Patches
 * https://github.com/MiguelNinja19/miguel-morphe-patches
 *
 * Compatibility for Duck Life 6: Space (Android).
 */

package com.mad.DuckLifeSpace.patches.shared

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType

val DUCK_LIFE_SPACE = Compatibility(
    name = "Duck Life 6: Space",
    packageName = "com.mad.DuckLifeSpace",
    // Distributed as an App Bundle (base + config.arm64_v8a splits),
    // so the installer works with APKS.
    apkFileType = ApkFileType.APKS,
    appIconColor = 0x283593,
    targets = listOf(
        AppTarget(version = "2026.729.2"),
        AppTarget(version = "2025.11.30"),
        AppTarget(version = "2024.5.27"),
        AppTarget(version = null, isExperimental = true)
    )
)
