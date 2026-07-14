/*
 * Free In-App Purchases patch for The Battle of Polytopia.
 *
 * Patches the Google Play Billing bridge (zzbq) to report success and
 * validate purchases. This complements the Unlock all tribes patch
 * (which hex-patches libil2cpp.so) by handling the billing layer.
 *
 * HOOKS:
 * 1. zzbq.onBillingSetupFinished -> force responseCode=0 (success)
 * 2. Purchase.isAcknowledged() -> return true
 * 3. Purchase.getPurchaseState() -> return 1 (PURCHASED)
 * 4. zzbq.onPurchasesUpdated -> swallow errors (conditional return-void)
 *
 * Note: For unlocking tribes and skins, use the "Unlock all tribes"
 * patch instead — it patches the native IL2CPP library directly.
 * This patch handles the Google Play Billing layer for crystal packs
 * and other IAP items.
 */

package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA
import air.com.midjiwan.polytopia.patches.shared.UnityBillingSetupFingerprint
import air.com.midjiwan.polytopia.patches.shared.UnityPurchasesUpdatedFingerprint
import air.com.midjiwan.polytopia.patches.shared.PurchaseIsAcknowledgedFingerprint
import air.com.midjiwan.polytopia.patches.shared.PurchaseGetStateFingerprint

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Patches the Google Play Billing bridge to report " +
        "success and validate purchases. Handles crystal packs and " +
        "other IAP items at the billing layer. For unlocking tribes " +
        "and skins, use the 'Unlock all tribes' patch instead.",
    default = false,
) {
    compatibleWith(POLYTOPIA)

    execute {
        UnityBillingSetupFingerprint.method.addInstructions(0, """
            iget-wide v0, p0, Lcom/android/billingclient/api/zzbq;->zza:J
            const/4 v2, 0x0
            const-string v3, ""
            invoke-static {v2, v3, v0, v1}, Lcom/android/billingclient/api/zzbq;->nativeOnBillingSetupFinished(ILjava/lang/String;J)V
            return-void
        """.trimIndent())

        PurchaseIsAcknowledgedFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            return v0
        """.trimIndent())

        PurchaseGetStateFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            return v0
        """.trimIndent())

        UnityPurchasesUpdatedFingerprint.method.addInstructions(0, """
            invoke-virtual {p1}, Lcom/android/billingclient/api/BillingResult;->getResponseCode()I
            move-result v0
            if-eqz v0, :continue
            return-void
            :continue
            nop
        """.trimIndent())
    }
}
