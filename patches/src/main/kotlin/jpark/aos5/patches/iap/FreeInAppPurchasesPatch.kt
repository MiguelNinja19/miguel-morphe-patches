/*
 * Free In-App Purchases patch for Anger of Stick 5 : Zombie.
 *
 * STRATEGY: Call nativeOnSuccess (to credit the item) followed by
 * nativeOnCanceled (to close the "Contacting..." UI).
 *
 * The C++ state machine has two separate concerns:
 * 1. Crediting the item → handled by nativeOnSuccess
 * 2. Resetting the purchase state / closing the UI → handled by
 *    nativeOnCanceled or nativeOnFailure
 *
 * When we call nativeOnSuccess directly, it credits the item but
 * does NOT reset the purchase state. The "Contacting..." UI stays
 * because the C++ thinks the purchase is still in progress.
 *
 * Fix: call nativeOnCanceled AFTER nativeOnSuccess. The cancel
 * callback resets the purchase state and closes the UI. It does
 * NOT un-credit the item (in normal flow, cancel means the user
 * didn't pay, so there's nothing to undo).
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
        val method = LaunchPurchaseFlowFingerprint.method
        method.addInstructions(
            0,
            """
                # Step 1: Credit the item
                # nativeOnSuccess(sku, true) → C++ credits gems/coins
                const/4 v0, 0x1
                invoke-static {p1, v0}, Lorg/cocos2dx/cpp/AppActivity;->nativeOnSuccess(Ljava/lang/String;Z)V
                
                # Step 2: Close the "Contacting..." UI
                # nativeOnCanceled(sku) → C++ resets purchase state and hides the loading screen
                # This does NOT un-credit the item (cancel = user didn't pay = nothing to undo)
                invoke-static {p1}, Lorg/cocos2dx/cpp/AppActivity;->nativeOnCanceled(Ljava/lang/String;)V
                
                # Return immediately - skip the original BillingClient code
                return-void
            """.trimIndent()
        )

        // Disable restore-purchases flow on startup
        SetRestoreFingerprint.method.addInstructions(0, "return-void")
        RestorePurchasesFingerprint.method.addInstructions(0, "return-void")
    }
}
