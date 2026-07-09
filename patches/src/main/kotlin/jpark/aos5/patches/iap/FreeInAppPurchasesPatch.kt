/*
 * Free In-App Purchases patch for Anger of Stick 5 : Zombie.
 *
 * ROOT CAUSE OF "Contacting..." STUCK SCREEN:
 * nativeOnSuccess must be called on the UI thread (not the C++ GL thread)
 * and after a small delay (so the C++ state machine has time to set up
 * the "Contacting..." UI before receiving the callback).
 *
 * SOLUTION:
 * 1. Patch launchPurchaseFlow to post a delayed (500ms) Runnable on the
 *    UI thread instead of calling BillingClient.launchBillingFlow
 * 2. The Runnable (ExternalSyntheticLambda3) calls lambda$billingStart$2
 * 3. Patch lambda$billingStart$2 to call nativeOnSuccess(mProductID, true)
 *    instead of checking billing connection
 *
 * Flow:
 *   C++ calls purchase(int i) -> sets mProductID -> calls launchPurchaseFlow
 *   -> launchPurchaseFlow posts a 500ms delayed Runnable on UI thread -> returns
 *   -> C++ shows "Contacting..." UI (has time to set up)
 *   -> 500ms later, Runnable fires on UI thread
 *   -> calls nativeOnSuccess(mProductID, true) on UI thread
 *   -> C++ credits item AND closes "Contacting..." UI
 */

package jpark.aos5.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import jpark.aos5.patches.shared.BillingStartLambdaFingerprint
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
        // 1) Patch lambda$billingStart$2 to call nativeOnSuccess
        BillingStartLambdaFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Lorg/cocos2dx/cpp/AppActivity;->mProductID:Ljava/lang/String;
                const/4 v1, 0x1
                invoke-static {v0, v1}, Lorg/cocos2dx/cpp/AppActivity;->nativeOnSuccess(Ljava/lang/String;Z)V
                return-void
            """.trimIndent()
        )

        // 2) Patch launchPurchaseFlow to post a delayed Runnable on UI thread
        val method = LaunchPurchaseFlowFingerprint.method
        method.addInstructions(
            0,
            """
                new-instance v0, Landroid/os/Handler;
                invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;
                move-result-object v1
                invoke-direct {v0, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V
                new-instance v1, Lorg/cocos2dx/cpp/AppActivity$$ExternalSyntheticLambda3;
                invoke-direct {v1, p0}, Lorg/cocos2dx/cpp/AppActivity$$ExternalSyntheticLambda3;-><init>(Lorg/cocos2dx/cpp/AppActivity;)V
                const-wide/16 v2, 0x1f4
                invoke-virtual {v0, v1, v2, v3}, Landroid/os/Handler;->postDelayed(Ljava/lang/Runnable;J)Z
                return-void
            """.trimIndent()
        )

        // 3) Disable restore-purchases flow on startup
        SetRestoreFingerprint.method.addInstructions(0, "return-void")
        RestorePurchasesFingerprint.method.addInstructions(0, "return-void")
    }
}
