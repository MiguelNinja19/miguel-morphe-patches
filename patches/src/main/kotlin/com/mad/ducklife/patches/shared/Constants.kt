/*
 * Copyright 2025 Miguel's Patches
 * https://github.com/MiguelNinja19/miguel-morphe-patches
 *
 * Compatibility for Duck Life 4 (Android).
 */

package com.mad.ducklife.patches.shared

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType

val DUCK_LIFE_4 = Compatibility(
    name = "Duck Life 4",
    packageName = "com.mad.ducklife",
    // Distributed as an App Bundle (base + config.arm64_v8a / armeabi-v7a
    // splits), so the installer works with APKS.
    apkFileType = ApkFileType.APKS,
    appIconColor = 0xF9A825,
    targets = listOf(
        AppTarget(version = "2026.5.12"),
        AppTarget(version = "2026.4.30"),
        AppTarget(version = "2026.1.6"),
        AppTarget(version = "2025.3.21"),
        AppTarget(version = "2025.2.24"),
        AppTarget(version = null, isExperimental = true)
    )
)
