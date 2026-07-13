/*
 * Free In-App Purchases patch for The Battle of Polytopia.
 *
 * HOW IT WORKS (following morphe-ai billing-bypass patterns):
 *
 *   HOOK 1: zzbq.onBillingSetupFinished -> force responseCode=0
 *   HOOK 2: Purchase.isAcknowledged() -> return true
 *   HOOK 3: Purchase.getPurchaseState() -> return 1 (PURCHASED)
 *   HOOK 4: zzbq.onPurchasesUpdated -> force responseCode=0 (safety net)
 *   HOOK 5: BillingClientImpl.launchBillingFlow -> fake purchase
 *
 * IMPORTANT (register safety):
 *   launchBillingFlow has .locals 28, so p2 = v30 (above v15 limit).
 *   invoke-virtual/invoke-static only accept v0-v15. We MUST move p2
 *   to a low register (v0) before using it. Same for return-object
 *   with BillingResult.Builder chain.
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
        // .locals 3, so p0=v3, p1=v4. All < 15, safe for invoke-static.
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
        // HOOK 4: zzbq.onPurchasesUpdated -> force success
        // ============================================================
        // .locals 1, so p0=v1, p1=v2, p2=v3. All < 15, safe.
        // We use v0 (local) + p2 (List) for the array conversion.
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


        // ============================================================
        // HOOK 5: BillingClientImpl.launchBillingFlow -> fake purchase
        // ============================================================
        // CRITICAL: This method has .locals 28, so:
        //   p0 = v28 (this)
        //   p1 = v29 (Activity)
        //   p2 = v30 (BillingFlowParams)  <-- ABOVE v15!
        //
        // invoke-virtual/invoke-static only accept v0-v15. So we MUST
        // move p2 to v0 at the start, then use v0 everywhere.
        //
        // We also use v0-v6 (all < 15) for the rest of the logic.
        // ============================================================
        LaunchBillingFlowFingerprint.method.addInstructions(0, """
            # Move p2 (v30) to v0 so we can use it in invoke-virtual
            move-object/from16 v0, p2

            # --- Step 1: Extract SKU from BillingFlowParams ---
            invoke-virtual {v0}, Lcom/android/billingclient/api/BillingFlowParams;->zzk()Ljava/util/List;
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
