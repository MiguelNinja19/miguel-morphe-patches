/*
 * Copyright 2025 diozz-cubex-patches
 * https://github.com/diozz-cubex-patches/morphe-patches
 *
 * Auto-Reward Ads patch for CubeX Solver.
 *
 * ════════════════════════════════════════════════════════════════════
 * IMPORTANT — APP-SPECIFIC FINDING
 * ════════════════════════════════════════════════════════════════════
 *
 * CubeX Solver 4.1.0 declares a REWARDED ad type in its AdManager
 * enum (`rc.a$a.REWARDED`) and registers a rewarded ad unit ID in
 * `AdManagerConfiguration.Builder.rewardedAd(...)`, but the app
 * does NOT actually display a rewarded ad anywhere in its UI. The
 * string resource `R.string.rewarded` ("You've been rewarded! Ads
 * will be removed shortly.") is defined in strings.xml but never
 * referenced from any smali method.
 *
 * Conclusion: there is no user-visible rewarded-ad flow to "skip"
 * in this app — the rewarded ad code path exists in the SDK but
 * is unreachable from the app's UI.
 *
 * What this patch still does (defensive / future-proof):
 *
 *   1. Forces `AdManager.isAdEnabled(REWARDED)` to return TRUE.
 *      This ensures that if a future app update wires up a
 *      "Watch ad to skip the relaunch screen" button, the
 *      rewarded ad loader will be allowed to proceed.
 *
 *   2. Forces `AdManager.isAdEnabled(<every other type>)` to
 *      return FALSE. This is already done by the "Remove
 *      non-rewarded ads" patch, but we add it here too so this
 *      patch is self-contained — applying it alone (without the
 *      other ads patch) still kills all non-rewarded ads.
 *
 *   3. NOTE on "grant reward without watching":
 *      In a typical rewarded-ad flow, the SDK calls
 *      `OnUserEarnedRewardListener.onUserEarnedReward(reward)`
 *      after the user finishes watching the video. Patching that
 *      callback to fire immediately (before the video plays) is
 *      not possible in this app because the callback is never
 *      registered — the rewarded-ad code path is dead. The
 *      closest equivalent in this app is the "Unlock premium"
 *      patch, which makes the entire premium-gating system
 *      return `true` — equivalent to "every reward has been
 *      granted permanently."
 *
 * If a future version of the app adds a "Watch ad to continue"
 * button (e.g. in `RelaunchPremiumActivity`), this patch should
 * be extended to also patch the relevant MaxRewardedAd /
 * YandexRewardedAd / AdMob RewardedAd callback registration.
 */

package diozz.cubex.patches.rewarded

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import diozz.cubex.patches.shared.Constants.CUBEX_SOLVER
import diozz.cubex.patches.shared.IsAdEnabledFingerprint

@Suppress("unused")
val autoRewardAdsPatch = bytecodePatch(
    name = "Auto-reward ads",
    description = "Forces AdManager.isAdEnabled(REWARDED) to return true and " +
        "all other ad types to return false. In this app version there is no " +
        "user-visible rewarded-ad button, so this patch is mainly defensive — " +
        "if a future update adds a 'watch ad to skip' flow, the rewarded ad " +
        "loader will already be allowed. Use the 'Unlock premium' patch for " +
        "the equivalent of 'always granted reward' today.",
    default = true,
) {
    compatibleWith(CUBEX_SOLVER)

    execute {
        // Same short-circuit as the "Remove non-rewarded ads" patch — repeated
        // here so this patch is self-contained if applied alone. Safe to apply
        // both patches together; the second addInstructions is a no-op on a
        // method that already starts with `return-object`.
        //
        // p1 = ad type enum (Lrc/a$a;)
        //   REWARDED             → return TRUE
        //   INTERSTITIAL/BANNER/NATIVE/BANNER_MEDIUM_RECT → return FALSE

        val isAdEnabledMethod = IsAdEnabledFingerprint.method
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
    }
}
