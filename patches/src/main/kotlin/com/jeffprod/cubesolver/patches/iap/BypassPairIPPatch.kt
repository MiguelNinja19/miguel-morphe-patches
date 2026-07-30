/*
 * Bypass PairIP patch for Cube Solver.
 *
 * WHY THIS PATCH EXISTS:
 *
 * Cube Solver uses Google Play's PairIP (Play Automatic Integrity Protection).
 * There are THREE protection layers, and the license check has TWO entry
 * points that must both be blocked:
 *
 *   1. SignatureCheck.verifyIntegrity(Context)
 *      Checks APK signing certificate hash. Crashes on patched APK.
 *      Called from Application.attachBaseContext.
 *
 *   2. LicenseClient.checkLicense(Context) [STATIC]
 *      Entry point #1 for license check. Called from
 *      Application.attachBaseContext. We no-op this.
 *
 *   3. LicenseClient.initializeLicenseCheck() [INSTANCE]
 *      Entry point #2 for license check. Called DIRECTLY by
 *      LicenseContentProvider.onCreate(), which runs BEFORE
 *      Application.attachBaseContext in the Android lifecycle.
 *      This bypasses our checkLicense no-op! The PairIP VM bytecode
 *      might also call this directly.
 *      When the check fails, it calls handleError ->
 *      startErrorDialogActivity -> createCloseAppIntentOrExitIfAppInBackground
 *      -> LicenseActivity, which redirects to the Play Store.
 *      THIS IS THE REDIRECT THE USER SEES.
 *
 * THE PATCH (4 hooks):
 *
 *   HOOK 1: SignatureCheck.verifyIntegrity(Context) -> return-void
 *     Skips APK signature hash check. Prevents crash.
 *
 *   HOOK 2: SignatureCheck.verifySignatureMatches(String) -> return true
 *     Belt-and-suspenders: always says "signature OK".
 *
 *   HOOK 3: LicenseClient.checkLicense(Context) -> return-void
 *     Skips license check entry point #1 (from attachBaseContext).
 *
 *   HOOK 4: LicenseClient.initializeLicenseCheck() -> return-void
 *     Skips license check entry point #2 (from ContentProvider/VM).
 *     THIS IS THE FIX for the Play Store redirect — without this hook,
 *     the license check runs via the ContentProvider path and redirects
 *     to the Play Store even though checkLicense is no-oped.
 *
 * We do NOT disable:
 *   - VMRunner.setContext (VM needs context)
 *   - StartupLauncher.launch (VM provides real onCreate via reflection)
 *   - libpairipcore.so (native VM executor, no integrity checks in it)
 *
 * This patch is REQUIRED for all other Cube Solver patches to work.
 */

package com.jeffprod.cubesolver.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.jeffprod.cubesolver.patches.shared.CUBE_SOLVER
import com.jeffprod.cubesolver.patches.shared.SignatureCheckFingerprint
import com.jeffprod.cubesolver.patches.shared.VerifySignatureMatchesFingerprint
import com.jeffprod.cubesolver.patches.shared.LicenseCheckFingerprint
import com.jeffprod.cubesolver.patches.shared.InitializeLicenseCheckFingerprint

@Suppress("unused")
val bypassPairIPPatch = bytecodePatch(
    name = "Bypass PairIP integrity check",
    description = "Bypasses Google Play's PairIP by disabling the APK " +
        "signature check and the Google Play licensing check. Without " +
        "this patch, the app crashes on launch (signature mismatch) or " +
        "redirects to the Play Store (license check failure). The " +
        "license check has TWO entry points that must both be blocked: " +
        "checkLicense (from attachBaseContext) and " +
        "initializeLicenseCheck (from ContentProvider, which runs " +
        "before attachBaseContext). REQUIRED for all other patches.",
    default = true,
) {
    compatibleWith(CUBE_SOLVER)

    execute {
        // ============================================================
        // HOOK 1: SignatureCheck.verifyIntegrity -> no-op
        // ============================================================
        SignatureCheckFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())

        // ============================================================
        // HOOK 2: SignatureCheck.verifySignatureMatches -> return true
        // ============================================================
        VerifySignatureMatchesFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            return v0
        """.trimIndent())

        // ============================================================
        // HOOK 3: LicenseClient.checkLicense -> no-op
        // ============================================================
        // This blocks license check entry point #1 (from attachBaseContext).
        // ============================================================
        LicenseCheckFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())

        // ============================================================
        // HOOK 4: LicenseClient.initializeLicenseCheck -> no-op
        // ============================================================
        // THIS IS THE FIX for the Play Store redirect!
        //
        // LicenseContentProvider.onCreate() creates a new LicenseClient
        // and calls initializeLicenseCheck() DIRECTLY, bypassing our
        // checkLicense no-op. ContentProviders run BEFORE
        // Application.attachBaseContext, so this path triggers the
        // license check before our other patches take effect.
        //
        // When the check fails, it calls handleError ->
        // startErrorDialogActivity -> LicenseActivity, which redirects
        // to the Play Store. This is exactly what the user sees.
        //
        // By no-oping initializeLicenseCheck(), we block BOTH entry
        // points: the ContentProvider path AND any VM bytecode that
        // might call it directly.
        // ============================================================
        InitializeLicenseCheckFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())
    }
}
