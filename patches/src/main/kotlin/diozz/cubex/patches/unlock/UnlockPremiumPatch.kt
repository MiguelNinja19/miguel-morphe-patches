/*
 * Copyright 2025 diozz-cubex-patches
 * https://github.com/diozz-cubex-patches/morphe-patches
 *
 * Unlock Premium patch for CubeX Solver.
 *
 * The app gates features behind a single boolean:
 *   zc.g.i()  →  SharedPreferences.getBoolean("has_active_purchase", false)
 *
 * When this returns true, the following features unlock:
 *   • Advanced Solver (Kociemba two-phase) — previously the "last cube
 *     option" that was greyed out at the end of the solver selector.
 *   • Custom color schemes (PaletteSettings, "CustomSchemeEnabled").
 *   • VIP customer support email.
 *   • No "Remove Ads" prompt in Settings.
 *   • No "Start Like Pro" / "Relaunch Premium" interruption screens.
 *   • No banner / interstitial / exit ads.
 *
 * Returning true from this single getter is the cleanest unlock
 * because the entire app already branches on it. We don't need to
 * patch each call site individually.
 */

package diozz.cubex.patches.unlock

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import diozz.cubex.patches.shared.Constants.CUBEX_SOLVER
import diozz.cubex.patches.shared.PremiumHelperHasActivePurchaseFingerprint

@Suppress("unused")
val unlockPremiumPatch = bytecodePatch(
    name = "Unlock premium",
    description = "Unlocks all premium-gated features (Advanced Solver / Kociemba, " +
        "custom color schemes, VIP support, no relaunch screens). Returns " +
        "true from PremiumHelper.hasActivePurchase() so the entire app " +
        "behaves as if a premium subscription is active.",
    // On by default — this is the main reason someone patches this app.
    default = true,
) {
    compatibleWith(CUBEX_SOLVER)

    execute {
        // Original method body (smali):
        //   .method public final i()Z
        //     const-string v0, "has_active_purchase"
        //     const/4 v1, 0x0
        //     iget-object v2, p0, Lzc/g;->a:Landroid/content/SharedPreferences;
        //     invoke-interface {v2, v0, v1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
        //     move-result v0
        //     return v0
        //
        // We replace the entire body with:
        //   const/4 v0, 0x1
        //   return v0
        //
        // The method has `.locals 3` so register v0 is safe to use as
        // the return value. We rewrite the first two instructions and
        // then remove the rest, but the simplest approach is to replace
        // the very first instruction (`const-string v0, "has_active_purchase"`)
        // with `const/4 v0, 0x1` and `return v0` and let the rest become
        // dead code (the smali verifier accepts unreachable instructions).
        //
        // However, to keep the patch clean and avoid verifier issues on
        // strict ART builds, we replace the first instruction with a
        // `const/4 v0, 0x1` followed by `return v0`, which makes the
        // remaining original instructions unreachable but still valid.

        val method = PremiumHelperHasActivePurchaseFingerprint.method

        // Replace the first instruction (`const-string v0, ...`) with
        // `const/4 v0, 0x1` (load true).
        method.replaceInstruction(
            0,
            "const/4 v0, 0x1"
        )
        // Replace the second instruction (`const/4 v1, 0x0`) with
        // `return v0` — return true immediately.
        method.replaceInstruction(
            1,
            "return v0"
        )
        // The remaining instructions (iget, invoke-interface, move-result,
        // return) become unreachable dead code, which is legal smali.
    }
}
