package air.com.midjiwan.polytopia.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

object UnityBillingBridgeFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Lcom/android/billingclient/api/BillingResult;",
        "Ljava/util/List;"
    ),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/android/billingclient/api/zzbq;",
            name = "nativeOnPurchasesUpdated",
        ),
    )
)

object UnityBillingSetupFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Lcom/android/billingclient/api/BillingResult;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/android/billingclient/api/zzbq;",
            name = "nativeOnBillingSetupFinished",
        ),
    )
)
