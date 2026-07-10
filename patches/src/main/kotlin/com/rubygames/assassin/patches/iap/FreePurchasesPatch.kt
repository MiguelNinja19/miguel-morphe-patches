/*
 * Free In-App Purchases patch for Hunter Assassin.
 *
 * Previous version used callOnPurchaseFailed which passes empty strings
 * for receipt data. The C++ validates these and shows "Something went wrong!".
 *
 * This version calls listener.onPurchase DIRECTLY with non-empty fake data:
 *   status = 0 (SUCCEEDED)
 *   sku = p1 (from method parameter)
 *   token = "fake_token"
 *   originalJson = valid-looking JSON
 *   signature = "fake_sig"
 *   responseCode = 0
 *
 * The C++ receives non-empty receipt data and processes it as success.
 */

package com.rubygames.assassin.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.rubygames.assassin.patches.shared.Constants.HUNTER_ASSASSIN
import com.rubygames.assassin.patches.shared.StartPurchaseFlowFingerprint

@Suppress("unused")
val freePurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips Google Play Billing and reports the purchase as " +
        "successful immediately with fake receipt data. When you tap buy, " +
        "the item is granted without payment or Play Store popup.",
    default = true,
) {
    compatibleWith(HUNTER_ASSASSIN)

    execute {
        val method = StartPurchaseFlowFingerprint.method
        method.addInstructions(
            0,
            """
                # Get the listener from this.listener
                iget-object v0, p0, Lcom/rovio/beacon/billing/GooglePlayBillingProvider;->listener:Lcom/rovio/beacon/billing/GooglePlayBillingListener;
                
                # v1 = status = 0 (SUCCEEDED)
                const/4 v1, 0x0
                
                # v2 = sku = p1 (already the product ID)
                # p1 is already the SKU string
                
                # v3 = token = "fake_token"
                const-string v3, "fake_token"
                
                # v4 = originalJson = valid-looking JSON
                const-string v4, "{\"orderId\":\"fake\",\"productId\":\"fake\",\"purchaseTime\":0,\"purchaseState\":0,\"acknowledged\":false}"
                
                # v5 = signature = "fake_sig"
                const-string v5, "fake_sig"
                
                # v6 = responseCode = 0
                const/4 v6, 0x0
                
                # Call listener.onPurchase(0, sku, "fake_token", json, "fake_sig", 0)
                invoke-interface/range {v0 .. v6}, Lcom/rovio/beacon/billing/GooglePlayBillingListener;->onPurchase(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V
                
                # Return immediately - skip launchBillingFlow
                return-void
            """.trimIndent()
        )
    }
}
