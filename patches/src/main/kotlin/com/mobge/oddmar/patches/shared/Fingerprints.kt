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

/**
 * Fingerprint for SocialImpl.displayLoginDialogIfNecessary(boolean, ActionListener).
 *
 * Oddmar uses com.mobge.unitygameintegration.SocialImpl to bridge Unity C#
 * social module calls (UnityEngine.SocialPlatforms) to Google Play Games
 * sign-in. The C# class MRSocial.ensureAuthenticate() (found in
 * global-metadata.dat) calls this Java method on every game launch to
 * ensure the user is authenticated with Google Play Games.
 *
 * THE PROBLEM (without this patch):
 *   1. On every app launch, Unity creates a NEW SocialImpl instance
 *      (instance field `i` = false, `d` = false).
 *   2. Unity calls displayLoginDialogIfNecessary(false, listener).
 *   3. Since `d` is false, the method sets `d = true` and calls
 *      private p(useSilent=true, listener).
 *   4. p() checks isAuthenticated() — returns FALSE because `i` is false
 *      on a fresh instance (the cached GoogleSignInAccount from last
 *      session is checked via getAccount(), but is null after the app
 *      process is killed and restarted).
 *   5. p() tries silentSignIn() (async).
 *   6. If silentSignIn() FAILS (which it does every time the cached
 *      account is null or expired), SocialImpl$2.onComplete() calls
 *      private w(listener, signInClient).
 *   7. w() OPENS AdaptorActivity — the interactive Google Play Games
 *      sign-in popup. THIS IS THE POPUP THE USER SEES EVERY LAUNCH.
 *
 * The popup is correct behavior the FIRST time (user needs to sign in
 * once), but it should NOT reappear on every subsequent launch when the
 * user has already signed in. The bug is that silentSignIn is failing
 * and the fallback is the interactive UI.
 *
 * THE FIX:
 *   We hook displayLoginDialogIfNecessary to check getAccount() FIRST.
 *   SocialImpl.getAccount() calls GoogleSignIn.getLastSignedInAccount(),
 *   which returns the GoogleSignInAccount that is CACHED ON THE DEVICE
 *   by Google Play Services. It returns non-null if and only if the
 *   user has signed in before AND the account is still valid (not
 *   expired). If the user has never signed in or has signed out,
 *   getAccount() returns null.
 *
 *   Hook flow:
 *
 *     if (forceReset == true)  -> skip hook, run original code
 *                                  (preserves the "reset guard" path)
 *     account = getAccount()
 *     if (account == null)     -> skip hook, run original code
 *                                  (FIRST TIME: shows popup so user
 *                                   can sign in and get cloud saves)
 *     // account != null: user already signed in before
 *     listener.onFinish(true)  -> tell C# we're authenticated
 *     return-void              -> suppress the popup
 *
 *   Effect:
 *     - FIRST launch (user never signed in):
 *         getAccount() returns null -> hook falls through to original
 *         code -> popup appears -> user signs in -> cloud saves work.
 *     - SUBSEQUENT launches (user already signed in):
 *         getAccount() returns non-null -> hook calls onFinish(true)
 *         and returns -> popup NEVER appears -> cloud saves continue
 *         to work via the cached account.
 *     - User signed out / cleared data:
 *         getAccount() returns null -> popup appears again (correct).
 *     - In-game "Sign in" button still works (calls authenticate()
 *         directly, not displayLoginDialogIfNecessary).
 *
 *   This is the BEST of both worlds:
 *     - Fixes the bug (popup doesn't reappear every launch)
 *     - Preserves first-time login (popup appears once when needed)
 *     - Preserves cloud saves (cached account still works)
 *     - No regression on the in-game Sign In button
 *
 * Smali signature (from decompiled APK 0.111):
 *   .method public displayLoginDialogIfNecessary(ZLcom/mobge/unitygameintegration/ActionListener;)V
 *     .locals 1
 *     if-eqz p1, :cond_0
 *     const/4 p1, 0x0
 *     iput-boolean p1, p0, Lcom/mobge/unitygameintegration/SocialImpl;->d:Z
 *     :cond_0
 *     iget-boolean p1, p0, Lcom/mobge/unitygameintegration/SocialImpl;->d:Z
 *     if-eqz p1, :cond_1
 *     return-void
 *     :cond_1
 *     const/4 p1, 0x1
 *     iput-boolean p1, p0, Lcom/mobge/unitygameintegration/SocialImpl;->d:Z
 *     iget-boolean v0, p0, Lcom/mobge/unitygameintegration/SocialImpl;->c:Z
 *     xor-int/2addr p1, v0
 *     invoke-direct {p0, p1, p2}, Lcom/mobge/unitygameintegration/SocialImpl;->p(ZLcom/mobge/unitygameintegration/ActionListener;)V
 *     return-void
 *   .end method
 */
object DisplayLoginDialogIfNecessaryFingerprint : Fingerprint(
    definingClass = "Lcom/mobge/unitygameintegration/SocialImpl;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf(
        "Z",
        "Lcom/mobge/unitygameintegration/ActionListener;"
    ),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/mobge/unitygameintegration/SocialImpl;",
            name = "p",
            parameters = listOf("Z", "Lcom/mobge/unitygameintegration/ActionListener;"),
            returnType = "V",
        ),
    )
)
