/*
 * Copyright 2025 diozz-cubex-patches
 * https://github.com/diozz-cubex-patches/morphe-patches
 *
 * Fingerprints for the PremiumHelper SDK and the AdManager shipped
 * inside CubeX Solver. The SDK is shipped obfuscated (zc.*, rc.*, bd.*,
 * nd.*) but the method bodies are very stable because ZipoApps reuses
 * the same SDK across all their apps.
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
 * subscription is present, which unlocks every premium-gated feature.
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
 * `rc.a.f(...)` — `isAdEnabled(adType, ..., continuation)`.
 *
 * Returns true if the given ad type should be shown. The method
 * consults the per-app remote config (`rc.j.a(...)`) and the local
 * premium flag, then returns a `Boolean` to the caller.
 *
 * We anchor on the string `"disabled"` (used as the sentinel return
 * value when an ad type is turned off). This string is unique to
 * this method inside `rc.a`.
 *
 * Matching method: `rc.a.f(Lrc/a$a;ZLje/d;)Ljava/lang/Boolean;`
 */
object IsAdEnabledFingerprint : Fingerprint(
    definingClass = "Lrc/a;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Boolean;",
    parameters = listOf("L", "Z", "L"),
    filters = listOf(
        string("disabled"),
    )
)

/**
 * `rc.a.k(Activity)` — `shouldShowExitAd(activity)`.
 *
 * Called by `MainActivity.onBackPressed()` to decide whether to show
 * an interstitial when the user tries to leave the app.
 *
 * The body references the `ph_ad_close_view` resource ID, which is
 * unique to this method. We anchor on it.
 *
 * Matching method: `rc.a.k(Landroid/app/Activity;)Z`
 */
object ExitAdFingerprint : Fingerprint(
    definingClass = "Lrc/a;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Landroid/app/Activity;"),
    filters = listOf(
        string("ph_ad_close_view"),
    )
)

/**
 * `zc.k.h()` — `shouldShowRelaunchOrPremium()` — public final boolean h().
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
 * `com.zipoapps.premiumhelper.ui.relaunch.RelaunchPremiumActivity`
 * and starts it.
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
