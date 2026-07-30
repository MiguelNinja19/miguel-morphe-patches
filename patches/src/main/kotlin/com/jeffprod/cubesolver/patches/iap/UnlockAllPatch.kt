/*
 * Unlock all designs patch for Cube Solver.
 *
 * HOW IT WORKS (master key injection via localStorage):
 *
 * Cube Solver is a WebView-based app. The game logic is in a Svelte/Vite
 * JavaScript bundle (assets/www/build/bundle.js). The Android side provides
 * a JavaScript bridge class `k93` (registered as `window.Android`).
 *
 * The JS code has a function:
 *
 *   function isPaidUser() {
 *       return window.localStorage.getItem("ulcsall") === "ok";
 *   }
 *
 * When isPaidUser() returns true, the JS code:
 *   - Unlocks ALL designs including kilominx (isKilominxUnlocked checks
 *     isPaidUser() first, then checks localStorage["ulk"])
 *   - Skips ALL rewarded ads (showRA returns immediately)
 *   - Skips ALL interstitial ads (showAdInterstitielle returns immediately)
 *   - Skips the GDPR consent popup (showRGPD returns immediately)
 *
 * The Android side has a method:
 *
 *   MainActivity.j(String key, String value)
 *
 * which calls:
 *   webView.evaluateJavascript(
 *       "window.localStorage.setItem(\"" + key + "\",\"" + value + "\");",
 *       null
 *   );
 *
 * So calling MainActivity.j("ulcsall", "ok") sets localStorage["ulcsall"] = "ok"
 * in the WebView, which makes isPaidUser() return true.
 *
 * THE PATCH:
 *
 *   We hook k93.appReady() — the @JavascriptInterface method that JS calls
 *   when the WebView finishes loading. We prepend instructions that:
 *     1. Get the MainActivity from k93's WeakReference field `a`
 *     2. Call MainActivity.j("ulcsall", "ok")
 *
 *   This sets localStorage["ulcsall"] = "ok" BEFORE any JS game logic runs,
 *   so isPaidUser() returns true from the very first frame.
 *
 *   Smali prepended to appReady():
 *
 *     p0 = this  (k93 instance)
 *     v0 = scratch (MainActivity)
 *     v1 = scratch ("ulcsall")
 *     v2 = scratch ("ok")
 *
 *     iget-object v0, p0, Lk93;->a:Ljava/lang/ref/WeakReference;
 *     invoke-virtual {v0}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
 *     move-result-object v0
 *     check-cast v0, Lcom/jeffprod/cubesolver/MainActivity;
 *     if-eqz v0, :skip
 *     const-string v1, "ulcsall"
 *     const-string v2, "ok"
 *     invoke-virtual {v0, v1, v2}, Lcom/jeffprod/cubesolver/MainActivity;->j(Ljava/lang/String;Ljava/lang/String;)V
 *     :skip
 *     # Original appReady code continues here...
 *
 * Result:
 *   - ALL designs are unlocked immediately (kilominx, mirror2x2, and any
 *     future designs that check isPaidUser())
 *   - ALL ads are also skipped (because ulcsall=ok makes isPaidUser()
 *     return true, which causes showRA/showAdInterstitielle to return early)
 *   - GDPR popup is skipped
 *   - The "Remove Ads" IAP button still works but is now redundant
 *
 * NOTE: This patch is a superset of the "Remove ads" patch — setting
 * ulcsall=ok also removes all ads. If you enable this patch, the
 * "Remove ads" patch is redundant but harmless. If you only want to
 * remove ads without unlocking everything, use only the "Remove ads"
 * patch instead.
 *
 * NOTE on PairIP:
 *   The app has PairIP (com.pairip.* package, libpairipcore.so, and
 *   Vq/aFGUz reflection routing for onCreate methods). However,
 *   VmDecryptor.decrypt() is a no-op stub (returns input bytes unchanged),
 *   so all method bodies are in plain smali. The k93 class is NOT behind
 *   PairIP VM — its methods are direct Java/smali. We can patch k93
 *   freely without needing to bypass PairIP encryption.
 *
 * Pattern reference (morphe-ai):
 *   - patcher-apis.md: addInstructions(0, ...) inserts at index 0,
 *     the original code follows the inserted block.
 *   - patch-examples.md: Pattern 5 (Callback Replacement) variant —
 *     we prepend a side-effect call rather than replacing the body.
 *
 * Pure smali, no extension DEX, no native patching.
 */

package com.jeffprod.cubesolver.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.jeffprod.cubesolver.patches.shared.CUBE_SOLVER
import com.jeffprod.cubesolver.patches.shared.AppReadyFingerprint

@Suppress("unused")
val unlockAllPatch = bytecodePatch(
    name = "Unlock all designs",
    description = "Unlocks all puzzle designs (kilominx, mirror 2x2x2, " +
        "and all other premium designs) by injecting " +
        "localStorage[\"ulcsall\"] = \"ok\" into the WebView when the " +
        "app starts. The JS-side isPaidUser() function checks this key " +
        "and, when it returns true, treats every design as unlocked. " +
        "Also skips the GDPR consent popup. Note: setting ulcsall=ok " +
        "also removes all ads as a side effect (because the JS ad " +
        "functions check isPaidUser first), so this patch is a superset " +
        "of the 'Remove ads' patch. If you only want to remove ads " +
        "without unlocking everything, use only the 'Remove ads' patch. " +
        "Requires the 'Bypass PairIP integrity check' patch.",
    default = false,
) {
    compatibleWith(CUBE_SOLVER)

    dependsOn(bypassPairIPPatch)

    execute {
        // ============================================================
        // HOOK: k93.appReady() -> prepend localStorage injection
        // ============================================================
        // Pattern: prepend side-effect call (morphe-ai Pattern 5 variant).
        //
        // We prepend instructions to appReady() that call
        // MainActivity.j("ulcsall", "ok") BEFORE the original code runs.
        // This sets localStorage["ulcsall"] = "ok" in the WebView, which
        // makes isPaidUser() return true and unlocks everything.
        //
        // Register usage:
        //   p0 = this  (k93 instance)
        //   v0 = scratch (WeakReference, then MainActivity)
        //   v1 = scratch ("ulcsall")
        //   v2 = scratch ("ok")
        //
        // The method's original .locals 5 is sufficient because we only
        // use v0, v1, v2 (all within the 5-local range), and the
        // original code will overwrite these registers as needed.
        // ============================================================
        AppReadyFingerprint.method.addInstructions(0, """
            # Get the MainActivity from k93's WeakReference field
            iget-object v0, p0, Lk93;->a:Ljava/lang/ref/WeakReference;
            invoke-virtual {v0}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
            move-result-object v0
            check-cast v0, Lcom/jeffprod/cubesolver/MainActivity;

            # If MainActivity is null (shouldn't happen, but be safe), skip
            if-eqz v0, :skip_inject

            # Call MainActivity.j("ulcsall", "ok") to set localStorage
            const-string v1, "ulcsall"
            const-string v2, "ok"
            invoke-virtual {v0, v1, v2}, Lcom/jeffprod/cubesolver/MainActivity;->j(Ljava/lang/String;Ljava/lang/String;)V

            :skip_inject
            # Original appReady() code continues here (unchanged)
        """.trimIndent())
    }
}
