/*
 * Bypass PairIP license check for Duck Life 4 (com.mad.ducklife).
 *
 * RESEARCH SUMMARY (v2026.5.12 / versionCode 300084, XAPK analyzed
 * from APKPure):
 *
 * - Duck Life 4 DOES ship PairIP - unlike Duck Life 6: Space. The
 *   manifest declares:
 *     com.pairip.licensecheck.LicenseActivity (exported=false)
 *     com.pairip.licensecheck.LicenseContentProvider (exported=false)
 *   plus the com.android.vending.CHECK_LICENSE permission.
 *
 * - BUT there is no native libpairipcore.so VM anywhere (the arm64
 *   split only contains libunity.so, libil2cpp.so, libmain.so and
 *   lib_burst_generated.so; zero "pairip" strings in any .so and in
 *   global-metadata.dat). All licensing lives in the 30 classes of
 *   classes2.dex, so Java-level no-ops are sufficient - no manifest
 *   surgery and no VM bypass needed (unlike Cube Solver).
 *
 * - Entry point: LicenseContentProvider.onCreate() runs before the
 *   Application class and calls
 *   new LicenseClient(getContext()).initializeLicenseCheck().
 *   The public static LicenseClient.checkLicense(Context) does the
 *   same. initializeLicenseCheck() drives the whole state machine:
 *   connectToLicensingService() -> processResponse() ->
 *   LicenseResponseHelper.validateResponse() -> on failure
 *   startPaywallActivity()/scheduleAppShutdown() (LicenseActivity
 *   blocks the game), including the "repeated check" scheduler.
 *
 * - No other code path references PairIP: the game has no own Java
 *   classes (Unity IL2CPP, C# via bitter.jnibridge.JNIBridge), the
 *   C# side never mentions pairip (verified in the string literals
 *   and identifiers of global-metadata.dat).
 *
 * This mirrors the proven Supreme Duelist bypass against the same
 * PairIP build: no-op both entry points so the licensing service is
 * never contacted. Required for the app to start when installed via
 * Morphe/SAI (not from the Play Store).
 */

package com.mad.ducklife.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.mad.ducklife.patches.shared.DUCK_LIFE_4
import com.mad.ducklife.patches.shared.LicenseCheckFingerprint
import com.mad.ducklife.patches.shared.InitializeLicenseCheckFingerprint
import java.util.logging.Logger

@Suppress("unused")
val bypassPairIpPatch = bytecodePatch(
    name = "Bypass PairIP license check",
    description = "No-ops LicenseClient.checkLicense(Context) and " +
        "LicenseClient.initializeLicenseCheck() so the PairIP license " +
        "verification never runs. Duck Life 4 ships the PairIP V2 client " +
        "(Java only, no libpairipcore.so VM): the LicenseContentProvider " +
        "starts the check before the Application class and shows a " +
        "paywall / closes the app when it fails. Required for the app " +
        "to start when installed via Morphe/SAI (not from the Play Store).",
    default = true,
) {
    compatibleWith(DUCK_LIFE_4)

    execute {
        val logger = Logger.getLogger("DuckLife4")

        // LicenseClient.checkLicense(Context) - 2 registers, no locals
        // needed: a single return-void is a valid body.
        LicenseCheckFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())

        // LicenseClient.initializeLicenseCheck() - 3 registers, no locals
        // needed: a single return-void is a valid body.
        InitializeLicenseCheckFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())

        logger.info("Bypass PairIP: no-op'd LicenseClient.checkLicense(Context) and LicenseClient.initializeLicenseCheck()")
    }
}
