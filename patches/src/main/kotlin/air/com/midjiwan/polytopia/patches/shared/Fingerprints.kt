package air.com.midjiwan.polytopia.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Fingerprint for the MAIN entry point of the Polytopia app.
 *
 * MessagingUnityPlayerActivity is declared in AndroidManifest.xml with
 * LAUNCHER intent-filter. Its onCreate runs BEFORE the Unity engine
 * starts, so it's the perfect hook point to write the debug config file
 * before Config.Load() runs in C#.
 *
 * Smali signature:
 *   .method protected onCreate(Landroid/os/Bundle;)V
 *     invoke-super {p0, p1}, Lcom/unity3d/player/UnityPlayerActivity;->onCreate(Landroid/os/Bundle;)V
 */
object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/google/firebase/MessagingUnityPlayerActivity;",
    accessFlags = listOf(AccessFlags.PROTECTED),
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/unity3d/player/UnityPlayerActivity;",
            name = "onCreate",
            parameters = listOf("Landroid/os/Bundle;"),
            returnType = "V",
        ),
    )
)

/**
 * Fingerprint for zzbq.onBillingSetupFinished(BillingResult).
 *
 * zzbq is the Unity IL2CPP billing bridge class. It implements multiple
 * Google Play Billing listener interfaces and forwards events to native
 * IL2CPP code via static native methods.
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
 * This follows the morphe-ai billing-bypass-patterns.md Google Play
 * Billing pattern: "Find: Purchase.isAcknowledged, Override: return
 * success/acknowledged"
 *
 * Smali signature:
 *   .method public isAcknowledged()Z
 *     .locals 3
 *     const-string v1, "acknowledged"
 *     invoke-virtual {v0, v1, v2}, Lorg/json/JSONObject;->optBoolean(Ljava/lang/String;Z)Z
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
