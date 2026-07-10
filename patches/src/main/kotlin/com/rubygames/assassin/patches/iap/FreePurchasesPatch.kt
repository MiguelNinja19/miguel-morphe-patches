/*
 * Free In-App Purchases patch for Hunter Assassin.
 *
 * FIX: Register layout was wrong in previous version.
 * The method has .locals 2, so:
 *   v0, v1 = locals
 *   v2 = p0 = this
 *   v3 = p1 = sku (String)
 *   v4 = p2 = ProductDetails
 *   v5 = p3 = String
 *   v6 = p4 = boolean
 *
 * invoke-interface/range {v0 .. v6} needs:
 *   v0 = listener
 *   v1 = status (0)
 *   v2 = sku
 *   v3 = token
 *   v4 = json
 *   v5 = signature
 *   v6 = responseCode
 *
 * So we must move sku from v3 to v2 AFTER getting the listener from v2.
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
                # v0 = listener (from p0 which is v2 = this)
                iget-object v0, p0, Lcom/rovio/beacon/billing/GooglePlayBillingProvider;->listener:Lcom/rovio/beacon/billing/GooglePlayBillingListener;
                
                # v1 = 0 (status = SUCCEEDED)
                const/4 v1, 0x0
                
                # v2 = sku (move from v3/p1, overwrites p0/this which we already used)
                move-object v2, p1
                
                # v3 = "fake_token" (overwrites p1/sku which we already moved)
                const-string v3, "fake_token"
                
                # v4 = originalJson (overwrites p2/ProductDetails, not needed)
                const-string v4, "{\"orderId\":\"fake\",\"productId\":\"fake\",\"purchaseTime\":0,\"purchaseState\":0,\"acknowledged\":false}"
                
                # v5 = "fake_sig" (overwrites p3, not needed)
                const-string v5, "fake_sig"
                
                # v6 = 0 (overwrites p4, not needed)
                const/4 v6, 0x0
                
                # Call listener.onPurchase(0, sku, "fake_token", json, "fake_sig", 0)
                invoke-interface/range {v0 .. v6}, Lcom/rovio/beacon/billing/GooglePlayBillingListener;->onPurchase(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V
                
                # Return immediately - skip launchBillingFlow
                return-void
            """.trimIndent()
        )
    }
}
