/*
 * Bypass PairIP license check patch for Supreme Duelist Stickman.
 *
 * Supreme Duelist uses a SIMPLE PairIP setup (not the full VM):
 * - NO SignatureCheck (no crash on patched APK)
 * - NO VMRunner/libpairipcore (no native VM)
 * - NO StartupLauncher (no VM bytecode)
 * - ONLY LicenseClient.checkLicense in Application.attachBaseContext
 *
 * The checkLicense method does:
 *   1. performLocalInstallerCheck() - checks if installer == "com.android.vending"
 *   2. If not, connects to Google Play Licensing Service via IPC
 *   3. If license invalid -> handleError -> startErrorDialogActivity ->
 *      LicenseActivity.showPaywallAndCloseApp -> opens Play Store + closes app
 *
 * THE PATCH (3 layers):
 *
 *   LAYER 1: No-op LicenseClient.checkLicense(Context) [STATIC]
 *     Prevents the license check from running via attachBaseContext.
 *     Safe because checkLicense does NOT throw exceptions — it just
 *     starts an async IPC call. No-op'ing it means the call never starts.
 *
 *   LAYER 2: No-op LicenseClient.initializeLicenseCheck() [INSTANCE]
 *     Prevents the license check from running via ANY other path
 *     (e.g., ContentProvider, or if someone creates a new LicenseClient
 *     instance and calls this directly). The original code checks
 *     licenseCheckState ordinal and calls connectToLicensingService or
 *     validateResponse. No-op'ing it means none of that runs.
 *
 *   LAYER 3: Manifest modifications
 *     a) Change android:name from "com.pairip.application.Application"
 *        to "android.app.Application" — this completely skips the PairIP
 *        Application class, so attachBaseContext (which calls checkLicense)
 *        is never called. Since there's no SignatureCheck and no VMRunner,
 *        the PairIP Application class does nothing useful — it only calls
 *        checkLicense. Using the default android.app.Application is safe.
 *     b) Remove LicenseActivity from manifest — even if the license check
 *        somehow runs, it can't start LicenseActivity to show the paywall
 *        or redirect to Play Store (ActivityNotFound instead).
 *     c) Remove CHECK_LICENSE permission — no longer needed since we
 *        don't contact the licensing service.
 *
 * WHY THIS WON'T CRASH (unlike Cube Solver):
 *   - No SignatureCheck that throws SignatureTamperedException
 *   - No libpairipcore.so that does JNI-based integrity checks
 *   - No VMRunner/StartupLauncher that the app depends on for onCreate
 *   - The license check is purely Java, purely async, and non-throwing
 *   - Changing the Application class is safe because the PairIP Application
 *     only adds checkLicense — it doesn't provide any needed functionality
 */

package com.Neurononfire.SupremeDuelist.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.Neurononfire.SupremeDuelist.patches.shared.SUPREME_DUELIST
import com.Neurononfire.SupremeDuelist.patches.shared.LicenseCheckFingerprint
import com.Neurononfire.SupremeDuelist.patches.shared.InitializeLicenseCheckFingerprint
import org.w3c.dom.Element

/**
 * Resource patch that modifies AndroidManifest.xml:
 * - Changes app class to android.app.Application (skip PairIP Application)
 * - Removes LicenseActivity (can't redirect to Play Store)
 * - Removes CHECK_LICENSE permission (no longer needed)
 *
 * This is a separate resourcePatch because bytecodePatch can't use document().
 */
val bypassPairIPManifestPatch = resourcePatch(
    name = "Bypass PairIP manifest",
    description = "Modifies AndroidManifest.xml to skip the PairIP " +
        "Application class, remove LicenseActivity, and remove " +
        "CHECK_LICENSE permission. Part of the PairIP bypass.",
    default = true,
) {
    compatibleWith(SUPREME_DUELIST)

    execute {
        document("AndroidManifest.xml").use { document ->
            // Change app class from PairIP Application to default Android Application
            val applicationElement =
                document.getElementsByTagName("application").item(0) as Element
            applicationElement.setAttribute(
                "android:name",
                "android.app.Application",
            )

            // Remove LicenseActivity
            val activities = document.getElementsByTagName("activity")
            for (i in activities.length - 1 downTo 0) {
                val activity = activities.item(i) as Element
                if (activity.getAttribute("android:name").contains("LicenseActivity")) {
                    activity.parentNode.removeChild(activity)
                }
            }

            // Remove CHECK_LICENSE permission
            val permissions = document.getElementsByTagName("uses-permission")
            for (i in permissions.length - 1 downTo 0) {
                val permission = permissions.item(i) as Element
                if (permission.getAttribute("android:name").contains("CHECK_LICENSE")) {
                    permission.parentNode.removeChild(permission)
                }
            }
        }
    }
}

@Suppress("unused")
val bypassPairIPPatch = bytecodePatch(
    name = "Bypass PairIP license check",
    description = "Bypasses the PairIP license check by (1) no-oping " +
        "LicenseClient.checkLicense (static, from attachBaseContext), " +
        "(2) no-oping LicenseClient.initializeLicenseCheck (instance, " +
        "from any caller), and (3) modifying AndroidManifest to skip " +
        "the PairIP Application class, remove LicenseActivity, and " +
        "remove CHECK_LICENSE permission. This app uses a simple PairIP " +
        "setup (no VM, no signature check) — only the license check.",
    default = true,
) {
    compatibleWith(SUPREME_DUELIST)

    dependsOn(bypassPairIPManifestPatch)

    execute {
        // ============================================================
        // LAYER 1: No-op LicenseClient.checkLicense(Context) [STATIC]
        // ============================================================
        LicenseCheckFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())

        // ============================================================
        // LAYER 2: No-op LicenseClient.initializeLicenseCheck() [INSTANCE]
        // ============================================================
        InitializeLicenseCheckFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())

        // ============================================================
        // LAYER 3: Manifest modifications (handled by bypassPairIPManifestPatch)
        // ============================================================
    }
}
