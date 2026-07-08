/*
 * Copyright 2025 diozz-cubex-patches
 * https://github.com/diozz-cubex-patches/morphe-patches
 *
 * Remove Non-Rewarded Ads patch for CubeX Solver.
 *
 * The app uses the ZipoApps AdManager (`rc.a`) which supports five
 * ad types (INTERSTITIAL, BANNER, NATIVE, REWARDED, BANNER_MEDIUM_RECT).
 * The user explicitly wants to:
 *   • Remove interstitial ads (full-screen popups).
 *   • Remove banner ads (the PhShimmerBannerAdView at the bottom of
 *     every screen).
 *   • Remove the exit-ad overlay (the "are you sure you want to leave"
 *     popup that appears on back-press).
 *   • KEEP rewarded ads functional — they are the mechanism the app
 *     uses to grant in-app rewards (the next patch makes them grant
 *     the reward without actually showing the ad).
 *
 * Approach: patch `rc.a.f(adType, ...)` — the `isAdEnabled` method —
 * to return `Boolean.FALSE` for all ad types EXCEPT REWARDED. We do
 * this by replacing the existing return-Boolean logic at the end of
 * the method. We also patch `rc.a.k(Activity)` (exit-ad gate) to
 * always return false, so the back-press exit ad never triggers.
 *
 * Note: when the "Unlock premium" patch is also applied, premium
 * status already suppresses most ads via the AdManager's own
 * `isPremiumActive()` branch inside `rc.j.a(...)`. This patch is
 * therefore belt-and-suspenders — it kills ads even if the premium
 * unlock is missing for some reason.
 */

package diozz.cubex.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import diozz.cubex.patches.shared.Constants.CUBEX_SOLVER
import diozz.cubex.patches.shared.ExitAdFingerprint
import diozz.cubex.patches.shared.IsAdEnabledFingerprint

@Suppress("unused")
val removeNonRewardedAdsPatch = bytecodePatch(
    name = "Remove non-rewarded ads",
    description = "Disables interstitial, banner, native and exit ads without " +
        "touching rewarded ads. Belt-and-suspenders complement to the premium " +
        "unlock — useful if the AdManager's own premium suppression is bypassed " +
        "by a remote config flag.",
    default = true,
) {
    compatibleWith(CUBEX_SOLVER)

    execute {
        // -----------------------------------------------------------------
        // 1) Disable interstitial / banner / native ad gating in rc.a.f()
        // -----------------------------------------------------------------
        //
        // `rc.a.f(Lrc/a$a; Z Lje/d;)Ljava/lang/Boolean;` is `isAdEnabled(adType, ...)`.
        // The first parameter (p1, register slot `p1`) is the ad type enum
        // value (`Lrc/a$a;`). The method ultimately returns a boxed
        // `java.lang.Boolean`. We want to return:
        //   • FALSE for INTERSTITIAL, BANNER, NATIVE, BANNER_MEDIUM_RECT
        //   • TRUE  for REWARDED  (so rewarded ads still load — the next
        //     patch will then make them grant the reward without showing)
        //
        // The simplest smali insertion is at the very top of the method:
        //
        //   sget-object v0, Lrc/a$a;->REWARDED:Lrc/a$a;
        //   if-ne p1, v0, :cond_not_rewarded
        //   sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
        //   return-object v0
        //   :cond_not_rewarded
        //   sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
        //   return-object v0
        //
        // This short-circuits the entire method. p1 is the first parameter
        // register (after `this` for instance methods). Because `rc.a.f`
        // is declared as `public final` (instance method) with signature
        // `(Lrc/a$a; Z Lje/d;)`, the parameter register layout is:
        //   p0 = this  (Lrc/a;)
        //   p1 = adType (Lrc/a$a;)
        //   p2 = boolean Z
        //   p3 = continuation Lje/d;
        //
        // We use `.locals 1` smali register v0 for the comparison/return.

        val isAdEnabledMethod = IsAdEnabledFingerprint.method

        // Replace the first instruction with our short-circuit. Morphe's
        // `addInstructions(0, ...)` inserts at the start of the method
        // without removing the original body, so the original code becomes
        // unreachable dead code (legal smali).
        isAdEnabledMethod.addInstructions(
            0,
            """
                # Compare ad type (p1) against REWARDED.
                sget-object v0, Lrc/a$${'$'}a;->REWARDED:Lrc/a$${'$'}a;
                if-ne p1, v0, :cond_is_rewarded

                # Not rewarded → return FALSE.
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0

                :cond_is_rewarded
                # Rewarded → return TRUE.
                sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                return-object v0
            """.trimIndent()
        )

        // -----------------------------------------------------------------
        // 2) Disable the exit-ad overlay in rc.a.k(Activity)
        // -----------------------------------------------------------------
        //
        // `rc.a.k(Landroid/app/Activity;)Z` returns true if the exit ad
        // should be shown when the user tries to leave the app. Returning
        // false makes `MainActivity.onBackPressed()` fall through to
        // `super.onBackPressed()` immediately, skipping the exit ad.
        //
        // Method body starts with:
        //   .method public final k(Landroid/app/Activity;)Z
        //     .locals 1
        //     const-string v0, "activity"
        //     invoke-static {p1, v0}, Lse/l;->f(...)
        //     ...
        //
        // We replace the first instruction with `const/4 v0, 0x0` and
        // the second with `return v0`, leaving the rest as dead code.

        val exitAdMethod = ExitAdFingerprint.method
        exitAdMethod.replaceInstruction(0, "const/4 v0, 0x0")
        exitAdMethod.replaceInstruction(1, "return v0")
    }
}
