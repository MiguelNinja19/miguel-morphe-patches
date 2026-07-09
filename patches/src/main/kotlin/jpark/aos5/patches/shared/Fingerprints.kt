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
