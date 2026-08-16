package com.Neurononfire.SupremeDuelist.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Fingerprint for PairIP LicenseClient.checkLicense(Context).
 *
 * Static method called from Application.attachBaseContext.
 * We no-op this to prevent the license check from starting.
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
 * Instance method that does the actual license verification.
 * Called by checkLicense, but could also be called from elsewhere.
 * We no-op this as a safety net.
 *
 * NOTE: No filter is used because the method is unique enough (only one
 * public initializeLicenseCheck()V with no parameters in LicenseClient).
 */
object InitializeLicenseCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
)

/**
 * Fingerprint for BillingClientImpl.launchBillingFlow(Activity, BillingFlowParams).
 *
 * Entry point for purchases. We hook this to call nativeOnPurchasesUpdated
 * directly with success, skipping the Play Store dialog.
 *
 * NOTE: Uses name = "launchBillingFlow" instead of filters because the
 * method is very large (28 locals) and doesn't directly call
 * nativeOnPurchasesUpdated (that happens asynchronously via onPurchasesUpdated
 * callback). Using name is more reliable.
 */
object LaunchBillingFlowFingerprint : Fingerprint(
    definingClass = "Lcom/android/billingclient/api/BillingClientImpl;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    name = "launchBillingFlow",
    returnType = "Lcom/android/billingclient/api/BillingResult;",
    parameters = listOf(
        "Landroid/app/Activity;",
        "Lcom/android/billingclient/api/BillingFlowParams;",
    ),
)

/**
 * Fingerprint for zzbq.onPurchasesUpdated(BillingResult, List<Purchase>).
 *
 * Callback for purchase completion. We force responseCode=0 (success).
 */
object OnPurchasesUpdatedFingerprint : Fingerprint(
    definingClass = "Lcom/android/billingclient/api/zzbq;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Lcom/android/billingclient/api/BillingResult;",
        "Ljava/util/List;",
    ),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/android/billingclient/api/zzbq;",
            name = "nativeOnPurchasesUpdated",
        ),
    )
)
