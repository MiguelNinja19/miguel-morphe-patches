package com.ivanaf.climbamiypfree.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * GooglePlayBilling.GPBilling_Purchase_GetState(String)D
 * Returns purchase state. Game calls this with purchaseToken to check
 * if a product was purchased. We patch to return 13001 (PURCHASED) when
 * called with "climbnoads".
 *
 * Smali signature:
 *   .method public GPBilling_Purchase_GetState(Ljava/lang/String;)D
 */
object PurchaseGetStateFingerprint : Fingerprint(
    definingClass = "Lcom/IvanAF/ClimbAMIYPfree/GooglePlayBilling;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "D",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/IvanAF/ClimbAMIYPfree/GooglePlayBillingService;",
            name = "GetPurchases",
        ),
    )
)

/**
 * GooglePlayBillingService.GetPurchases()Ljava/util/Map;
 * Returns m_purchaseRequests HashMap. We hook to inject fake Purchase
 * with purchaseToken="climbnoads".
 */
object GetPurchasesFingerprint : Fingerprint(
    definingClass = "Lcom/IvanAF/ClimbAMIYPfree/GooglePlayBillingService;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Ljava/util/Map;",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/IvanAF/ClimbAMIYPfree/GooglePlayBillingService;",
            name = "m_purchaseRequests",
        ),
    )
)

/**
 * GameMaker RunnerBillingSecurity.verifyPurchase(String, String)Z
 * Verifies purchase signature. Patch to always return true.
 */
object VerifyPurchaseFingerprint : Fingerprint(
    definingClass = "Lcom/IvanAF/ClimbAMIYPfree/RunnerBillingSecurity;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/util/Log;",
            name = "e",
        ),
    )
)
