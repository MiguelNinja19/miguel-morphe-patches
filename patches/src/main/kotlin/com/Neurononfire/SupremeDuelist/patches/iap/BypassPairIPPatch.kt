package com.Neurononfire.SupremeDuelist.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.Neurononfire.SupremeDuelist.patches.shared.SUPREME_DUELIST
import com.Neurononfire.SupremeDuelist.patches.shared.LicenseCheckFingerprint
import com.Neurononfire.SupremeDuelist.patches.shared.InitializeLicenseCheckFingerprint

@Suppress("unused")
val bypassPairIpPatch = bytecodePatch(
    name = "Bypass PairIP license check",
    description = "No-ops LicenseClient.checkLicense(Context) and " +
        "LicenseClient.initializeLicenseCheck() to prevent the PairIP " +
        "license verification from running. Required for the app to " +
        "start when installed via Morphe/SAI (not from Play Store).",
    default = true,
) {
    compatibleWith(SUPREME_DUELIST)

    execute {
        LicenseCheckFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())

        InitializeLicenseCheckFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())
    }
}
