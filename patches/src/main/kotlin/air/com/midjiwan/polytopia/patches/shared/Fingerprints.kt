package air.com.midjiwan.polytopia.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Fingerprint for zzbq.onBillingSetupFinished(BillingResult).
 *
 * zzbq is the Unity IL2CPP billing bridge class. It implements multiple
 * Google Play Billing listener interfaces and forwards events to native
 * IL2CPP code via static native methods.
 *
 * Smali signature:
 *   .method public final onBillingSetupFinished(Lcom/android/billingclient/api/BillingResult;)V
 *     invoke-virtual {p1}, Lcom/android/billingclient/api/BillingResult;->getResponseCode()I
 *     invoke-static {v0, p1, v1, v2}, Lcom/android/billingclient/api/zzbq;->nativeOnBillingSetupFinished(ILjava/lang/String;J)V
 *
 * We hook this to force responseCode=0 (success) so the game thinks
 * Google Play billing is connected.
 */
object UnityBillingSetupFingerprint : Fingerprint(
    definingClass = "Lcom/android/billingclient/api/zzbq;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Lcom/android/billingclient/api/BillingResult;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/android/billingclient/api/BillingResult;",
            name = "getResponseCode",
        ),
        methodCall(
            definingClass = "Lcom/android/billingclient/api/zzbq;",
            name = "nativeOnBillingSetupFinished",
            parameters = listOf("I", "Ljava/lang/String;", "J"),
            returnType = "V",
        ),
    )
)

/**
 * Fingerprint for zzbq.onPurchasesUpdated(BillingResult, List<Purchase>).
 *
 * Called when a purchase flow completes (or fails). Forwards the result
 * to nativeOnPurchasesUpdated(responseCode, debugMessage, Purchase[]).
 *
 * Smali signature:
 *   .method public final onPurchasesUpdated(Lcom/android/billingclient/api/BillingResult;Ljava/util/List;)V
 *     invoke-interface {p2}, Ljava/util/List;->toArray([Ljava/lang/Object;)[Ljava/lang/Object;
 *     invoke-static {v0, v1, p2}, Lcom/android/billingclient/api/zzbq;->nativeOnPurchasesUpdated(...)
 */
object UnityPurchasesUpdatedFingerprint : Fingerprint(
    definingClass = "Lcom/android/billingclient/api/zzbq;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Lcom/android/billingclient/api/BillingResult;",
        "Ljava/util/List;"
    ),
    filters = listOf(
        methodCall(
            definingClass = "Ljava/util/List;",
            name = "toArray",
        ),
        methodCall(
            definingClass = "Lcom/android/billingclient/api/zzbq;",
            name = "nativeOnPurchasesUpdated",
            parameters = listOf("I", "Ljava/lang/String;", "[Lcom/android/billingclient/api/Purchase;"),
            returnType = "V",
        ),
    )
)

/**
 * Fingerprint for Purchase.isAcknowledged().
 *
 * Returns true if the purchase has been acknowledged. We hook this to
 * always return true so the C# code thinks every purchase is acked.
 *
 * Smali signature:
 *   .method public isAcknowledged()Z
 *     .locals 3
 *     const-string v1, "acknowledged"
 *     invoke-virtual {v0, v1, v2}, Lorg/json/JSONObject;->optBoolean(Ljava/lang/String;Z)Z
 *
 * This follows the morphe-ai billing-bypass-patterns.md Google Play
 * Billing pattern: "Find: Purchase.isAcknowledged, Override: return
 * success/acknowledged"
 */
object PurchaseIsAcknowledgedFingerprint : Fingerprint(
    definingClass = "Lcom/android/billingclient/api/Purchase;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lorg/json/JSONObject;",
            name = "optBoolean",
        ),
    )
)

/**
 * Fingerprint for Purchase.getPurchaseState().
 *
 * Returns the purchase state (0=UNSPECIFIED, 1=PURCHASED, 2=PENDING).
 * We hook this to always return 1 (PURCHASED).
 *
 * Smali signature:
 *   .method public getPurchaseState()I
 *     .locals 3
 *     const-string v1, "purchaseState"
 *     invoke-virtual {v0, v1, v2}, Lorg/json/JSONObject;->optInt(Ljava/lang/String;I)I
 */
object PurchaseGetStateFingerprint : Fingerprint(
    definingClass = "Lcom/android/billingclient/api/Purchase;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lorg/json/JSONObject;",
            name = "optInt",
        ),
    )
)
