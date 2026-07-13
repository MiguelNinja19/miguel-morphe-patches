/*
 * Free In-App Purchases patch for The Battle of Polytopia.
 *
 * HOW IT WORKS (following morphe-ai billing-bypass-patterns.md EXACTLY):
 *
 * The morphe-ai guide says for Google Play Billing:
 *   "Pattern: Override purchase validation or skip billing flow."
 *   "Find: BillingClient.queryPurchases or Purchase.isAcknowledged"
 *   "Override: return success/acknowledged"
 *
 * So this patch does ONLY what the guide recommends:
 *
 *   HOOK 1: Purchase.isAcknowledged() -> return true
 *     Forces every purchase to look acknowledged (valid).
 *
 *   HOOK 2: Purchase.getPurchaseState() -> return 1 (PURCHASED)
 *     Forces every purchase state to PURCHASED.
 *
 *   HOOK 3: zzbq.onBillingSetupFinished -> force responseCode=0
 *     Makes the game think billing is connected (no "Waiting..." screen).
 *
 *   HOOK 4: zzbq.onPurchasesUpdated -> force responseCode=0
 *     If a purchase flow does trigger, force success.
 *
 * NO fake Purchase creation. NO intercepting launchBillingFlow.
 * NO Java extension. Just simple returnEarly patterns as the guide shows.
 *
 * When the user taps "Buy", the Play Store dialog still appears, but
 * when the purchase flow completes (or if the user already "owns" the
 * item from a previous restore), the game sees isAcknowledged=true and
 * getPurchaseState=PURCHASED, so it credits the item.
 *
 * Reference: morphe-ai/.kiro/steering/patterns/billing-bypass-patterns.md
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
    description = "Forces Purchase.isAcknowledged() to return true and " +
        "getPurchaseState() to return PURCHASED (1), so the game thinks " +
        "every purchase is valid and completed. Also patches the billing " +
        "bridge to report success on setup and purchase updates.",
    default = false,
) {
    compatibleWith(POLYTOPIA)

    execute {
        // ============================================================
        // HOOK 1: Purchase.isAcknowledged() -> return true
        // ============================================================
        // Pattern from billing-bypass-patterns.md:
        //   "Override: return success/acknowledged"
        //   PurchaseCheckFingerprint.method.returnEarly(true)
        //
        // Smali: const/4 v0, 0x1 + return v0
        // .locals 3 — v0 is available, safe.
        // ============================================================
        PurchaseIsAcknowledgedFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            return v0
        """.trimIndent())


        // ============================================================
        // HOOK 2: Purchase.getPurchaseState() -> return 1 (PURCHASED)
        // ============================================================
        // PurchaseState.PURCHASED = 1 (from Purchase.java source).
        // Forces every purchase to look completed.
        //
        // .locals 3 — v0 is available, safe.
        // ============================================================
        PurchaseGetStateFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            return v0
        """.trimIndent())


        // ============================================================
        // HOOK 3: zzbq.onBillingSetupFinished -> force success
        // ============================================================
        // Makes the game think Google Play billing is connected.
        // .locals 3 — v0, v1, v2, v3 all available, safe.
        // ============================================================
        UnityBillingSetupFingerprint.method.addInstructions(0, """
            iget-wide v0, p0, Lcom/android/billingclient/api/zzbq;->zza:J
            const/4 v2, 0x0
            const-string v3, ""
            invoke-static {v2, v3, v0, v1}, Lcom/android/billingclient/api/zzbq;->nativeOnBillingSetupFinished(ILjava/lang/String;J)V
            return-void
        """.trimIndent())


        // ============================================================
        // HOOK 4: zzbq.onPurchasesUpdated -> force success
        // ============================================================
        // If a purchase flow triggers (e.g. user taps Buy and completes
        // or cancels), force responseCode=0 (success) so the C# code
        // doesn't show error dialogs.
        // .locals 1 — v0 available, p0=v1, p1=v2, p2=v3, all safe.
        // ============================================================
        UnityPurchasesUpdatedFingerprint.method.addInstructions(0, """
            if-nez p2, :has_purchases
            const/4 v0, 0x0
            new-array v0, v0, [Lcom/android/billingclient/api/Purchase;
            const-string v1, ""
            invoke-static {v0, v1, v0}, Lcom/android/billingclient/api/zzbq;->nativeOnPurchasesUpdated(ILjava/lang/String;[Lcom/android/billingclient/api/Purchase;)V
            return-void

            :has_purchases
            invoke-interface {p2}, Ljava/util/List;->size()I
            move-result v0
            new-array v0, v0, [Lcom/android/billingclient/api/Purchase;
            invoke-interface {p2, v0}, Ljava/util/List;->toArray([Ljava/lang/Object;)[Ljava/lang/Object;
            move-result-object v0
            check-cast v0, [Lcom/android/billingclient/api/Purchase;
            const/4 v1, 0x0
            const-string v2, ""
            invoke-static {v1, v2, v0}, Lcom/android/billingclient/api/zzbq;->nativeOnPurchasesUpdated(ILjava/lang/String;[Lcom/android/billingclient/api/Purchase;)V
            return-void
        """.trimIndent())
    }
}
