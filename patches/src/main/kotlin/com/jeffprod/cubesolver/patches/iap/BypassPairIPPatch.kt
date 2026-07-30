/*
 * Bypass PairIP patch for Cube Solver.
 *
 * ROOT CAUSE (found after deep analysis):
 *
 * The app has TWO problems caused by PairIP when the APK is patched:
 *
 *   1. CRASH on launch: Application.attachBaseContext calls
 *      SignatureCheck.verifyIntegrity which throws
 *      SignatureTamperedException because the Morphe-signed APK has a
 *      different signing certificate.
 *
 *   2. PLAY STORE REDIRECT on launch: The JS code detects the app was
 *      not installed from the Play Store (no valid installer signature)
 *      and calls Android.openPlayStore(), which creates a jl1 runnable
 *      with case=5 that opens "market://details?id=<package>".
 *
 * THE PATCH (2 parts: manifest modification + bytecode hook):
 *
 *   PART 1 (manifest): Change android:name from
 *   "com.pairip.application.Application" to "com.jeffprod.cubesolver.App"
 *   in AndroidManifest.xml.
 *
 *   This completely bypasses com.pairip.application.Application, so
 *   attachBaseContext (with SignatureCheck.verifyIntegrity and
 *   LicenseClient.checkLicense) is NEVER called. The app uses App
 *   directly, which inherits attachBaseContext from
 *   android.app.Application (the default, no PairIP checks).
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
 *   PART 2 (bytecode): No-op k93.openPlayStore() to prevent the
 *   Play Store redirect that the JS code triggers when it detects
 *   the app was sideloaded.
 *
 *   The JS code calls Android.openPlayStore() when it detects the app
 *   was not installed from the Play Store. This creates a jl1 runnable
 *   with case=5, which falls through to the DEFAULT case in jl1.run()
 *   and opens "market://details?id=<package>" or
 *   "https://play.google.com/store/apps/details?id=<package>".
 *
 *   By no-oping openPlayStore(), the redirect never happens.
 *
 * Analysis confirming this is safe:
 *   - VM bytecode (asset PAvdaIa2xHwL2BZt) does NOT contain any
 *     integrity/license check strings (verified with `strings`)
 *   - VM bytecode only contains onCreate implementation (WebView setup,
 *     loadUrl, addJavascriptInterface)
 *   - libpairipcore.so has NO anti-debug, NO native integrity checks
 *   - The VM is purely a bytecode executor, not an integrity checker
 *
 * This patch is REQUIRED for all other Cube Solver patches to work.
 */

package com.jeffprod.cubesolver.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.jeffprod.cubesolver.patches.shared.CUBE_SOLVER
import com.jeffprod.cubesolver.patches.shared.OpenPlayStoreFingerprint
import org.w3c.dom.Element

@Suppress("unused")
val bypassPairIPPatch = bytecodePatch(
    name = "Bypass PairIP integrity check",
    description = "Bypasses Google Play's PairIP by (1) changing the " +
        "application class in AndroidManifest.xml from " +
        "com.pairip.application.Application to com.jeffprod.cubesolver.App " +
        "(skips signature check + license check in attachBaseContext), " +
        "(2) removing LicenseActivity and CHECK_LICENSE permission from " +
        "the manifest, and (3) no-oping k93.openPlayStore() to prevent " +
        "the Play Store redirect that the JS code triggers when it " +
        "detects the app was sideloaded. The PairIP VM is NOT disabled " +
        "— it provides real onCreate implementations via reflection. " +
        "REQUIRED for all other Cube Solver patches.",
    default = true,
) {
    compatibleWith(CUBE_SOLVER)

    execute {
        // ============================================================
        // PART 1: Manifest modifications (resource patching)
        // ============================================================

        document("AndroidManifest.xml").use { document ->
            // HOOK 1: Change application class to skip PairIP Application
            val applicationElement =
                document.getElementsByTagName("application").item(0) as Element

            applicationElement.setAttribute(
                "android:name",
                "com.jeffprod.cubesolver.App",
            )

            // HOOK 2: Remove LicenseActivity from manifest
            val activities = document.getElementsByTagName("activity")
            for (i in activities.length - 1 downTo 0) {
                val activity = activities.item(i) as Element
                if (activity.getAttribute("android:name")
                        .contains("LicenseActivity")
                ) {
                    activity.parentNode.removeChild(activity)
                }
            }

            // HOOK 3: Remove CHECK_LICENSE permission
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


        // ============================================================
        // PART 2: Bytecode hook to prevent Play Store redirect
        // ============================================================
        // HOOK 4: k93.openPlayStore() -> return-void (no-op)
        //
        // The JS code calls Android.openPlayStore() when it detects the
        // app was not installed from the Play Store. This creates a jl1
        // runnable with case=5, which falls through to the DEFAULT case
        // in jl1.run() and opens "market://details?id=<package>".
        //
        // By no-oping openPlayStore(), the redirect never happens.
        // ============================================================
        OpenPlayStoreFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())
    }
}
