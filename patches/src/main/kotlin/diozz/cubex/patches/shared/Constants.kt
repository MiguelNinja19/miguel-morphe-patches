/*
 * Copyright 2025 Miguel's Patches
 * https://github.com/MiguelNinja19/miguel-morphe-patches
 *
 * Patches for CubeX Solver (Cube Solver) by Pipi Chick Studio / ZipoApps.
 * Targets the PremiumHelper SDK and the AdManager (rc.a) shipped in this app.
 */

package diozz.cubex.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val CUBEX_SOLVER = Compatibility(
        name = "CubeX Solver",
        packageName = "diozz.cubex",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x29CC29,
        targets = listOf(
            AppTarget(
                version = "4.1.1"
            ),
            AppTarget(
                version = "4.1.0"
            ),
            AppTarget(
                version = null,
                isExperimental = true
            )
        )
    )
}
