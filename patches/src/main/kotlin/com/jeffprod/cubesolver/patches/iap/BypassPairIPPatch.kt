/*
 * Bypass PairIP patch for Cube Solver.
 *
 * ROOT CAUSE (found after deep analysis):
 *
 * The AndroidManifest declares:
 *   android:name="com.pairip.application.Application"
 *
 * This PairIP Application class extends com.jeffprod.cubesolver.App and
 * overrides attachBaseContext to run THREE PairIP protection layers:
 *
 *   1. VMRunner.setContext(context) — needed for PairIP VM
 *   2. SignatureCheck.verifyIntegrity(context) — CRASHES on patched APK
 *   3. LicenseClient.checkLicense(context) — redirects to Play Store
 *
 * Previous attempts tried to no-op the individual Java methods
 * (verifyIntegrity, checkLicense, initializeLicenseCheck). This didn't
 * work because:
 * - LicenseContentProvider.onCreate() calls initializeLicenseCheck()
 *   DIRECTLY, bypassing the static checkLicense method
 * - ContentProviders run BEFORE Application.attachBaseContext
 * - The PairIP VM might also call these methods internally
 *
 * THE FIX (manifest modification):
 *
 *   Change android:name from "com.pairip.application.Application" to
 *   "com.jeffprod.cubesolver.App" in the AndroidManifest.xml.
 *
 *   This completely bypasses com.pairip.application.Application, so
 *   attachBaseContext (with all its checks) is NEVER called. The app
 *   uses App directly, which inherits attachBaseContext from
 *   android.app.Application (the default, no checks).
 *
 *   App.<clinit> still calls StartupLauncher.launch() which starts the
 *   PairIP VM. The VM provides the real onCreate/onDestroy implementations
 *   via reflection (aFGUz). So the app functions normally.
 *
 *   Also remove LicenseActivity from the manifest so even if the license
 *   check somehow runs, it can't redirect to the Play Store.
 *
 *   Also remove the CHECK_LICENSE permission since it's no longer needed.
 *
 * Analysis confirming this is safe:
 *   - VM bytecode (asset PAvdaIa2xHwL2BZt) does NOT contain any
 *     integrity/license check strings (verified with `strings`)
 *   - VM bytecode only contains onCreate implementation (WebView setup,
 *     loadUrl, addJavascriptInterface)
 *   - libpairipcore.so has NO anti-debug, NO native integrity checks
 *   - The VM is purely a bytecode executor, not an integrity checker
 *
 * This approach is inspired by PKiller (github.com/Anon4You/PKiller)
 * which removes PairIP code entirely. We take a softer approach: keep
 * the VM running (needed for onCreate) but bypass the Application
 * wrapper that triggers the checks.
 *
 * This is a RESOURCE patch (modifies AndroidManifest.xml), not a
 * bytecode patch. No smali modification needed for the bypass itself.
 */

package com.jeffprod.cubesolver.patches.iap

import app.morphe.patcher.patch.resourcePatch
import com.jeffprod.cubesolver.patches.shared.CUBE_SOLVER
import org.w3c.dom.Element

@Suppress("unused")
val bypassPairIPPatch = resourcePatch(
    name = "Bypass PairIP integrity check",
    description = "Bypasses Google Play's PairIP by changing the " +
        "application class in AndroidManifest.xml from " +
        "com.pairip.application.Application to com.jeffprod.cubesolver.App. " +
        "This completely skips the PairIP attachBaseContext which runs " +
        "the APK signature check (crashes on patched APK) and the Google " +
        "Play licensing check (redirects to Play Store). Also removes " +
        "LicenseActivity and CHECK_LICENSE permission from the manifest. " +
        "The PairIP VM is NOT disabled — it provides real onCreate " +
        "implementations via reflection. REQUIRED for all other patches.",
    default = true,
) {
    compatibleWith(CUBE_SOLVER)

    execute {
        // ============================================================
        // HOOK 1: Change application class to skip PairIP Application
        // ============================================================
        // Change android:name from "com.pairip.application.Application"
        // to "com.jeffprod.cubesolver.App" in the <application> tag.
        //
        // This is the KEY fix. com.pairip.application.Application is the
        // class that calls SignatureCheck.verifyIntegrity and
        // LicenseClient.checkLicense in attachBaseContext. By using App
        // directly, attachBaseContext is the default Android one (no
        // checks). The PairIP VM still starts via App.<clinit> ->
        // StartupLauncher.launch(), so onCreate works via reflection.
        // ============================================================
        document("AndroidManifest.xml").use { document ->
            val applicationElement =
                document.getElementsByTagName("application").item(0) as Element

            applicationElement.setAttribute(
                "android:name",
                "com.jeffprod.cubesolver.App",
            )


            // ============================================================
            // HOOK 2: Remove LicenseActivity from manifest
            // ============================================================
            // LicenseActivity is the activity that redirects to the Play
            // Store when the license check fails. By removing it from the
            // manifest, even if the license check somehow runs, it can't
            // start the redirect activity.
            // ============================================================
            val activities = document.getElementsByTagName("activity")
            for (i in activities.length - 1 downTo 0) {
                val activity = activities.item(i) as Element
                if (activity.getAttribute("android:name")
                        .contains("LicenseActivity")
                ) {
                    activity.parentNode.removeChild(activity)
                }
            }


            // ============================================================
            // HOOK 3: Remove CHECK_LICENSE permission
            // ============================================================
            // The CHECK_LICENSE permission is used by the Play Store
            // licensing service. Since we're skipping the license check
            // entirely, this permission is no longer needed.
            // ============================================================
            val permissions = document.getElementsByTagName("uses-permission")
            for (i in permissions.length - 1 downTo 0) {
                val permission = permissions.item(i) as Element
                if (permission.getAttribute("android:name")
                        .contains("CHECK_LICENSE")
                ) {
                    permission.parentNode.removeChild(permission)
                }
            }
        }
    }
}
