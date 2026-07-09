/*
 * Copyright 2025 Miguel's Patches
 * https://github.com/MiguelNinja19/miguel-morphe-patches
 *
 * Fingerprints for Anger of Stick 5 : Zombie.
 *
 * The game is Cocos2d-x with C++ game logic in libMyGame.so, but ALL
 * Google Play Billing flow is handled in Java by
 * org.cocos2dx.cpp.AppActivity. The C++ side calls purchase(int i)
 * via JNI, and AppActivity calls back into C++ via nativeOnSuccess
 * (String productId, boolean isConsumable) when a purchase succeeds.
 *
 * This means we can patch the Java side to fake purchases without
 * touching the C++ code.
 */

package jpark.aos5.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * AppActivity.purchase(int i) -> V
 *
 * Called by the C++ game engine via JNI when the user taps a buy
 * button. The int parameter is an index into the static
 * purchaseItemIDs ArrayList (which contains the 22 IAP SKUs like
 * "aos5.g001", "aos5.j001", etc).
 *
 * Original body:
 *   1. Check isBillingConnected, fail if not
 *   2. Get SKU from purchaseItemIDs[i]
 *   3. Store SKU in mProductID
 *   4. Call launchPurchaseFlow(SKU) which opens Google Play
 *
 * The patch replaces steps 1+4 to skip Google Play entirely and
 * call nativeOnSuccess(SKU, isConsumable) directly - the C++ engine
 * then credits the gem/coin pack as if the purchase had succeeded.
 *
 * Anchor: the unique call to launchPurchaseFlow(String) - this is
 * only invoked from purchase(int), so it uniquely identifies the
 * method.
 */
object PurchaseFingerprint : Fingerprint(
    definingClass = "Lorg/cocos2dx/cpp/AppActivity;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        methodCall(
            definingClass = "Lorg/cocos2dx/cpp/AppActivity;",
            name = "launchPurchaseFlow",
            parameters = listOf("Ljava/lang/String;"),
            returnType = "V",
        ),
    )
)

/**
 * AppActivity.setRestore() -> V
 *
 * Called by the C++ engine on app startup to restore previous
 * purchases. It creates a new BillingClient, connects to Google
 * Play, then queries purchase history. If no purchases are found,
 * it calls nativeOnRestored("null", -1) which the C++ side
 * interprets as "nothing to restore" - but the loading UI sometimes
 * gets stuck waiting for the query to complete.
 *
 * We make this method a no-op so the game never tries to contact
 * Google Play on startup, preventing the loading-screen loop.
 *
 * Anchor: the call to BillingClient.newBuilder() - this method
 * creates a fresh BillingClient specifically for the restore flow.
 */
object SetRestoreFingerprint : Fingerprint(
    definingClass = "Lorg/cocos2dx/cpp/AppActivity;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/android/billingclient/api/BillingClient;",
            name = "newBuilder",
            returnType = "Lcom/android/billingclient/api/BillingClient\$Builder;",
        ),
    )
)

/**
 * AppActivity.restorePurchases() -> V
 *
 * Same as setRestore but called from a different code path. We
 * make it a no-op too for the same reason.
 */
object RestorePurchasesFingerprint : Fingerprint(
    definingClass = "Lorg/cocos2dx/cpp/AppActivity;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/android/billingclient/api/BillingClient;",
            name = "newBuilder",
            returnType = "Lcom/android/billingclient/api/BillingClient\$Builder;",
        ),
    )
)
