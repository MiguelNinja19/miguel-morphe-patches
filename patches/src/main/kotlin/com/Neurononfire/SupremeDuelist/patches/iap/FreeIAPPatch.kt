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
        // Hook 1: Force onPurchasesUpdated to always report success
        // (responseCode=0 means BILLING_RESPONSE_CODE_OK)
        OnPurchasesUpdatedFingerprint.method.addInstructions(0, """
            const/4 v0, 0x0
            new-array v1, v0, [Lcom/android/billingclient/api/Purchase;
            const-string v2, ""
            invoke-static {v0, v2, v1}, Lcom/android/billingclient/api/zzbq;->nativeOnPurchasesUpdated(ILjava/lang/String;[Lcom/android/billingclient/api/Purchase;)V
            return-void
        """.trimIndent())

        // Hook 2: Make launchBillingFlow return success immediately
        // without contacting Google Play (skips the Play Store dialog).
        // Uses ${'$'} to escape dollar signs in smali inner class names
        // (BillingResult$Builder) inside triple-quoted strings.
        LaunchBillingFlowFingerprint.method.addInstructions(0, """
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
