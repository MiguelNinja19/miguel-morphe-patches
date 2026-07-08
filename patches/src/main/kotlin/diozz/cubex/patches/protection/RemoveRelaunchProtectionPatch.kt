/*
 * Copyright 2025 diozz-cubex-patches
 * https://github.com/diozz-cubex-patches/morphe-patches
 *
 * Remove Relaunch / "Pairip-Style" Protection patch for CubeX Solver.
 *
 * CubeX Solver doesn't ship with the native `libpairipcore.so` from
 * Google Play's app integrity SDK, but it does ship the ZipoApps
 * "PremiumHelper" relaunch flow that achieves the same user-facing
 * effect: after the app is detected as modified (or sideloaded from
 * a non-Play source), the next launch yanks the user into the
 * `RelaunchPremiumActivity` screen which says, in effect,
 * "Baixe este app na Play Store".
 *
 * The flow has three trigger points:
 *
 *   1. `zc.k.n(String)` — static method that builds and starts an
 *      Intent targeting RelaunchPremiumActivity. Called from the
 *      preference screens and (transitively) from the splash
 *      activity when the app detects it's been tampered with.
 *
 *   2. `zc.k.h()` — public boolean gate used by SettingsActivity
 *      and MainActivity to decide whether to show the "Remove Ads"
 *      / "Personalized Ads" rows and whether to intercept the
 *      back button.
 *
 *   3. `PHSplashActivity` — the entry-point activity that, on
 *      first launch after install, decides whether to route the
 *      user to `StartLikeProActivity` (a one-time premium upsell)
 *      instead of `MainActivity`. The decision is based on
 *      `zc.g.i()` (has_active_purchase) — which is already patched
 *      to true by the "Unlock premium" patch, so we don't need to
 *      touch the splash here.
 *
 * This patch handles trigger points 1 and 2 directly so that the
 * relaunch activity never starts, even if some new caller is added
 * in a future SDK update.
 */

package diozz.cubex.patches.protection

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import diozz.cubex.patches.shared.Constants.CUBEX_SOLVER
import diozz.cubex.patches.shared.LaunchRelaunchActivityFingerprint
import diozz.cubex.patches.shared.ShouldShowRelaunchFingerprint

@Suppress("unused")
val removeRelaunchProtectionPatch = bytecodePatch(
    name = "Remove relaunch protection",
    description = "Disables the ZipoApps PremiumHelper relaunch / " +
        "start-like-pro flow that shows a \"Baixe este app na Play Store\" " +
        "screen when the app detects it has been modified. Patches " +
        "zc.k.n(String) to no-op and zc.k.h() to always return false.",
    default = true,
) {
    compatibleWith(CUBEX_SOLVER)

    execute {
        // -----------------------------------------------------------------
        // 1) Make zc.k.n(String) a no-op.
        // -----------------------------------------------------------------
        //
        // Original body:
        //   .method public static n(Lzc/k;Ljava/lang/String;)V
        //     .locals 3
        //     const-string v0, "source"
        //     invoke-static {p1, v0}, Lse/l;->f(...)
        //     ... build Intent, startActivity ...
        //     return-void
        //
        // We add `return-void` as the very first instruction. The
        // method takes two parameters (p0 = zc.k instance, p1 = source
        // string) but returns void, so a bare `return-void` at the top
        // short-circuits the entire method. The rest becomes dead code.

        LaunchRelaunchActivityFingerprint.method.addInstructions(0, "return-void")

        // -----------------------------------------------------------------
        // 2) Make zc.k.h() always return false.
        // -----------------------------------------------------------------
        //
        // Original body returns true when the user is eligible for the
        // relaunch / start-like-pro flow (i.e. not premium, and the
        // consent manager returned a specific status). Returning false
        // makes the app behave as if the user is already premium or
        // already past the relaunch window.
        //
        // Method signature: public final h()Z  — instance method, no
        // parameters, returns boolean. `.locals 3` in original.
        //
        // Replace the first instruction with `const/4 v0, 0x0` and the
        // second with `return v0`. The rest becomes dead code.

        val shouldShowMethod = ShouldShowRelaunchFingerprint.method
        shouldShowMethod.replaceInstruction(0, "const/4 v0, 0x0")
        shouldShowMethod.replaceInstruction(1, "return v0")
    }
}
