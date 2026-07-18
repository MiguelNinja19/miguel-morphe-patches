/*
 * Unlock Full Game patch for Oddmar.
 *
 * HOW IT WORKS (callback-injection pattern):
 *
 * Oddmar uses a Java billing wrapper class called OddmarPurchaseHandler
 * (package com.mobge.oddmarbilling). Unity C# code calls into it via
 * AndroidJavaObject reflection — the class name string
 * "com.mobge.oddmarbilling.OddmarPurchaseHandler" is found in
 * assets/bin/Data/Managed/Metadata/global-metadata.dat.
 *
 * The only IAP product in Oddmar is "unlock_all_levels" (confirmed by
 * searching global-metadata.dat: the string sits right between
 * "Purchasing product asynchronously: '{0}'" and
 * "IAP: OnInitializeFailed InitializationFailureReason:").
 *
 * The flow on the Java side is:
 *
 *   1. Unity C# calls HasProductBeenPurchased("unlock_all_levels", callback)
 *      to check if the player has purchased the full game.
 *   2. The Java method queries Google Play Billing's queryPurchasesAsync
 *      and forwards the result to the callback via OnCallback(boolean).
 *   3. The callback is a Java interface:
 *          interface HasProductBeenPurchasedCallback { void OnCallback(Z); }
 *
 * The patch:
 *
 *   We replace the body of HasProductBeenPurchased with two smali
 *   instructions that immediately invoke callback.OnCallback(true) and
 *   return. The original 30+ instructions that query Google Play Billing
 *   become dead code.
 *
 *   p0 = this  (OddmarPurchaseHandler)
 *   p1 = productId  (String, ignored)
 *   p2 = callback  (HasProductBeenPurchasedCallback)
 *
 *   const/4 v0, 0x1                    # true
 *   invoke-interface {p2, v0}, Lcom/mobge/oddmarbilling/HasProductBeenPurchasedCallback;->OnCallback(Z)V
 *   return-void
 *
 * Result: the C# code sees "purchased == true" for every product, so the
 * game treats "unlock_all_levels" as already bought and unlocks every
 * level/world. No Google Play Billing connection needed, no purchases
 * needed.
 *
 * Pattern reference (morphe-ai):
 *   - billing-bypass-patterns.md: "Override validation method to always
 *     return success" — this is the canonical pattern for custom Java
 *     billing wrappers.
 *   - patcher-apis.md: addInstructions(0, ...) replaces the method body
 *     by inserting at index 0 and returning before the original code
 *     runs.
 *   - patch-examples.md: Pattern 3 (Return Value Override) and Pattern 5
 *     (Callback Replacement).
 *
 * Pure smali, no extension DEX, no native patching.
 */

package com.mobge.Oddmar.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.mobge.Oddmar.patches.shared.ODDMAR
import com.mobge.Oddmar.patches.shared.HasProductBeenPurchasedFingerprint
import com.mobge.Oddmar.patches.shared.EnsureAllPurchasesAcknowledgedFingerprint

@Suppress("unused")
val unlockFullGamePatch = bytecodePatch(
    name = "Unlock full game",
    description = "Unlocks the full game (all levels, all worlds) by " +
        "intercepting the IAP 'has product been purchased' check. " +
        "Oddmar's only IAP product is 'unlock_all_levels' — this patch " +
        "makes the game think it has already been purchased, so every " +
        "level is unlocked from the start. No Google Play Billing " +
        "connection is required. Also disables the startup 'acknowledge " +
        "all purchases' query so the patch doesn't try to reach Google " +
        "Play at boot.",
    default = true,
) {
    compatibleWith(ODDMAR)

    execute {
        // ============================================================
        // HOOK 1: HasProductBeenPurchased -> always call OnCallback(true)
        // ============================================================
        // Pattern: callback replacement (morphe-ai Pattern 5).
        //
        // We replace the method body with two instructions:
        //   1. const/4 v0, 0x1            # load true into v0
        //   2. invoke-interface {p2, v0}, ...->OnCallback(Z)V
        //                                # call callback.OnCallback(true)
        //   3. return-void                # exit
        //
        // p2 is the HasProductBeenPurchasedCallback parameter.
        // The original code (which calls BillingClient.isReady,
        // queryPurchasesAsync, etc.) becomes dead code.
        // ============================================================
        HasProductBeenPurchasedFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            invoke-interface {p2, v0}, Lcom/mobge/oddmarbilling/HasProductBeenPurchasedCallback;->OnCallback(Z)V
            return-void
        """.trimIndent())


        // ============================================================
        // HOOK 2: EnsureAllPurchasesAcknowledged -> no-op
        // ============================================================
        // Pattern: returnEarly() (morphe-ai Pattern 1).
        //
        // At startup, the billing client connects (or tries to) and then
        // calls EnsureAllPurchasesAcknowledged, which queries all
        // purchases from Google Play. When the patch makes the billing
        // connection unnecessary, this query is wasted work and could
        // even fail / cause an ANR if Google Play Billing isn't
        // available. We replace the method body with `return-void` so
        // the query never runs.
        // ============================================================
        EnsureAllPurchasesAcknowledgedFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())
    }
}
