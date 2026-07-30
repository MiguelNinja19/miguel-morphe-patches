package com.jeffprod.cubesolver.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Fingerprint for k93.appReady().
 *
 * Cube Solver is a WebView-based app. The JS code calls Android.appReady()
 * when the WebView finishes loading. Perfect hook point for injecting
 * localStorage values via MainActivity.j(key, value).
 */
object AppReadyFingerprint : Fingerprint(
    definingClass = "Lk93;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Ljava/lang/ref/Reference;",
            name = "get",
            returnType = "Ljava/lang/Object;",
        ),
    )
)

/**
 * Fingerprint for k93.showRA(String designKey).
 *
 * Called by JS when user taps "Watch ad to unlock". We hook this to
 * call MainActivity.j(designKey, "ok") directly, skipping the ad.
 */
object ShowRAFingerprint : Fingerprint(
    definingClass = "Lk93;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        methodCall(
            definingClass = "Ljl1;",
            name = "<init>",
        ),
    )
)

/**
 * Fingerprint for k93.showAdInterstitielle().
 *
 * Called by JS to show interstitial ads. We no-op this.
 */
object ShowAdInterstitielleFingerprint : Fingerprint(
    definingClass = "Lk93;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lil1;",
            name = "<init>",
        ),
    )
)

/**
 * Fingerprint for k93.loadRewardedAd().
 *
 * Called by JS to preload rewarded ads. We no-op this.
 */
object LoadRewardedAdFingerprint : Fingerprint(
    definingClass = "Lk93;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lil1;",
            name = "<init>",
        ),
    )
)

/**
 * Fingerprint for PairIP SignatureCheck.verifyIntegrity(Context).
 *
 * Computes SHA-256 of the APK signing certificate and compares to expected
 * hash. Throws SignatureTamperedException on mismatch (crashes the app).
 *
 * We no-op this (return-void) so the signature check is skipped.
 */
object SignatureCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/SignatureCheck;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/pairip/SignatureCheck;",
            name = "verifySignatureMatches",
        ),
    )
)

/**
 * Fingerprint for PairIP SignatureCheck.verifySignatureMatches(String).
 *
 * Called by verifyIntegrity. Returns true if signature matches expected.
 * We hook this to always return true (belt-and-suspenders).
 */
object VerifySignatureMatchesFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/SignatureCheck;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;"),
)

/**
 * Fingerprint for PairIP LicenseClient.checkLicense(Context).
 *
 * Called from Application.attachBaseContext. We no-op this.
 */
object LicenseCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
            name = "isIsolatedProcess",
        ),
    )
)

/**
 * Fingerprint for PairIP LicenseClient.initializeLicenseCheck().
 *
 * THIS IS THE KEY METHOD. It's an INSTANCE method (not static) that does
 * the actual license verification. It's called from TWO places:
 *
 *   1. LicenseClient.checkLicense(Context) — the static method we already
 *      no-op. This path is from Application.attachBaseContext.
 *
 *   2. LicenseContentProvider.onCreate() — creates a NEW LicenseClient
 *      and calls initializeLicenseCheck() DIRECTLY, bypassing our
 *      checkLicense no-op! ContentProviders run BEFORE
 *      Application.attachBaseContext in the Android lifecycle, so this
 *      path triggers the license check before our patch even runs.
 *
 * The PairIP VM bytecode might also call initializeLicenseCheck directly.
 *
 * Smali signature:
 *   .method public initializeLicenseCheck()V
 *     .locals 3
 *     ... checks licenseCheckState ordinal ...
 *     ... calls connectToLicensingService or validateResponse ...
 *     ... on error: handleError -> startErrorDialogActivity ->
 *         createCloseAppIntentOrExitIfAppInBackground -> LicenseActivity
 *         (this is the Play Store redirect the user sees)
 *
 * We no-op this (return-void) so the license check NEVER runs, regardless
 * of which code path triggers it.
 *
 * NOTE: No filter is used because the method is unique enough (only one
 * public initializeLicenseCheck()V with no parameters in LicenseClient).
 * Using LicenseCheckState as a filter caused a compile error because
 * it's a smali inner class (LicenseClient$LicenseCheckState) which
 * Kotlin can't resolve as a type.
 */
object InitializeLicenseCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
)
