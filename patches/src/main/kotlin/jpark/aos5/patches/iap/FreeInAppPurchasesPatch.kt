/*
 * Free In-App Purchases patch for Anger of Stick 5 : Zombie.
 *
 * NEW STRATEGY: Instead of patching purchase(int i), we patch
 * launchPurchaseFlow(String sku) - the private method that
 * actually opens Google Play. We replace its body to call
 * nativeOnSuccess(sku, true) directly, skipping Google Play.
 *
 * Why this works better:
 * - purchase(int i) runs its FULL normal flow (sets mProductID,
 *   checks billing connection, etc) before calling launchPurchaseFlow
 * - The C++ state machine is fully set up when launchPurchaseFlow
 *   is called, so it's ready to receive the nativeOnSuccess callback
 * - launchPurchaseFlow is private, so it's only called from purchase()
 * - The original launchPurchaseFlow checks productDetailsMap for the
 *   SKU and calls BillingClient.launchBillingFlow - we replace ALL of
 *   that with a simple nativeOnSuccess call
 */

package jpark.aos5.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import jpark.aos5.patches.shared.Constants.ANGER_OF_STICK_5
import jpark.aos5.patches.shared.LaunchPurchaseFlowFingerprint
import jpark.aos5.patches.shared.RestorePurchasesFingerprint
import jpark.aos5.patches.shared.SetRestoreFingerprint

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips Google Play Billing and credits IAP items (gem packs, " +
        "coin packs, starter packs) directly. Tap any buy button in the shop " +
        "and the item is granted instantly without payment. Also disables the " +
        "startup purchase-restore flow.",
    default = true,
) {
    compatibleWith(ANGER_OF_STICK_5)

    execute {
        // Patch launchPurchaseFlow(String sku) to call nativeOnSuccess
        // instead of BillingClient.launchBillingFlow.
        //
        // Register layout:
        //   p0 = this  (Lorg/cocos2dx/cpp/AppActivity;)
        //   p1 = sku   (Ljava/lang/String;)

        val method = LaunchPurchaseFlowFingerprint.method
        method.addInstructions(
            0,
            """
                # Call nativeOnSuccess(p1, true) directly
                # p1 is the SKU string, already set by purchase()
                const/4 v0, 0x1
                invoke-static {p1, v0}, Lorg/cocos2dx/cpp/AppActivity;->nativeOnSuccess(Ljava/lang/String;Z)V
                
                # Return immediately - skip the original BillingClient code
                return-void
            """.trimIndent()
        )

        // Disable restore-purchases flow
        SetRestoreFingerprint.method.addInstructions(0, "return-void")
        RestorePurchasesFingerprint.method.addInstructions(0, "return-void")
    }
}
