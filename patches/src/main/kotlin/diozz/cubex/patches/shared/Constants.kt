/*
 * Copyright 2025 diozz-cubex-patches
 * https://github.com/diozz-cubex-patches/morphe-patches
 *
 * Patches for CubeX Solver (Cube Solver) by Pipi Chick Studio / ZipoApps.
 * Targets the PremiumHelper SDK and the AdManager (rc.a) shipped in this app.
 */

package diozz.cubex.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    /**
     * CubeX Solver (package name: diozz.cubex).
     *
     * The app is shipped by Pipi Chick Studio / ZipoApps and uses the
     * PremiumHelper SDK to gate features (Advanced Solver, custom color
     * schemes, VIP support, etc.) behind an in-app subscription
     * ("cubexsolver_premium_v1_100_trial_7d_yearly").
     *
     * The APK targeted here is the 4.1.0 / 8000101 build distributed on
     * APKMirror. The patches should also work on neighboring versions
     * because they target SDK framework code (zc.g / rc.a) which is
     * stable across releases, but they are marked experimental for
     * non-pinned versions.
     */
    val CUBEX_SOLVER = Compatibility(
        name = "CubeX Solver",
        packageName = "diozz.cubex",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x29CC29, // CubeX green accent (matches the in-app switch tint)
        targets = listOf(
            AppTarget(
                version = "4.1.0"
            ),
            AppTarget(
                // Other versions of the same major line — likely to work
                // because we target SDK framework code, but not verified.
                version = null,
                isExperimental = true
            )
        )
    )
}
