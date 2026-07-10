package com.time.trigger.patches.shared

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

object BillingResultGetResponseCodeFingerprint : Fingerprint(
    definingClass = "Lcom/android/billingclient/api/BillingResult;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "I",
    parameters = emptyList(),
)

object PurchaseProductFingerprint : Fingerprint(
    definingClass = "Lsaygames/bridge/unity/SayKitBridge;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        app.morphe.patcher.methodCall(
            definingClass = "Lsaygames/saykit/SayKit${'$'}Purchases;",
            name = "purchaseProduct",
        ),
    )
)
