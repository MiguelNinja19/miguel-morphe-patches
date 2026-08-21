package com.Neurononfire.SupremeDuelist.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.Neurononfire.SupremeDuelist.patches.shared.SUPREME_DUELIST
import com.Neurononfire.SupremeDuelist.patches.shared.LaunchBillingFlowFingerprint
import com.Neurononfire.SupremeDuelist.patches.shared.OnPurchasesUpdatedFingerprint

@Suppress("unused")
val freeIapPatch = bytecodePatch(
    name = "Free in-app purchases (optional)",
    description = "Skips Google Play Billing and credits IAP items " +
        "(Remove Ads) directly. OPTIONAL: The other patches already " +
        "give you unlimited coins and no ads.",
    default = false,
) {
    compatibleWith(SUPREME_DUELIST)

    execute {
        OnPurchasesUpdatedFingerprint.method.addInstructions(0, """
            const/4 v0, 0x0
            new-array v1, v0, [Lcom/android/billingclient/api/Purchase;
            const-string v2, ""
            invoke-static {v0, v2, v1}, Lcom/android/billingclient/api/zzbq;->nativeOnPurchasesUpdated(ILjava/lang/String;[Lcom/android/billingclient/api/Purchase;)V
            return-void
        """.trimIndent())

        LaunchBillingFlowFingerprint.method.addInstructions(0, """
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
            const-string v1, "{\"productId\":\"%s\",\"purchaseToken\":\"supreme_mod\",\"packageName\":\"com.Neurononfire.SupremeDuelist\",\"purchaseState\":1,\"purchaseTime\":1700000000000,\"acknowledged\":true}"
            const/4 v2, 0x1
            new-array v3, v2, [Ljava/lang/Object;
            const/4 v4, 0x0
            aput-object v0, v3, v4
            invoke-static {v1, v3}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;
            move-result-object v0
            const-string v1, ""
            new-instance v2, Lcom/android/billingclient/api/Purchase;
            invoke-direct {v2, v0, v1}, Lcom/android/billingclient/api/Purchase;-><init>(Ljava/lang/String;Ljava/lang/String;)V
            const/4 v3, 0x1
            new-array v4, v3, [Lcom/android/billingclient/api/Purchase;
            const/4 v5, 0x0
            aput-object v2, v4, v5
            const/4 v5, 0x0
            const-string v6, ""
            invoke-static {v5, v6, v4}, Lcom/android/billingclient/api/zzbq;->nativeOnPurchasesUpdated(ILjava/lang/String;[Lcom/android/billingclient/api/Purchase;)V
            :return_success
            invoke-static {}, Lcom/android/billingclient/api/BillingResult;->newBuilder()Lcom/android/billingclient/api/BillingResult$Builder;
            move-result-object v0
            const/4 v1, 0x0
            invoke-virtual {v0, v1}, Lcom/android/billingclient/api/BillingResult$Builder;->setResponseCode(I)Lcom/android/billingclient/api/BillingResult$Builder;
            invoke-virtual {v0}, Lcom/android/billingclient/api/BillingResult$Builder;->build()Lcom/android/billingclient/api/BillingResult;
            move-result-object v0
            return-object v0
        """.trimIndent())
    }
}
