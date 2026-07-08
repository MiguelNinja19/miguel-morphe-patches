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
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * ZipoApps PremiumHelper "Preferences" class — obfuscated as `zc.g`.
 *
 * The class wraps the `premium_helper_data` SharedPreferences and exposes
 * typed getters. The key getter used by every premium check in the app is:
 *
 *   public final boolean i()
 *     → SharedPreferences.getBoolean("has_active_purchase", false)
 *
 * This getter is called from `zc.k.h()`, `zc.k.l()`, `PHSplashActivity`,
 * `SettingsActivity`, `PHMessagingService`, and a few other places.
 * Returning `true` from it makes the app think an active premium
 * subscription is present, which unlocks every premium-gated feature
 * (Advanced Solver, custom color schemes, no relaunch, etc.).
 *
 * We deliberately don't pin on `definingClass = "Lzc/g;"` because the
 * `zc.` prefix is just an obfuscation of the package name and could
 * theoretically be reshuffled in a future SDK release. Instead, the
 * fingerprint is anchored on the very stable SharedPreferences string
 * constant `"has_active_purchase"`, which is the on-disk key the SDK
 * has been using since at least PremiumHelper 4.x.
 *
 * Matching method: `zc.g.i() -> Z`
 */
object PremiumHelperHasActivePurchaseFingerprint : Fingerprint(
    // `zc.g` is the obfuscated class name; declared as a partial match
    // so renaming the prefix in a future build still finds the class.
    definingClass = "/g;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    // No parameters — getter takes no args.
    parameters = emptyList(),
    filters = listOf(
        // The string constant is the most stable anchor — it's the
        // literal SharedPreferences key name written to disk.
        string("has_active_purchase"),
        // Followed by an `iget-object` of the SharedPreferences field,
        // then the interface invoke. We only need the string to uniquely
        // identify this method inside `zc.g` because no other method in
        // the class touches this key.
    )
)

/**
 * ZipoApps AdManager ad-type enum — obfuscated as `rc.a$a` (the inner
 * enum class declared inside `rc.a`).
 *
 * The enum lists the ad types supported by the SDK:
 *   INTERSTITIAL, BANNER, NATIVE, REWARDED, BANNER_MEDIUM_RECT
 *
 * The enum's `<clinit>` static initializer builds the `$VALUES` array
 * and references every enum constant by name as a string. This makes
 * an extremely stable anchor (the SDK has had this exact set of ad
 * types for years). We use this fingerprint to find the AdManager
 * class, then patch individual ad methods by signature via
 * `classFingerprint = AdManagerClassFingerprint`.
 */
object AdManagerClassFingerprint : Fingerprint(
    definingClass = "/rc/a$${'$'}a;",
    // Static initializer (`<clinit>`) builds the enum values array and
    // references every enum constant by name. Picking the static init
    // means the fingerprint survives any renaming of the ad methods.
    accessFlags = listOf(AccessFlags.STATIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    filters = listOf(
        string("INTERSTITIAL"),
        string("BANNER"),
        string("NATIVE"),
        string("REWARDED"),
        string("BANNER_MEDIUM_RECT"),
    )
)

/**
 * `rc.a.f(...)` — `isAdEnabled(adType, ..., continuation)`.
 *
 * Returns `true` if the given ad type should be shown. The method
 * consults the per-app remote config (`rc.j.a(...)`) and the local
 * premium flag, then returns a `Boolean` to the caller.
 *
 * Used by the AdManager internally before showing any ad. Patching
 * this method to return `false` for non-rewarded types cleanly
 * disables interstitial and banner ads without touching each
 * individual ad loader.
 *
 * The fingerprint is intentionally loose: the method is identified
 * by the string `"disabled"` (used as the sentinel return value
 * when an ad type is turned off) plus the invocation of
 * `rc.j.a(...)` which is the remote-config lookup.
 */
object IsAdEnabledFingerprint : Fingerprint(
    classFingerprint = AdManagerClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Boolean;",
    // (Lrc/a$a; Z Lje/d;) → (L, Z, L)  (rc/a$a is obfuscated to "L")
    parameters = listOf("L", "Z", "L"),
    filters = listOf(
        string("disabled"),
    )
)

/**
 * `rc.a.k(Activity)` — `shouldShowExitAd(activity)`.
 *
 * Called by `MainActivity.onBackPressed()` to decide whether to show
 * an interstitial when the user tries to leave the app. Returns `true`
 * to show the exit ad, `false` to skip it and call `super.onBackPressed()`.
 *
 * Signature: public final boolean k(android.app.Activity)
 *
 * The body references the `ph_ad_close_view` resource ID, which is
 * unique to this method. We anchor on it.
 */
object ExitAdFingerprint : Fingerprint(
    classFingerprint = AdManagerClassFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("Landroid/app/Activity;"),
    filters = listOf(
        // `ph_ad_close_view` is the resource ID used by the exit-ad
        // overlay layout. Anchoring on the resource name keeps the
        // fingerprint stable across renames of `rc.a.k`.
        string("ph_ad_close_view"),
    )
)

/**
 * `zc.k.h()` — `shouldShowRelaunchOrPremium()` — public final boolean h().
 *
 * This is the main "should we interrupt the user with a premium
 * relaunch / start-like-pro screen" gate. It's called from
 * `SettingsActivity.onResume()` to toggle the visibility of the
 * "Remove Ads" and "Personalized Ads" preference rows, and from
 * `MainActivity.onBackPressed()` indirectly.
 *
 * Returning `false` from this method makes the app behave as if the
 * user is in a "premium trial expired / not eligible for relaunch"
 * state — which conveniently also disables the start-like-pro
 * relaunch flow on app launch.
 *
 * The method body checks `zc.g.i()` (has_active_purchase) and the
 * consent manager state (`rc.v.b()`), then returns based on the
 * remote config flag stored in `v7.c` (the consent info object).
 *
 * The fingerprint is anchored on the static call
 * `Lrc/v;->b()Z` (`PhConsentManager.isConsentRequired()`),
 * which is invoked exactly once inside this method.
 *
 * Matching method: `zc.k.h() -> Z`
 */
object ShouldShowRelaunchFingerprint : Fingerprint(
    definingClass = "/rc/k;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        // Static call `Lrc/v;->b()Z` — `PhConsentManager.isConsentRequired()`.
        // Invoked exactly once inside zc.k.h(), so this uniquely identifies
        // the method even though zc.k has many other boolean getters.
        app.morphe.patcher.methodCall(
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
 * and starts it. This is the actual "Baixe este app na Play Store"
 * trigger — when invoked, the user is yanked out of the app and
 * shown the relaunch/premium upsell screen.
 *
 * Patching this method to immediately `return-void` prevents the
 * relaunch activity from ever being started, which is the cleanest
 * way to disable the entire relaunch flow without touching each
 * caller.
 *
 * Signature (obfuscated):
 *   public static void n(zc.k, String)
 *
 * We anchor on the string `"Intent(context, RelaunchPremiumActivity.ARG_THEME, theme)"`
 * which is a Kotlin-origin debug assertion string baked into this
 * method. It is unique to `zc.k.n()` and very stable.
 */
object LaunchRelaunchActivityFingerprint : Fingerprint(
    definingClass = "/rc/k;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Lrc/k;", "Ljava/lang/String;"),
    filters = listOf(
        // Kotlin `requireNotNull`/`check` assertion string is baked
        // into zc.k.n() by the Kotlin compiler. Uniquely identifies
        // this method. The `\u2026` is the Kotlin "…" ellipsis the
        // compiler inserts when a string is too long for the inline
        // debug representation.
        string("Intent(context, Relaunch\u2026ctivity.ARG_THEME, theme)"),
    )
)
