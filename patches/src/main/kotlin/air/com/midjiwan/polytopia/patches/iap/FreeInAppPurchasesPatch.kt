package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.Constants.POLYTOPIA
import air.com.midjiwan.polytopia.patches.shared.UnityBillingBridgeFingerprint
import air.com.midjiwan.polytopia.patches.shared.UnityBillingSetupFingerprint

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips Google Play Billing and simulates successful " +
        "purchases by calling the Unity JNI bridge (nativeOnPurchasesUpdated) " +
        "directly with responseCode=0 (OK).",
    default = true,
) {
    compatibleWith(POLYTOPIA)

    execute {
        val purchasesUpdatedMethod = UnityBillingBridgeFingerprint.method
        purchasesUpdatedMethod.addInstructions(0, """
            const/4 v0, 0x0
            const-string v1, ""
            const/4 v2, 0x0
            invoke-static {v0, v1, v2}, Lcom/android/billingclient/api/zzbq;->nativeOnPurchasesUpdated(ILjava/lang/String;[Lcom/android/billingclient/api/Purchase;)V
            return-void
        """.trimIndent())

        val billingSetupMethod = UnityBillingSetupFingerprint.method
        billingSetupMethod.addInstructions(0, """
            const/4 v0, 0x0
            const-string v1, ""
            const-wide/16 v2, 0x0
            invoke-static {v0, v1, v2, v3}, Lcom/android/billingclient/api/zzbq;->nativeOnBillingSetupFinished(ILjava/lang/String;J)V
            return-void
        """.trimIndent())
    }
}
