/*
 * Bypass PairIP patch for Cube Solver.
 *
 * WHY THIS PATCH EXISTS:
 *
 * Cube Solver uses Google Play's PairIP (Play Automatic Integrity Protection)
 * with THREE active protection layers:
 *
 *   1. SignatureCheck.verifyIntegrity(Context)
 *      Checks that the APK signing certificate matches the expected hash
 *      ("pFVteim+91xX9uruckRiPle8UtHsH0NqfUqPPmj3wO0="). When Morphe
 *      re-signs the APK with its own key, this check fails and throws
 *      SignatureTamperedException, crashing the app.
 *
 *   2. LicenseClient.checkLicense(Context)
 *      Contacts Google Play's licensing service to verify the app was
 *      installed from the Play Store. On a patched APK sideloaded outside
 *      the Play Store, this check will fail, which can cause the app to
 *      refuse to run or display a license error.
 *
 *   3. VMRunner / libpairipcore.so
 *      The native VM executes bytecode from asset "PAvdaIa2xHwL2BZt".
 *      This VM provides the real implementations of MainActivity.onCreate,
 *      MainActivity.onDestroy, and App.onCreate via reflection
 *      (com.unity3d.ads.datastore.Vq.aFGUz). We MUST keep the VM running
 *      because the app's core lifecycle methods depend on it.
 *
 * The AndroidManifest declares android:name="com.pairip.application.Application"
 * which extends com.jeffprod.cubesolver.App. The Application.attachBaseContext
 * calls:
 *   VMRunner.setContext(context)
 *   SignatureCheck.verifyIntegrity(context)   <- crashes on patched APK
 *   LicenseClient.checkLicense(context)       <- blocks on patched APK
 *   super.attachBaseContext(context)
 *
 * THE PATCH (2 hooks):
 *
 *   HOOK 1: SignatureCheck.verifyIntegrity(Context) -> return-void (no-op)
 *     Skips the APK signature check entirely. The Morphe-signed APK can
 *     now run without throwing SignatureTamperedException.
 *
 *   HOOK 2: LicenseClient.checkLicense(Context) -> return-void (no-op)
 *     Skips the Google Play licensing check entirely. The patched APK
 *     can now run without contacting Google Play's licensing service.
 *
 * We do NOT disable StartupLauncher.launch() or VMRunner because the VM
 * provides the real implementations of onCreate/onDestroy via reflection.
 * Disabling the VM would cause NullPointerExceptions in the lifecycle
 * methods and crash the app.
 *
 * After this patch, the app runs normally:
 *   - The VM still starts and provides reflected method implementations
 *   - No signature check crash
 *   - No license check block
 *   - The k93 JS bridge works normally (it's NOT behind PairIP VM)
 *   - The RemoveAds and UnlockAll patches can function
 *
 * This patch is REQUIRED for any other Cube Solver patch to work. It
 * should be default ON and all other patches should depend on it.
 *
 * Pattern reference (morphe-ai):
 *   - patcher-apis.md: addInstructions(0, "return-void") = the simplest
 *     returnEarly() pattern (Pattern 1).
 *
 * Pure smali, no extension DEX, no native patching.
 */

package com.jeffprod.cubesolver.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.jeffprod.cubesolver.patches.shared.CUBE_SOLVER
import com.jeffprod.cubesolver.patches.shared.SignatureCheckFingerprint
import com.jeffprod.cubesolver.patches.shared.LicenseCheckFingerprint

@Suppress("unused")
val bypassPairIPPatch = bytecodePatch(
    name = "Bypass PairIP integrity check",
    description = "Bypasses Google Play's PairIP (Play Automatic Integrity " +
        "Protection) by disabling the APK signature check and the Google " +
        "Play licensing check. Without this patch, the app crashes on " +
        "launch with SignatureTamperedException because the Morphe-signed " +
        "APK has a different signing certificate than the original. The " +
        "PairIP VM itself (libpairipcore.so) is NOT disabled — it provides " +
        "the real implementations of onCreate/onDestroy via reflection, " +
        "so disabling it would crash the app. This patch is REQUIRED for " +
        "all other Cube Solver patches to function.",
    default = true,
) {
    compatibleWith(CUBE_SOLVER)

    execute {
        // ============================================================
        // HOOK 1: SignatureCheck.verifyIntegrity -> no-op
        // ============================================================
        // Pattern: returnEarly (morphe-ai Pattern 1).
        //
        // We replace the method body with a single return-void.
        // The original code that checks the APK signature hash and
        // throws SignatureTamperedException on mismatch becomes dead code.
        //
        // This allows the Morphe-signed APK to run without crashing.
        // ============================================================
        SignatureCheckFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())


        // ============================================================
        // HOOK 2: LicenseClient.checkLicense -> no-op
        // ============================================================
        // Pattern: returnEarly (morphe-ai Pattern 1).
        //
        // We replace the method body with a single return-void.
        // The original code that contacts Google Play's licensing service
        // and blocks the app if the license is invalid becomes dead code.
        //
        // This allows the patched APK to run without a valid Play Store
        // license.
        // ============================================================
        LicenseCheckFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())
    }
}
