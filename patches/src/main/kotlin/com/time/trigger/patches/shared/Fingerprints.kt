package com.time.trigger.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

object BillingResultGetResponseCodeFingerprint : Fingerprint(
    definingClass = "Lcom/android/billingclient/api/BillingResult;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "I",
    parameters = emptyList(),
)

object PurchaseResultFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        string("onPurchasesUpdated"),
        string("GoogleBillingApi"),
    )
)
