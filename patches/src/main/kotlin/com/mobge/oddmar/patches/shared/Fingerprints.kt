package com.mobge.Oddmar.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Fingerprint for OddmarPurchaseHandler.HasProductBeenPurchased(String, callback).
 *
 * Oddmar uses a custom Java billing wrapper called OddmarPurchaseHandler that
 * the Unity C# code invokes via AndroidJavaObject / reflection. The class
 * string "com.mobge.oddmarbilling.OddmarPurchaseHandler" appears in
 * assets/bin/Data/Managed/Metadata/global-metadata.dat, confirming Unity
 * calls into Java via reflection using the class name.
 *
 * The Unity C# code asks Google Play Billing whether a product has been
 * purchased. The only IAP product in Oddmar is "unlock_all_levels" (also
 * found in global-metadata.dat next to "Purchasing product asynchronously"
 * and "IAP: OnInitialized: PASS").
 *
 * Smali signature (from decompiled APK 0.111):
 *   .method public HasProductBeenPurchased(
 *           Ljava/lang/String;
 *           Lcom/mobge/oddmarbilling/HasProductBeenPurchasedCallback;)V
 *     iget-object v0, p0, Lcom/mobge/oddmarbilling/OddmarPurchaseHandler;->_billingClient:...
 *     invoke-virtual {v0}, Lcom/android/billingclient/api/BillingClient;->isReady()Z
 *     move-result v0
 *     if-nez v0, :cond_0
 *     const/4 p1, 0x0
 *     invoke-interface {p2, p1}, Lcom/mobge/oddmarbilling/HasProductBeenPurchasedCallback;->OnCallback(Z)V
 *     ...
 *
 * We hook this method to immediately call callback.OnCallback(true) and
 * return. This makes the game think "unlock_all_levels" is purchased
 * without ever querying Google Play Billing.
 *
 * The HasProductBeenPurchasedCallback interface is:
 *   .method public abstract OnCallback(Z)V
 */
object HasProductBeenPurchasedFingerprint : Fingerprint(
    definingClass = "Lcom/mobge/oddmarbilling/OddmarPurchaseHandler;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Lcom/mobge/oddmarbilling/HasProductBeenPurchasedCallback;"
    ),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/android/billingclient/api/BillingClient;",
            name = "isReady",
        ),
        methodCall(
            definingClass = "Lcom/mobge/oddmarbilling/HasProductBeenPurchasedCallback;",
            name = "OnCallback",
            parameters = listOf("Z"),
            returnType = "V",
        ),
    )
)

/**
 * Fingerprint for OddmarPurchaseHandler.PurchaseProduct(String, callback).
 *
 * The Unity C# code calls this when the user taps "Buy" on a product
 * (currently only "unlock_all_levels"). Internally it creates a PurchaseCall
 * that queries Google Play Billing's queryProductDetailsAsync and launches
 * the billing flow.
 *
 * Smali signature (from decompiled APK 0.111):
 *   .method public PurchaseProduct(
 *           Ljava/lang/String;
 *           Lcom/mobge/oddmarbilling/PurchaseCallback;)V
 *     iget-object v0, p0, Lcom/mobge/oddmarbilling/OddmarPurchaseHandler;->_billingClient:...
 *     invoke-virtual {v0}, Lcom/android/billingclient/api/BillingClient;->isReady()Z
 *     move-result v0
 *     const/4 v1, 0x0
 *     if-nez v0, :cond_0
 *     invoke-interface {p2, v1}, Lcom/mobge/oddmarbilling/PurchaseCallback;->OnCallback(Z)V
 *     ...
 *
 * The PurchaseCallback interface is:
 *   .method public abstract OnCallback(Z)V
 *
 * We hook this to immediately call callback.OnCallback(true) and return.
 * This credits the purchase WITHOUT contacting Google Play Billing - the
 * user gets the product for free, and the C# code calls ProcessPurchase
 * as if the purchase had succeeded.
 *
 * The result: every IAP "purchase" succeeds instantly. For Oddmar this
 * gives the user a free "unlock_all_levels" purchase. Combined with the
 * UnlockFullGamePatch (which makes the same check return true without
 * needing a purchase), this functions as "free IAP".
 */
object PurchaseProductFingerprint : Fingerprint(
    definingClass = "Lcom/mobge/oddmarbilling/OddmarPurchaseHandler;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Lcom/mobge/oddmarbilling/PurchaseCallback;"
    ),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/android/billingclient/api/BillingClient;",
            name = "isReady",
        ),
        methodCall(
            definingClass = "Lcom/mobge/oddmarbilling/PurchaseCallback;",
            name = "OnCallback",
            parameters = listOf("Z"),
            returnType = "V",
        ),
    )
)

/**
 * Fingerprint for OddmarPurchaseHandler.EnsureAllPurchasesAcknowledged().
 *
 * Called from OnBillingSetupComplete() right after the billing client
 * connects. It queries all purchases and acknowledges them. We hook this
 * to be a no-op so the patch doesn't try to talk to Google Play Billing
 * at startup (which could fail or cause ANRs when the billing client
 * isn't actually connected).
 *
 * Smali signature:
 *   .method public EnsureAllPurchasesAcknowledged()V
 *     sget-object v0, Lcom/mobge/oddmarbilling/OddmarPurchaseHandler;->TAG:...
 *     const-string v1, "Acknowledging all purchases..."
 *     invoke-static {v0, v1}, Landroid/util/Log;->d(...)I
 *     iget-object v0, p0, ...->_billingClient:...
 *     invoke-static {}, Lcom/android/billingclient/api/QueryPurchasesParams;->newBuilder()...
 */
object EnsureAllPurchasesAcknowledgedFingerprint : Fingerprint(
    definingClass = "Lcom/mobge/oddmarbilling/OddmarPurchaseHandler;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/android/billingclient/api/QueryPurchasesParams;",
            name = "newBuilder",
        ),
    )
)
