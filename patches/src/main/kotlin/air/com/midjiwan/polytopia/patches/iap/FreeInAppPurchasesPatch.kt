/*
 * Free In-App Purchases patch for The Battle of Polytopia.
 *
 * HOW IT WORKS (following morphe-ai billing-bypass patterns):
 *
 *   HOOK 1: zzbq.onBillingSetupFinished -> force responseCode=0
 *   HOOK 2: Purchase.isAcknowledged() -> return true
 *   HOOK 3: Purchase.getPurchaseState() -> return 1 (PURCHASED)
 *   HOOK 4: zzbq.onPurchasesUpdated -> force responseCode=0 (safety net)
 *   HOOK 5: BillingClientImpl.launchBillingFlow -> call Java extension
 *
 * HOOK 5 uses a Java extension (BillingBypassPatch) instead of inline
 * smali because:
 *   - The method has .locals 28 (p2 = v30, above invoke limit)
 *   - Creating Purchase objects in smali is error-prone (JSONException)
 *   - Java gives us try-catch for safety
 *   - Reflection in Java is cleaner than in smali
 *
 * Pattern reference:
 *   - morphe-ai/extension-development.md (when to use extensions)
 *   - morphe-ai/patterns/billing-bypass-patterns.md (Google Play Billing)
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
        "launchBillingFlow to create a fake Purchase via Java extension " +
        "and feed it to the game's native billing callback — no payment, " +
        "no Play Store dialog, instant credit.",
    default = false,
) {
    compatibleWith(POLYTOPIA)

    // Load the extension DEX that contains the billing bypass logic
    extendWith("extensions/extension.mpe")

    execute {
        // ============================================================
        // HOOK 1: zzbq.onBillingSetupFinished -> force success
        // ============================================================
        // .locals 3, p0=v3, p1=v4. All < 15, safe for invoke-static.
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
        // .locals 1, p0=v1, p1=v2, p2=v3. All < 15, safe.
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
        // HOOK 5: BillingClientImpl.launchBillingFlow -> extension
        // ============================================================
        // .locals 28, so p2 = v30 (above v0-v15 invoke limit).
        // Move p2 to v0 first, then call Java extension which handles
        // SKU extraction, Purchase creation, and native callback.
        // All complex logic is in Java (BillingBypassPatch) with
        // try-catch for safety.
        // ============================================================
        LaunchBillingFlowFingerprint.method.addInstructions(0, """
            # Move p2 (v30) to v0 so we can use it in invoke-static
            move-object/from16 v0, p2

            # Call extension: BillingBypassPatch.onLaunchBillingFlow(params)
            invoke-static {v0}, Lair/com/midjiwan/polytopia/extension/BillingBypassPatch;->onLaunchBillingFlow(Ljava/lang/Object;)V

            # Return success BillingResult
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
