/*
 * Free In-App Purchases patch for Hunter Assassin.
 *
 * The game uses Rovio Beacon SDK for billing. When a purchase is
 * requested, GooglePlayBillingProvider.startInAppPurchase() calls
 * launchBillingFlow() which opens Google Play.
 *
 * This patch intercepts the lambda that calls launchBillingFlow,
 * and instead calls callOnPurchaseFailed() with PurchaseStatus.SUCCEEDED.
 *
 * Despite the name "callOnPurchaseFailed", it just calls the listener's
 * onPurchase(status, sku, ...) with whatever status you pass.
 * SUCCEEDED has ordinal 0, which the C++ engine interprets as success.
 *
 * No server validation exists - the purchase result is processed
 * entirely client-side via JNI callback.
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
        "successful immediately. When you tap buy, the item is granted " +
        "without payment or Play Store popup.",
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
                
                # Get PurchaseStatus.SUCCEEDED (ordinal = 0)
                sget-object v1, Lcom/rovio/beacon/billing/PurchaseStatus;->SUCCEEDED:Lcom/rovio/beacon/billing/PurchaseStatus;
                
                # Call callOnPurchaseFailed(listener, sku, SUCCEEDED, 0)
                # Despite the name, this calls listener.onPurchase(0, sku, "", "", "", 0)
                # which means status=SUCCEEDED, sku=p1, empty strings, 0
                const/4 v2, 0x0
                invoke-static {v0, p1, v1, v2}, Lcom/rovio/beacon/billing/GooglePlayBillingProviderUtils;->callOnPurchaseFailed(Lcom/rovio/beacon/billing/GooglePlayBillingListener;Ljava/lang/String;Lcom/rovio/beacon/billing/PurchaseStatus;I)V
                
                # Return immediately - skip launchBillingFlow
                return-void
            """.trimIndent()
        )
    }
}
