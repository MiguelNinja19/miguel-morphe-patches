/*
 * Copyright 2025 diozz-cubex-patches
 * https://github.com/diozz-cubex-patches/morphe-patches
 *
 * Fingerprints for the PremiumHelper SDK shipped inside CubeX Solver.
 * The SDK is shipped obfuscated (zc.*, rc.*, bd.*, nd.*) but the method
 * bodies are very stable because ZipoApps reuses the same SDK across
 * all their apps.
 */

package diozz.cubex.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * ZipoApps PremiumHelper "Preferences" class — obfuscated as `zc.g`.
 *
 * The class wraps the `premium_helper_data` SharedPreferences and exposes
 * typed getters. The key getter used by every premium check in the app is:
 *
 *   public final boolean i()
 *     → SharedPreferences.getBoolean("has_active_purchase", false)
 *
 * Returning `true` from it makes the app think an active premium
 * subscription is present, which unlocks every premium-gated feature
 * (Advanced Solver / Kociemba, custom color schemes, VIP support, no
 * relaunch screens, no ads).
 *
 * We anchor on the very stable SharedPreferences string constant
 * `"has_active_purchase"` — the on-disk key the SDK has been using
 * since at least PremiumHelper 4.x.
 *
 * Matching method: `zc.g.i() -> Z`
 */
object PremiumHelperHasActivePurchaseFingerprint : Fingerprint(
    definingClass = "Lzc/g;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        string("has_active_purchase"),
    )
)

/**
 * `zc.k.h()` — `shouldShowRelaunchOrPremium()` — public final boolean h().
 *
 * The main "should we interrupt the user with a premium relaunch /
 * start-like-pro screen" gate. Returning false makes the app behave
 * as if the user is already premium or past the relaunch window.
 *
 * The fingerprint is anchored on the static call
 * `Lrc/v;->b()Z` (`PhConsentManager.isConsentRequired()`),
 * which is invoked exactly once inside this method.
 *
 * Matching method: `zc.k.h() -> Z`
 */
object ShouldShowRelaunchFingerprint : Fingerprint(
    definingClass = "Lzc/k;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lrc/v;",
            name = "b",
            returnType = "Z",
        ),
    )
)

/**
 * `zc.k.n(String)` — `launchRelaunchPremiumActivity(source)`.
 *
 * Static method that builds an Intent targeting
 * `com.zipoapps.prempremiumhelper.ui.relaunch.RelaunchPremiumActivity`
 * and starts it. This is the actual "Baixe este app na Play Store"
 * trigger — when invoked, the user is yanked out of the app and
 * shown the relaunch/premium upsell screen.
 *
 * Patching this method to immediately `return-void` prevents the
 * relaunch activity from ever being started.
 *
 * We anchor on the Kotlin compiler-emitted assertion string
 * `"Intent(context, Relaunch…ctivity.ARG_THEME, theme)"` (with the
 * \u2026 ellipsis) which is unique to this method.
 *
 * Signature (obfuscated):
 *   public static void n(zc.k, String)
 */
object LaunchRelaunchActivityFingerprint : Fingerprint(
    definingClass = "Lzc/k;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Lzc/k;", "Ljava/lang/String;"),
    filters = listOf(
        string("Intent(context, Relaunch\u2026ctivity.ARG_THEME, theme)"),
    )
)
