/*
 * Free In-App Purchases patch for Anger of Stick 5 : Zombie.
 *
 * KEY DISCOVERY from C++ disassembly:
 * - nativeOnSuccess calls vtable[0x550] → CREDITS item, does NOT close screen
 * - nativeOnFailure calls vtable[0x568] → CLOSES screen (same as onCanceled)
 * - vtable[0x568] also shows an error dialog, BUT the message comes from Java
 * - If we pass EMPTY string as the message, the dialog might not show
 *
 * STRATEGY:
 * 1. Call nativeOnSuccess(sku, true) → credits the item
 * 2. Call nativeOnFailure(sku, "") → closes "Contacting..." screen
 *    (empty message hopefully means no/blank error dialog)
 *
 * In the normal unpatched flow (no billing connected), nativeOnFailure
 * IS what closes the Contacting screen. nativeOnSuccess alone doesn't.
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
        "coin packs, starter packs) directly. Calls nativeOnFailure with empty " +
        "message after nativeOnSuccess to close the Contacting screen. Also " +
        "disables the startup purchase-restore flow.",
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
                
                # Step 2: Close the "Contacting..." screen
                # nativeOnFailure(sku, "") → C++ resets purchase state and closes UI
                # Empty string message = hopefully no error dialog
                const-string v0, ""
                invoke-static {p1, v0}, Lorg/cocos2dx/cpp/AppActivity;->nativeOnFailure(Ljava/lang/String;Ljava/lang/String;)V
                
                # Return - skip original BillingClient code
                return-void
            """.trimIndent()
        )

        // Disable restore-purchases on startup
        SetRestoreFingerprint.method.addInstructions(0, "return-void")
        RestorePurchasesFingerprint.method.addInstructions(0, "return-void")
    }
}
