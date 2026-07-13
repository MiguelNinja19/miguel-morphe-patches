/*
 * Free In-App Purchases patch for The Battle of Polytopia.
 *
 * HOW IT WORKS (following morphe-ai billing-bypass patterns):
 *
 * Polytopia uses Google Play Billing via the Unity IL2CPP bridge
 * class zzbq. The morphe-ai billing-bypass-patterns guide recommends
 * two approaches for Google Play Billing:
 *
 *   1. Override BillingResult to always return OK (responseCode=0)
 *   2. Override Purchase validation methods (isAcknowledged, getPurchaseState)
 *
 * This patch does BOTH, plus a third safety net:
 *
 * HOOK 1: zzbq.onBillingSetupFinished -> force responseCode=0
 * HOOK 2: Purchase.isAcknowledged() -> returnEarly(true)
 * HOOK 3: Purchase.getPurchaseState() -> return 1 (PURCHASED)
 * HOOK 4: zzbq.onPurchasesUpdated -> force responseCode=0 (safety net)
 * HOOK 5: BillingClientImpl.launchBillingFlow -> fake purchase
 *
 * Pure smali, no extension DEX.
 */

package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA
import air.com.midjiwan.polytopia.patches.shared.UnityBillingSetupFingerprint
import air.com.midjiwan.polytopia.patches.shared.UnityPurchasesUpdatedFingerprint
import air.com.midjiwan.polytopia.patches.shared.LaunchBillingFlowFingerprint
import air.com.midjiwan.polytopia.patches.shared.PurchaseIsAcknowledgedFingerprint
import air.com.midjiwan.polytopia.patches.shared.PurchaseGetStateFingerprint

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips Google Play Billing and credits IAP items " +
        "(tribes, skins, crystal packs) directly. Patches the billing " +
        "bridge to report success, forces Purchase.isAcknowledged() and " +
        "getPurchaseState() to return valid values, and intercepts " +
        "launchBillingFlow to create a fake Purchase with the product ID " +
        "and feed it to the game's native billing callback — no payment, " +
        "no Play Store dialog, instant credit.",
    default = false,
) {
    compatibleWith(POLYTOPIA)

    execute {
        // ============================================================
        // HOOK 1: zzbq.onBillingSetupFinished -> force success
        // ============================================================
        UnityBillingSetupFingerprint.method.addInstructions(0, """
            # p0 = this (zzbq)
            # p1 = BillingResult (ignored - we force success)

            # Get this.zza (the native pointer)
            iget-wide v0, p0, Lcom/android/billingclient/api/zzbq;->zza:J

            # Call nativeOnBillingSetupFinished(0, "", zza)
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
        // HOOK 4: zzbq.onPurchasesUpdated -> force success
        // ============================================================
        UnityPurchasesUpdatedFingerprint.method.addInstructions(0, """
            # p0 = this (zzbq)
            # p1 = BillingResult (ignored)
            # p2 = List<Purchase>

            # If list is null, use empty array
            if-nez p2, :has_purchases
            const/4 v0, 0x0
            new-array v1, v0, [Lcom/android/billingclient/api/Purchase;
            const-string v2, ""
            invoke-static {v0, v2, v1}, Lcom/android/billingclient/api/zzbq;->nativeOnPurchasesUpdated(ILjava/lang/String;[Lcom/android/billingclient/api/Purchase;)V
            return-void

            :has_purchases
            invoke-interface {p2}, Ljava/util/List;->size()I
            move-result v0
            new-array v1, v0, [Lcom/android/billingclient/api/Purchase;
            invoke-interface {p2, v1}, Ljava/util/List;->toArray([Ljava/lang/Object;)[Ljava/lang/Object;
            move-result-object v1
            check-cast v1, [Lcom/android/billingclient/api/Purchase;
            const/4 v2, 0x0
            const-string v3, ""
            invoke-static {v2, v3, v1}, Lcom/android/billingclient/api/zzbq;->nativeOnPurchasesUpdated(ILjava/lang/String;[Lcom/android/billingclient/api/Purchase;)V
            return-void
        """.trimIndent())


        // ============================================================
        // HOOK 5: BillingClientImpl.launchBillingFlow -> fake purchase
        // ============================================================
        // NOTE: ${'$'} is used to escape $ in Kotlin triple-quoted strings.
        // In smali, $ is a literal character (inner class separator).
        // ============================================================
        LaunchBillingFlowFingerprint.method.addInstructions(0, """
            # --- Step 1: Extract SKU from BillingFlowParams (p2) ---

            invoke-virtual {p2}, Lcom/android/billingclient/api/BillingFlowParams;->zzk()Ljava/util/List;
            move-result-object v0
            if-eqz v0, :return_success

            invoke-interface {v0}, Ljava/util/List;->size()I
            move-result v1
            if-eqz v1, :return_success

            const/4 v1, 0x0
            invoke-interface {v0, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;
            move-result-object v0
            check-cast v0, Lcom/android/billingclient/api/BillingFlowParams${'$'}ProductDetailsParams;

            invoke-virtual {v0}, Lcom/android/billingclient/api/BillingFlowParams${'$'}ProductDetailsParams;->zza()Lcom/android/billingclient/api/ProductDetails;
            move-result-object v0
            if-eqz v0, :return_success

            invoke-virtual {v0}, Lcom/android/billingclient/api/ProductDetails;->getProductId()Ljava/lang/String;
            move-result-object v0
            if-eqz v0, :return_success

            # --- Step 2: Build Purchase JSON via String.format ---

            const-string v1, "{\"productId\":\"%s\",\"purchaseToken\":\"polytopia_mod\",\"packageName\":\"air.com.midjiwan.polytopia\",\"purchaseState\":1,\"purchaseTime\":1700000000000,\"acknowledged\":true}"
            const/4 v2, 0x1
            new-array v3, v2, [Ljava/lang/Object;
            const/4 v4, 0x0
            aput-object v0, v3, v4
            invoke-static {v1, v3}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;
            move-result-object v0

            # --- Step 3: Create Purchase object ---

            const-string v1, ""
            new-instance v2, Lcom/android/billingclient/api/Purchase;
            invoke-direct {v2, v0, v1}, Lcom/android/billingclient/api/Purchase;-><init>(Ljava/lang/String;Ljava/lang/String;)V

            # --- Step 4: Create Purchase[] array ---

            const/4 v3, 0x1
            new-array v4, v3, [Lcom/android/billingclient/api/Purchase;
            const/4 v5, 0x0
            aput-object v2, v4, v5

            # --- Step 5: Call nativeOnPurchasesUpdated(0, "", array) ---

            const/4 v5, 0x0
            const-string v6, ""
            invoke-static {v5, v6, v4}, Lcom/android/billingclient/api/zzbq;->nativeOnPurchasesUpdated(ILjava/lang/String;[Lcom/android/billingclient/api/Purchase;)V

            # --- Step 6: Return success BillingResult ---

            :return_success
            invoke-static {}, Lcom/android/billingclient/api/BillingResult;->newBuilder()Lcom/android/billingclient/api/BillingResult${'$'}Builder;
            move-result-object v0
            const/4 v1, 0x0
            invoke-virtual {v0, v1}, Lcom/android/billingclient/api/BillingResult${'$'}Builder;->setResponseCode(I)Lcom/android/billingclient/api/BillingResult${'$'}Builder;
            invoke-virtual {v0}, Lcom/android/billingclient/api/BillingResult${'$'}Builder;->build()Lcom/android/billingclient/api/BillingResult;
            move-result-object v0
            return-object v0
        """.trimIndent())
    }
}
