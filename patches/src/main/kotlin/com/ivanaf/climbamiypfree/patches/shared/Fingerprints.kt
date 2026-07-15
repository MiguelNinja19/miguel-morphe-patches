package com.ivanaf.climbamiypfree.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * GameMaker RunnerBillingSecurity.verifyPurchase(String, String)Z
 * Verifies purchase signature. Patch to always return true.
 */
object VerifyPurchaseFingerprint : Fingerprint(
    definingClass = "Lcom/IvanAF/ClimbAMIYPfree/RunnerBillingSecurity;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/util/Log;",
            name = "e",
        ),
    )
)

/**
 * GoogleMobileAdsGM.AdMob_Interstitial_Show()D
 * Shows interstitial ad. Patch to return 0 (skip).
 */
object InterstitialShowFingerprint : Fingerprint(
    definingClass = "Lcom/IvanAF/ClimbAMIYPfree/GoogleMobileAdsGM;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "D",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/IvanAF/ClimbAMIYPfree/GoogleMobileAdsGM;",
            name = "showInterstitialAd",
        ),
    )
)

/**
 * GoogleMobileAdsGM.AdMob_Banner_Show()D
 * Shows banner ad. Patch to return 0 (skip).
 */
object BannerShowFingerprint : Fingerprint(
    definingClass = "Lcom/IvanAF/ClimbAMIYPfree/GoogleMobileAdsGM;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "D",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/google/android/gms/ads/AdView;",
            name = "setVisibility",
        ),
    )
)

/**
 * GoogleMobileAdsGM.AdMob_Banner_Create(DD)D
 * Creates banner ad. Patch to return 0 (skip).
 */
object BannerCreateFingerprint : Fingerprint(
    definingClass = "Lcom/IvanAF/ClimbAMIYPfree/GoogleMobileAdsGM;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "D",
    parameters = listOf("D", "D"),
)

/**
 * GoogleMobileAdsGM.AdMob_RewardedVideo_Show()D
 * Shows rewarded video ad. Patch to return 0 (skip).
 */
object RewardedVideoShowFingerprint : Fingerprint(
    definingClass = "Lcom/IvanAF/ClimbAMIYPfree/GoogleMobileAdsGM;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "D",
    parameters = emptyList(),
)

/**
 * GoogleMobileAdsGM.AdMob_RewardedInterstitial_Show()D
 * Shows rewarded interstitial ad. Patch to return 0 (skip).
 */
object RewardedInterstitialShowFingerprint : Fingerprint(
    definingClass = "Lcom/IvanAF/ClimbAMIYPfree/GoogleMobileAdsGM;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "D",
    parameters = emptyList(),
)

/**
 * GoogleMobileAdsGM.AdMob_AppOpenAd_Enable(D)D
 * Enables app open ad. Patch to return 0 (disable).
 */
object AppOpenAdEnableFingerprint : Fingerprint(
    definingClass = "Lcom/IvanAF/ClimbAMIYPfree/GoogleMobileAdsGM;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "D",
    parameters = listOf("D"),
)
