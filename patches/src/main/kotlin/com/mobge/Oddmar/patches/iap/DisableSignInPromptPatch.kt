/*
 * Disable Google Play Games Sign-In Prompt (when already signed in) patch for Oddmar.
 *
 * WHY THIS PATCH EXISTS:
 *
 * User report (GitHub issue #4, Sourav0357):
 *   "Also in oddmar whenever I open the game, it keeps asking to sign in
 *    Google Play Games, even though I signed in already."
 *
 * Root cause (traced through decompiled smali of v0.111):
 *
 * Oddmar's Unity C# code calls Java's
 *   com.mobge.unitygameintegration.SocialImpl.displayLoginDialogIfNecessary(
 *       boolean forceReset, ActionListener listener)
 * on every game launch (from MRSocial.ensureAuthenticate(), found in
 * global-metadata.dat at offset 965534).
 *
 * The flow on every launch:
 *
 *   1. Unity creates a NEW SocialImpl instance. Field `i` (authenticated)
 *      is false, field `d` (already prompted) is false.
 *   2. Unity calls displayLoginDialogIfNecessary(false, listener).
 *   3. Since `d` is false, the method sets `d = true` and calls
 *      private p(useSilent=true, listener).
 *   4. p() checks isAuthenticated() — returns FALSE because field `i`
 *      is false on a fresh instance. (getAccount() also returns null
 *      because the cached GoogleSignInAccount is gone after the app
 *      process is killed.)
 *   5. p() tries silentSignIn() (asynchronous).
 *   6. If silentSignIn() FAILS — which happens every time the cached
 *      account is null or expired — SocialImpl$2.onComplete() calls
 *      private w(listener, signInClient).
 *   7. w() opens com.mobge.unitygameintegration.AdaptorActivity,
 *      which is the interactive Google Play Games sign-in popup.
 *      THIS IS THE POPUP THE USER SEES EVERY LAUNCH.
 *
 * THE FIX (conditional, preserves first-time login + cloud saves):
 *
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
 * Pattern reference (morphe-ai):
 *   - patcher-apis.md: addInstructions(0, ...) inserts at index 0,
 *     the original code follows the inserted block.
 *   - patch-examples.md: Pattern 2 (Conditional Branch) + Pattern 1
 *     (returnEarly) combined.
 *
 * Pure smali, no extension DEX, no native patching.
 */

package com.mobge.Oddmar.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.mobge.Oddmar.patches.shared.ODDMAR
import com.mobge.Oddmar.patches.shared.DisplayLoginDialogIfNecessaryFingerprint

@Suppress("unused")
val disableSignInPromptPatch = bytecodePatch(
    name = "Disable Google Play Games sign-in prompt when already signed in",
    description = "Suppresses the Google Play Games sign-in popup ONLY " +
        "when the user has already signed in before. On first launch " +
        "(or after signing out) the popup still appears so the user " +
        "can authenticate and get cloud saves. On subsequent launches " +
        "the popup is suppressed because getAccount() returns the " +
        "cached GoogleSignInAccount. Cloud saves, leaderboards and " +
        "achievements continue to work normally. The in-game Sign In " +
        "button is unaffected. Fixes the issue where the popup " +
        "reappeared on every launch even after the user had already " +
        "signed in.",
    default = true,
) {
    compatibleWith(ODDMAR)

    execute {
        // ============================================================
        // HOOK: displayLoginDialogIfNecessary -> conditional skip
        // ============================================================
        // Pattern: conditional branch + returnEarly (morphe-ai
        // Pattern 2 + Pattern 1).
        //
        // We prepend a check to the method body. The original code
        // runs unchanged when:
        //   - forceReset (p1) is true (Unity wants to reset the
        //     "already prompted" guard), OR
        //   - getAccount() returns null (user has never signed in or
        //     has signed out, so we DO want to show the popup).
        //
        // When getAccount() returns non-null (user is already signed
        // in with a valid cached account), we short-circuit:
        //   - Call listener.onFinish(true) to tell Unity the
        //     authentication check succeeded.
        //   - Return immediately so the popup never opens.
        //
        // Register usage:
        //   p0 = this  (SocialImpl)
        //   p1 = forceReset  (Z)
        //   p2 = listener  (ActionListener)
        //   v0 = scratch (account, then 1 for true)
        //
        // The method's original .locals 1 is sufficient because we
        // only use v0 (reused) and the parameter registers.
        // ============================================================
        DisplayLoginDialogIfNecessaryFingerprint.method.addInstructions(0, """
            # If forceReset (p1) is true, skip the hook — let the
            # original code reset the `d` guard and run normally.
            if-nez p1, :hook_end

            # Get the cached GoogleSignInAccount from Google Play
            # Services. Returns non-null iff user signed in before
            # AND the account is still valid.
            invoke-virtual {p0}, Lcom/mobge/unitygameintegration/SocialImpl;->getAccount()Lcom/google/android/gms/auth/api/signin/GoogleSignInAccount;
            move-result-object v0

            # If account is null, skip the hook — let the original
            # code show the popup so the user can sign in (first time
            # or after signing out).
            if-eqz v0, :hook_end

            # Account is cached and valid: user is already signed in.
            # Call listener.onFinish(true) so Unity treats this as a
            # successful authentication and proceeds silently.
            const/4 v0, 0x1
            invoke-interface {p2, v0}, Lcom/mobge/unitygameintegration/ActionListener;->onFinish(Z)V

            # Return without running the original code — popup is
            # suppressed.
            return-void

            :hook_end
            # Original method body runs from here (unchanged).
        """.trimIndent())
    }
}
