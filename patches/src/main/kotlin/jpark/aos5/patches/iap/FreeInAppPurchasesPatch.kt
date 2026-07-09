/*
 * Free In-App Purchases patch for Anger of Stick 5 : Zombie.
 *
 * NEW STRATEGY: "Fire and let it fall through"
 *
 * Instead of intercepting launchPurchaseFlow with return-void, we
 * add nativeOnSuccess at the BEGINNING but DON'T return. The original
 * code continues to run:
 *
 * 1. Our code: nativeOnSuccess(sku, true) → credits item
 * 2. Original code: productDetailsMap.get(sku) → null (no billing)
 * 3. Original code: nativeOnFailure(sku, "") → original failure path
 * 4. Original code: return-void
 *
 * The nativeOnFailure is called by the ORIGINAL code path (not by us),
 * which might set up the right context for the C++ to close the screen.
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
        "coin packs, starter packs) directly. Lets the original failure path " +
        "run after crediting to close the Contacting screen naturally. Also " +
        "disables the startup purchase-restore flow.",
    default = true,
) {
    compatibleWith(ANGER_OF_STICK_5)

    execute {
        val method = LaunchPurchaseFlowFingerprint.method
        method.addInstructions(
            0,
            """
                # Credit the item FIRST
                # nativeOnSuccess(sku, true) → C++ credits gems/coins
                const/4 v0, 0x1
                invoke-static {p1, v0}, Lorg/cocos2dx/cpp/AppActivity;->nativeOnSuccess(Ljava/lang/String;Z)V
                
                # DO NOT return-void! Let the original code continue.
                # Original code will:
                #   1. productDetailsMap.get(sku) → null (no billing)
                #   2. Jump to :cond_0
                #   3. Call nativeOnFailure(sku, "") → original failure path
                #   4. return-void
                #
                # The nativeOnFailure from the ORIGINAL code path should
                # properly close the Contacting screen because it goes
                # through the normal C++ state machine flow.
            """.trimIndent()
        )

        // Disable restore-purchases on startup
        SetRestoreFingerprint.method.addInstructions(0, "return-void")
        RestorePurchasesFingerprint.method.addInstructions(0, "return-void")
    }
}
