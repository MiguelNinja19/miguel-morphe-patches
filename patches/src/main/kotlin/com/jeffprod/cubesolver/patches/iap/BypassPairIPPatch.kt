/*
 * Bypass PairIP patch for Cube Solver.
 *
 * WHY THIS PATCH EXISTS:
 *
 * Cube Solver uses Google Play's PairIP (Play Automatic Integrity Protection).
 * The AndroidManifest declares android:name="com.pairip.application.Application"
 * which extends com.jeffprod.cubesolver.App. The Application.attachBaseContext
 * method (called very early, before onCreate) runs three PairIP layers:
 *
 *   1. VMRunner.setContext(context)
 *      Sets the context for the PairIP VM. The VM (libpairipcore.so) executes
 *      bytecode from asset "PAvdaIa2xHwL2BZt" which provides the REAL
 *      implementations of MainActivity.onCreate, MainActivity.onDestroy, and
 *      App.onCreate via reflection (com.unity3d.ads.datastore.Vq.aFGUz).
 *      We MUST keep this — without it, the app crashes with NullPointerException.
 *
 *   2. SignatureCheck.verifyIntegrity(context)
 *      Computes SHA-256 of the APK signing certificate and compares it to the
 *      expected hash "pFVteim+91xX9uruckRiPle8UtHsH0NqfUqPPmj3wO0=". When
 *      Morphe re-signs the APK with its own key, the hash doesn't match and
 *      this method THROWS SignatureTamperedException, which is NOT caught by
 *      attachBaseContext, so the app CRASHES immediately.
 *
 *   3. LicenseClient.checkLicense(context)
 *      Contacts Google Play's licensing service via IPC to verify the app was
 *      installed from the Play Store. On a patched APK sideloaded outside the
 *      Play Store, this check fails and can cause the app to refuse to run.
 *
 * WHY WE CAN'T USE THE ORIGINAL SIGNATURE:
 *   The expected signature hash is the SHA-256 of the original developer's
 *   signing certificate. To reproduce it, we'd need the developer's private
 *   key, which we don't have. Android requires every APK to be signed with
 *   a valid key, so Morphe MUST re-sign with its own key. The signature
 *   check will ALWAYS fail on a patched APK — the only option is to skip it.
 *
 * WHY MICROG RE DOESN'T HELP WITH THE LICENSE CHECK:
 *   MicroG RE replaces Google Play Services (GmsCore) but does NOT implement
 *   Google Play's licensing service. The licensing service is a server-side
 *   check that requires a valid Play Store install. MicroG RE focuses on
 *   account authentication and Google API compatibility, not app licensing.
 *   The only way to bypass the license check is to skip it entirely.
 *
 * WHY WE CAN'T DISABLE THE VM (libpairipcore.so):
 *   The PairIP VM bytecode (asset "PAvdaIa2xHwL2BZt") contains the REAL
 *   implementations of:
 *     - MainActivity.onCreate (sets up WebView, loads file:///android_asset/www/index.html,
 *       calls addJavascriptInterface to register the k93 JS bridge as "Android")
 *     - MainActivity.onDestroy
 *     - App.onCreate
 *   These methods are routed through PairIP VM reflection (aFGUz). If we
 *   disable the VM (e.g., by no-oping StartupLauncher.launch()), the aFGUz
 *   static Method fields stay null, and onCreate tries to call null.invoke()
 *   → NullPointerException → app crash.
 *
 *   Analysis of libpairipcore.so (596KB ARM64):
 *     - NO anti-debug (no ptrace, no Frida/Xposed/Magisk detection)
 *     - NO native signature/integrity checks (only Java-level checks)
 *     - Only native function: ExecuteProgram (runs VM bytecode)
 *     - Checks "ro.arch" property (architecture, not root/debug)
 *   So the native lib is purely a VM executor, not an integrity checker.
 *
 * THE PATCH (1 hook, "nuclear option"):
 *
 *   We replace the body of com.pairip.application.Application.attachBaseContext
 *   with:
 *     1. VMRunner.setContext(context) — KEEP (VM needs context)
 *     2. super.attachBaseContext(context) — KEEP (app needs to initialize)
 *
 *   We SKIP:
 *     - SignatureCheck.verifyIntegrity(context) — prevents crash
 *     - LicenseClient.checkLicense(context) — prevents license block
 *
 *   Smali:
 *     p0 = this  (com.pairip.application.Application)
 *     p1 = context  (Context)
 *
 *     invoke-static {p1}, Lcom/pairip/VMRunner;->setContext(Landroid/content/Context;)V
 *     invoke-super {p0, p1}, Lcom/jeffprod/cubesolver/App;->attachBaseContext(Landroid/content/Context;)V
 *     return-void
 *
 *   This is better than patching SignatureCheck and LicenseClient individually
 *   because:
 *   - One hook instead of two (simpler, more reliable fingerprint match)
 *   - Even if the VM bytecode calls SignatureCheck or LicenseClient from
 *     elsewhere, they won't be called during startup (attachBaseContext is
 *     the only caller in the original code)
 *   - More robust against future PairIP updates that might add more checks
 *
 * After this patch:
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
 *   - patcher-apis.md: addInstructions(0, ...) replaces method body
 *   - patch-examples.md: Pattern 5 (Callback Replacement) — we replace
 *     the method body with a selective version that keeps some calls
 *     and skips others.
 *
 * Pure smali, no extension DEX, no native patching.
 */

package com.jeffprod.cubesolver.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.jeffprod.cubesolver.patches.shared.CUBE_SOLVER
import com.jeffprod.cubesolver.patches.shared.AttachBaseContextFingerprint

@Suppress("unused")
val bypassPairIPPatch = bytecodePatch(
    name = "Bypass PairIP integrity check",
    description = "Bypasses Google Play's PairIP (Play Automatic Integrity " +
        "Protection) by replacing Application.attachBaseContext to skip " +
        "the APK signature check (SignatureCheck.verifyIntegrity) and " +
        "the Google Play licensing check (LicenseClient.checkLicense). " +
        "Without this patch, the app crashes on launch with " +
        "SignatureTamperedException because the Morphe-signed APK has a " +
        "different signing certificate than the original. The PairIP VM " +
        "itself (libpairipcore.so) is NOT disabled — it provides the " +
        "real implementations of onCreate/onDestroy via reflection, so " +
        "disabling it would crash the app. This patch is REQUIRED for " +
        "all other Cube Solver patches to function.",
    default = true,
) {
    compatibleWith(CUBE_SOLVER)

    execute {
        // ============================================================
        // HOOK: Application.attachBaseContext -> selective bypass
        // ============================================================
        // Pattern: method body replacement (morphe-ai Pattern 5).
        //
        // We replace the entire method body with just two calls:
        //   1. VMRunner.setContext(context) — needed for the PairIP VM
        //   2. super.attachBaseContext(context) — needed for app init
        //
        // We SKIP:
        //   - SignatureCheck.verifyIntegrity(context) — crashes on patched APK
        //   - LicenseClient.checkLicense(context) — blocks sideloaded APK
        //
        // p0 = this  (com.pairip.application.Application)
        // p1 = context  (Context)
        //
        // Note: we call super on Lcom/jeffprod/cubesolver/App; (the parent
        // class), NOT on Lcom/pairip/application/Application; (the current
        // class). This is because the original code calls:
        //   invoke-super {p0, p1}, Lcom/pairip/application/Application;->attachBaseContext
        // But we ARE com.pairip.application.Application, so we need to call
        // the GRANDPARENT's attachBaseContext, which is App.attachBaseContext.
        // App doesn't override attachBaseContext, so this effectively calls
        // android.app.Application.attachBaseContext (the framework default).
        // ============================================================
        AttachBaseContextFingerprint.method.addInstructions(0, """
            # Keep: set VM context (needed for PairIP VM to work)
            invoke-static {p1}, Lcom/pairip/VMRunner;->setContext(Landroid/content/Context;)V

            # Skip: SignatureCheck.verifyIntegrity(context) — would crash
            # Skip: LicenseClient.checkLicense(context) — would block

            # Keep: call super.attachBaseContext (needed for app init)
            # The parent of com.pairip.application.Application is
            # com.jeffprod.cubesolver.App, which doesn't override
            # attachBaseContext, so this calls the framework default.
            invoke-super {p0, p1}, Lcom/jeffprod/cubesolver/App;->attachBaseContext(Landroid/content/Context;)V

            return-void
        """.trimIndent())
    }
}
