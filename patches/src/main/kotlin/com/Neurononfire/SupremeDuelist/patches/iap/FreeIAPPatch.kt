/*
 * Free In-App Purchases patch for Supreme Duelist Stickman.
 *
 * Hooks BillingClientImpl.launchBillingFlow and zzbq.onPurchasesUpdated
 * to make every purchase succeed instantly without contacting Google Play.
 */

package com.Neurononfire.SupremeDuelist.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.Neurononfire.SupremeDuelist.patches.shared.SUPREME_DUELIST
import com.Neurononfire.SupremeDuelist.patches.shared.LaunchBillingFlowFingerprint
import com.Neurononfire.SupremeDuelist.patches.shared.OnPurchasesUpdatedFingerprint

@Suppress("unused")
val freeIAPPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Makes every IAP purchase succeed instantly without " +
        "contacting Google Play Billing. When the user taps 'Buy' on " +
        "any product (Remove Ads), the purchase is credited immediately.",
    default = true,
) {
    compatibleWith(SUPREME_DUELIST)

    execute {
        // HOOK 1: launchBillingFlow -> call nativeOnPurchasesUpdated(0, "", [])
        LaunchBillingFlowFingerprint.method.addInstructions(0, """
            const/4 v0, 0x0
            new-array v1, v0, [Lcom/android/billingclient/api/Purchase;
            const-string v2, ""
            invoke-static {v0, v2, v1}, Lcom/android/billingclient/api/zzbq;->nativeOnPurchasesUpdated(ILjava/lang/String;[Lcom/android/billingclient/api/Purchase;)V
            invoke-static {}, Lcom/android/billingclient/api/BillingResult;->newBuilder()Lcom/android/billingclient/api/BillingResult${'$'}Builder;
            move-result-object v3
            invoke-virtual {v3, v0}, Lcom/android/billingclient/api/BillingResult${'$'}Builder;->setResponseCode(I)Lcom/android/billingclient/api/BillingResult${'$'}Builder;
            invoke-virtual {v3}, Lcom/android/billingclient/api/BillingResult${'$'}Builder;->build()Lcom/android/billingclient/api/BillingResult;
            move-result-object v0
            return-object v0
        """.trimIndent())

        // HOOK 2: onPurchasesUpdated -> force responseCode=0
        OnPurchasesUpdatedFingerprint.method.addInstructions(0, """
            if-nez p2, :has_purchases
            invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
            move-result-object p2
            :has_purchases
            invoke-interface {p2}, Ljava/util/List;->size()I
            move-result v0
            new-array v1, v0, [Lcom/android/billingclient/api/Purchase;
            invoke-interface {p2, v1}, Ljava/util/List;->toArray([Ljava/lang/Object;)[Ljava/lang/Object;
            move-result-object v2
            check-cast v2, [Lcom/android/billingclient/api/Purchase;
            const/4 v0, 0x0
            const-string v3, ""
            invoke-static {v0, v3, v2}, Lcom/android/billingclient/api/zzbq;->nativeOnPurchasesUpdated(ILjava/lang/String;[Lcom/android/billingclient/api/Purchase;)V
            return-void
        """.trimIndent())
    }
}
