/*
 * Copyright 2025 Miguel's Patches
 * https://github.com/MiguelNinja19/miguel-morphe-patches
 *
 * Compatibility for Lara Croft and the Guardian of Light (Android).
 */

package com.feralinteractive.laracroftgol.patches.shared

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType

val LARA_CROFT_GOL = Compatibility(
    name = "Lara Croft: Guardian of Light",
    packageName = "com.feralinteractive.laracroftgol_android",
    // Game is distributed as an App Bundle (base + config.arm64_v8a +
    // config.mdpi + data_core splits), so the installer works with APKS.
    apkFileType = ApkFileType.APKS,
    appIconColor = 0x8B0000,
    targets = listOf(
        AppTarget(version = "1.2.6RC1"),
        AppTarget(version = "1.2.7RC2"),
        AppTarget(version = null, isExperimental = true)
    )
)
