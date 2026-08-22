package com.Neurononfire.SupremeDuelist.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

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

object InitializeLicenseCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
)

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
