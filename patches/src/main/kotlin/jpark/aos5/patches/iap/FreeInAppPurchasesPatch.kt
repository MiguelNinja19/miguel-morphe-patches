/*
 * Copyright 2025 Miguel's Patches
 * https://github.com/MiguelNinja19/miguel-morphe-patches
 *
 * Free In-App Purchases patch for Anger of Stick 5 : Zombie.
 *
 * The game's billing flow is:
 *   1. C++ game calls AppActivity.purchase(int i) via JNI
 *   2. AppActivity looks up SKU = purchaseItemIDs[i]
 *   3. AppActivity calls launchPurchaseFlow(SKU) -> opens Google Play
 *   4. User pays in Google Play
 *   5. onPurchasesUpdated callback fires
 *   6. handleConsumablePurchase consumes the purchase
 *   7. nativeOnSuccess(SKU, isConsumable) is called -> C++ credits item
 *
 * This patch short-circuits steps 3-6 by replacing the body of
 * purchase(int i) with:
 *   - Get SKU from purchaseItemIDs[i]
 *   - Call isConsumable(SKU)
 *   - Call nativeOnSuccess(SKU, isConsumable) directly
 *
 * The C++ engine receives a "purchase succeeded" notification and
 * credits the gem/coin pack without ever talking to Google Play.
 *
 * This effectively gives:
 *   - Free gem packs (aos5.g001, aos5.g002, aos5.g003)
 *   - Free coin/joker packs (aos5.j001, aos5.j002, aos5.gj001)
 *   - Free starter packs (aos5.sg001-003, aos5.sj001-003, aos5.sgj001-003)
 *   - Free big packs (aos5.ho399, aos5.ht399, aos5.hg599, etc)
 *
 * Note: this works for ALL 22 SKUs in the purchaseItemIDs list, so
 * it covers both "gem packs" and "starter packs" with one patch.
 *
 * Side effect: each time you tap "buy" the item is credited
 * instantly - so this also effectively gives unlimited gems/coins
 * (just keep tapping buy).
 *
 * ADDITIONAL: We also no-op setRestore() and restorePurchases() to
 * prevent the game from contacting Google Play on startup to
 * restore previous purchases. Without this, the loading screen
 * gets stuck in a loop waiting for the (empty) purchase history
 * query to complete.
 */

package jpark.aos5.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import jpark.aos5.patches.shared.Constants.ANGER_OF_STICK_5
import jpark.aos5.patches.shared.PurchaseFingerprint
import jpark.aos5.patches.shared.RestorePurchasesFingerprint
import jpark.aos5.patches.shared.SetRestoreFingerprint

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips Google Play Billing and credits IAP items (gem packs, " +
        "coin packs, starter packs) directly. Tap any buy button in the shop " +
        "and the item is granted instantly without payment. Effectively gives " +
        "unlimited gems and coins since you can 'buy' the largest pack as many " +
        "times as you want. Also disables the startup purchase-restore flow " +
        "to prevent the loading screen from getting stuck.",
    default = true,
) {
    compatibleWith(ANGER_OF_STICK_5)

    execute {
        // =========================================================
        // 1) Free IAP: replace purchase(int i) body
        // =========================================================
        val purchaseMethod = PurchaseFingerprint.method
        purchaseMethod.addInstructions(
            0,
            """
                # Get SKU from purchaseItemIDs.get(p1)
                sget-object v0, Lorg/cocos2dx/cpp/AppActivity;->purchaseItemIDs:Ljava/util/ArrayList;
                invoke-virtual {v0, p1}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Ljava/lang/String;
                
                # Store SKU in mProductID
                sput-object v0, Lorg/cocos2dx/cpp/AppActivity;->mProductID:Ljava/lang/String;
                
                # Call isConsumable(v0) -> Z
                invoke-direct {p0, v0}, Lorg/cocos2dx/cpp/AppActivity;->isConsumable(Ljava/lang/String;)Z
                move-result v1
                
                # Call nativeOnSuccess(v0, v1)
                invoke-static {v0, v1}, Lorg/cocos2dx/cpp/AppActivity;->nativeOnSuccess(Ljava/lang/String;Z)V
                
                # Return immediately
                return-void
            """.trimIndent()
        )

        // =========================================================
        // 2) Disable restore-purchases flow on startup
        // =========================================================
        // setRestore() and restorePurchases() both create a new
        // BillingClient, connect to Google Play, then query purchase
        // history. On a patched APK this query either times out or
        // returns empty, leaving the C++ side waiting for a
        // nativeOnRestored() callback that sometimes leaves the
        // loading UI in a stuck state.
        //
        // We make both methods immediate no-ops by inserting
        // return-void at the very start.

        SetRestoreFingerprint.method.addInstructions(0, "return-void")
        RestorePurchasesFingerprint.method.addInstructions(0, "return-void")
    }
}
