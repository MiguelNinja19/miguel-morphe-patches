package com.rubygames.assassin.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

object StartPurchaseFlowFingerprint : Fingerprint(
    definingClass = "Lcom/rovio/beacon/billing/GooglePlayBillingProvider;",
    returnType = "V",
    filters = listOf(
        string("Purchase: Starting purchase flow"),
        methodCall(
            definingClass = "Lcom/android/billingclient/api/BillingClient;",
            name = "launchBillingFlow",
        ),
    )
)

object OnAdHiddenFingerprint : Fingerprint(
    definingClass = "Lcom/rovio/beacon/ads/AdsSdk;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Z"),
)
