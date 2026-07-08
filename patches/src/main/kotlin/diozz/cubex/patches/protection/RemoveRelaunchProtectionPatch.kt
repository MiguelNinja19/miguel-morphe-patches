/*
 * Copyright 2025 diozz-cubex-patches
 *
 * Simplified: only patches zc.k.h() to return false.
 * The zc.k.n() part was removed because its fingerprint (with the
 * Kotlin \u2026 ellipsis in the assertion string) was not matching
 * reliably. The premium unlock patch already prevents the relaunch
 * flow from triggering at the splash level, so this is just a
 * defensive belt-and-suspenders.
 */

package diozz.cubex.patches.protection

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import diozz.cubex.patches.shared.Constants.CUBEX_SOLVER
import diozz.cubex.patches.shared.ShouldShowRelaunchFingerprint

@Suppress("unused")
val removeRelaunchProtectionPatch = bytecodePatch(
    name = "Remove relaunch protection",
    description = "Forces zc.k.h() to return false so the relaunch / " +
        "start-like-pro decision gate is suppressed. Combined with the " +
        "Unlock premium patch, this prevents the \"Baixe este app na " +
        "Play Store\" screen from appearing.",
    default = true,
) {
    compatibleWith(CUBEX_SOLVER)

    execute {
        // zc.k.h() -> Z  always returns false
        val shouldShowMethod = ShouldShowRelaunchFingerprint.method
        shouldShowMethod.replaceInstruction(0, "const/4 v0, 0x0")
        shouldShowMethod.replaceInstruction(1, "return v0")
    }
}
