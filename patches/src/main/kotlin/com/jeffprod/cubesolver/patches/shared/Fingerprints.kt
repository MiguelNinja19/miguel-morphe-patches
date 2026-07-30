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
