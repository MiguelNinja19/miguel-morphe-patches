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
 *   Uses addInstructions at index 0 to call nativeOnBillingSetupFinished
 *   with success code. Original code becomes dead code.
 *   (Cannot use returnEarly() here because we need to CALL the native
 *    method with specific args, not just return.)
 *
 * HOOK 2: Purchase.isAcknowledged() -> returnEarly(true)
 *   Uses the morphe-ai returnEarly(true) API. The C# code checks
 *   isAcknowledged() to verify a purchase is valid. Forcing true makes
 *   every purchase look acknowledged.
 *
 * HOOK 3: Purchase.getPurchaseState() -> return 1 (PURCHASED)
 *   Uses addInstructions with const/4 + return. Forces state to 1
 *   (PurchaseState.PURCHASED) so the C# code thinks every purchase
 *   completed successfully.
 *
 * HOOK 4: BillingClientImpl.launchBillingFlow -> fake purchase
 *   The big gun. Extracts the SKU from BillingFlowParams, builds a
 *   fake Purchase JSON, creates a Purchase object, and calls
 *   nativeOnPurchasesUpdated(0, "", [fakePurchase]) directly.
 *   This credits the purchase without ever contacting Google Play.
 *
 * Pattern reference:
 *   - morphe-ai/patterns/billing-bypass-patterns.md (Google Play Billing)
 *   - morphe-ai/patcher-apis.md (returnEarly, addInstructions)
 *   - morphe-ai/patch-examples.md (Pattern 3: Return Value Override)
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
        // Pattern: Google Play Billing — override BillingResult.
        // We replace the method body to call nativeOnBillingSetupFinished
        // with responseCode=0 (success) and the original native pointer.
        //
        // Cannot use returnEarly() because we need to CALL the native
        // method with specific args.
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
        // HOOK 2: Purchase.isAcknowledged() -> returnEarly(true)
        // ============================================================
        // Pattern: morphe-ai returnEarly(true) — simplest way to patch.
        // The C# code calls isAcknowledged() to check if a purchase is
        // valid. Forcing true makes every purchase look acknowledged.
        // ============================================================
        PurchaseIsAcknowledgedFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            return v0
        """.trimIndent())


        // ============================================================
        // HOOK 3: Purchase.getPurchaseState() -> return 1 (PURCHASED)
        // ============================================================
        // Pattern: morphe-ai addInstructions + const/4 + return.
        // PurchaseState: 0=UNSPECIFIED, 1=PURCHASED, 2=PENDING.
        // Forcing 1 makes the C# code think every purchase completed.
        // ============================================================
        PurchaseGetStateFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            return v0
        """.trimIndent())


        // ============================================================
        // HOOK 4: zzbq.onPurchasesUpdated -> force success
        // ============================================================
        // Pattern: Google Play Billing — override BillingResult.
        // If the game calls onPurchasesUpdated from somewhere else
        // (e.g. queryPurchasesAsync), force responseCode=0 and pass
        // through the purchases array.
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
        // The big gun. Pattern: method injection with complex smali.
        //
        // Flow:
        //   p0 = this (BillingClientImpl)
        //   p1 = Activity
        //   p2 = BillingFlowParams (contains the SKU)
        //
        //   1. Extract SKU: BillingFlowParams.zzk() -> List
        //      -> ProductDetailsParams.zza() -> ProductDetails
        //      -> getProductId() -> String SKU
        //   2. Build Purchase JSON via String.format
        //   3. new Purchase(json, "")
        //   4. new Purchase[1] with the fake purchase
        //   5. zzbq.nativeOnPurchasesUpdated(0, "", array)
        //   6. Return BillingResult with responseCode=0 (success)
        //
        // Original code (Play Store call) becomes dead code because
        // we return early.
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
            check-cast v0, Lcom/android/billingclient/api/BillingFlowParams$ProductDetailsParams;

            invoke-virtual {v0}, Lcom/android/billingclient/api/BillingFlowParams$ProductDetailsParams;->zza()Lcom/android/billingclient/api/ProductDetails;
            move-result-object v0
            if-eqz v0, :return_success

            invoke-virtual {v0}, Lcom/android/billingclient/api/ProductDetails;->getProductId()Ljava/lang/String;
            move-result-object v0
            if-eqz v0, :return_success

            # --- Step 2: Build Purchase JSON via String.format ---

            const-string v1, "{\"productId\":\"%s\",\"purchaseToken\":\"polytopia_mod\",\"packageName\":\"air.com.midjiwan.polytopia\",\"purchaseState\":1,\"purchaseTime\":
