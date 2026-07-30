/*
 * Remove ads patch for Cube Solver.
 *
 * HOW IT WORKS (reward granting without ad display):
 *
 * Cube Solver is a WebView-based app. The JS code calls Android.showRA(key)
 * when the user taps "Watch ad to unlock" on a locked design (e.g.,
 * kilominx). The key is the localStorage key for that design (e.g., "ulk").
 *
 * The original flow:
 *   1. JS calls Android.showRA("ulk")
 *   2. Java (k93.showRA) logs to Firebase, then posts a jl1 runnable
 *   3. jl1 shows a rewarded ad via AdMob/AppLovin
 *   4. After the ad is watched, y7 callback calls MainActivity.j("ulk", "ok")
 *   5. j() executes: webView.evaluateJavascript("window.localStorage.setItem('ulk','ok')", null)
 *   6. The design is now unlocked in the JS side
 *
 * For interstitial ads:
 *   1. JS calls Android.showAdInterstitielle()
 *   2. Java (k93.showAdInterstitielle) posts an il1 runnable with case=9
 *   3. il1 shows an interstitial ad via AdMob/AppLovin
 *
 * THE PATCH (3 hooks):
 *
 *   HOOK 1: k93.showRA(String designKey) -> call MainActivity.j(designKey, "ok") directly
 *     Skips the rewarded ad entirely but still grants the reward.
 *     The design is unlocked instantly without any ad being shown.
 *     p1 = designKey (e.g., "ulk" for kilominx)
 *
 *   HOOK 2: k93.showAdInterstitielle() -> return-void (no-op)
 *     Interstitial ads never show.
 *
 *   HOOK 3: k93.loadRewardedAd() -> return-void (no-op)
 *     Rewarded ads are never preloaded, saving bandwidth and preventing
 *     the ad SDK from initialising.
 *
 * Result:
 *   - NO ads are ever shown (rewarded or interstitial)
 *   - Designs can still be unlocked by tapping "Watch ad" — the ad is
 *     skipped and the reward is granted instantly
 *   - The "Remove Ads" IAP button still works (it sets localStorage["ulcsall"]
 *     = "ok" which also removes ads, so it's redundant with this patch)
 *   - GDPR popup may still appear (use the UnlockAllPatch to skip it,
 *     since ulcsall=ok also skips GDPR)
 *
 * NOTE: This patch does NOT unlock designs automatically. The user must
 * still tap "Watch ad to unlock" on each locked design (the ad is skipped
 * and the design unlocks instantly). For automatic unlock of ALL designs
 * at startup, use the UnlockAllPatch instead (or in addition).
 *
 * Pattern reference (morphe-ai):
 *   - patcher-apis.md: addInstructions(0, ...) replaces method body
 *     by inserting at index 0 and returning before the original code runs.
 *   - patch-examples.md: Pattern 5 (Callback Replacement) for showRA,
 *     Pattern 1 (returnEarly) for showAdInterstitielle and loadRewardedAd.
 *
 * Pure smali, no extension DEX, no native patching.
 */

package com.jeffprod.cubesolver.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.jeffprod.cubesolver.patches.shared.CUBE_SOLVER
import com.jeffprod.cubesolver.patches.shared.ShowRAFingerprint
import com.jeffprod.cubesolver.patches.shared.ShowAdInterstitielleFingerprint
import com.jeffprod.cubesolver.patches.shared.LoadRewardedAdFingerprint

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Removes all advertisements (rewarded ads and " +
        "interstitial ads) from the app. When the user taps 'Watch ad " +
        "to unlock' on a locked design, the ad is skipped and the " +
        "design is unlocked instantly — the reward is granted without " +
        "showing any ad. Also disables rewarded ad preloading to save " +
        "bandwidth. Note: this patch does NOT automatically unlock " +
        "designs — the user must still tap each locked design to unlock " +
        "it (the ad is just skipped). For automatic unlock of ALL " +
        "designs at startup, enable the 'Unlock all designs' patch. " +
        "Requires the 'Bypass PairIP integrity check' patch.",
    default = true,
) {
    compatibleWith(CUBE_SOLVER)

    dependsOn(bypassPairIPPatch)

    execute {
        // ============================================================
        // HOOK 1: showRA(designKey) -> call j(designKey, "ok") directly
        // ============================================================
        // Pattern: callback replacement (morphe-ai Pattern 5).
        //
        // We replace the method body to call MainActivity.j(p1, "ok")
        // directly, which sets localStorage[p1] = "ok" in the WebView.
        // This is the SAME thing the ad reward callback (y7) does after
        // the ad is watched — we just skip the ad entirely.
        //
        // p0 = this  (k93 instance)
        // p1 = designKey  (String, e.g., "ulk" for kilominx)
        // v0 = scratch (WeakReference, then MainActivity)
        // v1 = scratch ("ok")
        //
        // The method's original .locals 7 is sufficient.
        // ============================================================
        ShowRAFingerprint.method.addInstructions(0, """
            # Get the MainActivity from k93's WeakReference field
            iget-object v0, p0, Lk93;->a:Ljava/lang/ref/WeakReference;
            invoke-virtual {v0}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
            move-result-object v0
            check-cast v0, Lcom/jeffprod/cubesolver/MainActivity;

            # If MainActivity is null, skip
            if-eqz v0, :skip

            # Call MainActivity.j(p1, "ok") to grant the reward without showing ad
            const-string v1, "ok"
            invoke-virtual {v0, p1, v1}, Lcom/jeffprod/cubesolver/MainActivity;->j(Ljava/lang/String;Ljava/lang/String;)V

            :skip
            return-void
        """.trimIndent())


        // ============================================================
        // HOOK 2: showAdInterstitielle() -> no-op
        // ============================================================
        // Pattern: returnEarly (morphe-ai Pattern 1).
        //
        // We replace the method body with a single return-void.
        // The original code that posts the il1 runnable (case=9) which
        // shows the interstitial ad becomes dead code.
        // ============================================================
        ShowAdInterstitielleFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())


        // ============================================================
        // HOOK 3: loadRewardedAd() -> no-op
        // ============================================================
        // Pattern: returnEarly (morphe-ai Pattern 1).
        //
        // We replace the method body with a single return-void.
        // The original code that posts the il1 runnable (case=12) which
        // preloads the rewarded ad becomes dead code.
        // ============================================================
        LoadRewardedAdFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())
    }
}
