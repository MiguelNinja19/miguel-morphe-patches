/*
 * Free In-App Purchases patch for The Battle of Polytopia.
 *
 * FIX: The previous version's HOOK 4 (onPurchasesUpdated) was causing
 * "Purchase error" because it called nativeOnPurchasesUpdated with
 * responseCode=0 but an EMPTY purchase list when the user cancelled.
 * The C# code saw "success but 0 purchases" → error dialog.
 *
 * NEW HOOK 4: Only forward to native if responseCode == 0 (success).
 * If responseCode != 0 (cancel/error), swallow it (return-void without
 * calling native). This prevents the "Purchase error" dialog.
 *
 * HOOKS:
 *   1. zzbq.onBillingSetupFinished -> force responseCode=0
 *   2. Purchase.isAcknowledged() -> return true
 *   3. Purchase.getPurchaseState() -> return 1 (PURCHASED)
 *   4. zzbq.onPurchasesUpdated -> conditional (swallow errors, forward success)
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
    description = "Skips Google Play Billing and credits IAP items " +
        "(crystal packs, tribe skins) directly. Patches the billing " +
        "bridge to report success, forces Purchase.isAcknowledged() and " +
        "getPurchaseState() to return valid values, and swallows purchase " +
        "errors so no error dialog appears.",
    default = false,
) {
    compatibleWith(POLYTOPIA)

    execute {
        // ============================================================
        // HOOK 1: zzbq.onBillingSetupFinished -> force success
        // ============================================================
        UnityBillingSetupFingerprint.method.addInstructions(0, """
            iget-wide v0, p0, Lcom/android/billingclient/api/zzbq;->zza:J
            const/4 v2, 0x0
            const-string v3, ""
            invoke-static {v2, v3, v0, v1}, Lcom/android/billingclient/api/zzbq;->nativeOnBillingSetupFinished(ILjava/lang/String;J)V
            return-void
        """.trimIndent())


        // ============================================================
        // HOOK 2: Purchase.isAcknowledged() -> return true
        // ============================================================
        PurchaseIsAcknowledgedFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            return v0
        """.trimIndent())


        // ============================================================
        // HOOK 3: Purchase.getPurchaseState() -> return 1 (PURCHASED)
        // ============================================================
        PurchaseGetStateFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            return v0
        """.trimIndent())


        // ============================================================
        // HOOK 4: zzbq.onPurchasesUpdated -> conditional (FIX)
        // ============================================================
        // FIX: Only forward to native if responseCode == 0 (success).
        // If responseCode != 0 (cancel/error), swallow it (return-void
        // without calling native). This prevents "Purchase error".
        //
        // Logic:
        //   v0 = BillingResult.getResponseCode()
        //   if (v0 == 0) goto :continue  → original code runs
        //   return-void                  → swallow error
        //   :continue
        //   [original code starts here]
        // ============================================================
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
