/*
 * Free In-App Purchases patch for Oddmar ("unlimited money" / free IAP).
 *
 * HOW IT WORKS (callback-injection pattern, mirroring the unlock patch):
 *
 * Oddmar's IAP entry point on the Java side is:
 *
 *   OddmarPurchaseHandler.PurchaseProduct(String productId,
 *                                         PurchaseCallback callback)
 *
 * The Unity C# code calls this when the user taps "Buy" on a product.
 * Internally the Java method:
 *
 *   1. Checks if BillingClient.isReady(). If not, calls
 *      callback.OnCallback(false) and retries the connection.
 *   2. Otherwise creates a PurchaseCall that does:
 *        - QueryProductDetailsAsync to fetch the SKU's price
 *        - BillingClient.launchBillingFlow(...) to show the Play Store
 *          purchase dialog
 *        - On success, calls callback.OnCallback(true)
 *        - On failure, calls callback.OnCallback(false)
 *
 * The PurchaseCallback interface is:
 *   interface PurchaseCallback { void OnCallback(boolean success); }
 *
 * When the C# code receives OnCallback(true), it runs ProcessPurchase
 * and credits the product — including any coin/currency packs, level
 * unlocks, etc. — without ever charging the user.
 *
 * The patch:
 *
 *   We replace the body of PurchaseProduct with two smali instructions
 *   that immediately invoke callback.OnCallback(true) and return. The
 *   original code that contacts Google Play Billing becomes dead code.
 *
 *   p0 = this  (OddmarPurchaseHandler)
 *   p1 = productId  (String, ignored)
 *   p2 = callback  (PurchaseCallback)
 *
 *   const/4 v0, 0x1                    # true
 *   invoke-interface {p2, v0}, Lcom/mobge/oddmarbilling/PurchaseCallback;->OnCallback(Z)V
 *   return-void
 *
 * Result: every "Buy" tap instantly credits the product — no Play Store
 * dialog, no payment, no waiting. Combined with the UnlockFullGamePatch,
 * the player gets:
 *
 *   - The full game unlocked (levels + worlds) for free
 *   - Every IAP product purchased for free
 *
 * In Oddmar 0.111 the only IAP SKU is "unlock_all_levels", so this
 * patch's main visible effect is "free full-game unlock on tap".
 * Future SKUs (coin packs, gem packs, etc.) would also be free.
 *
 * NATIVE PATCHING LIMITATION:
 *
 * The apkhome.net mod ("Unlimited Money, Unlocked All") also patches
 * libil2cpp.so directly via the KittyMemory library to give infinite
 * in-game coins/cash. That requires runtime memory patching of the
 * IL2CPP-compiled C# code, which is NOT reproducible via morphe's
 * bytecode patches without il2cpp-dumping the lib to find the exact
 * offsets of the coin counter methods.
 *
 * This patch instead gives "unlimited money" via the IAP route: every
 * purchase is free, so any coin pack the game adds in the future
 * would be free. For Oddmar 0.111 specifically, the only IAP is
 * "unlock_all_levels" — there is no separate coin/gem pack IAP — so
 * "unlimited money" in the literal sense requires the native patch
 * and is left as future work.
 *
 * Pattern reference (morphe-ai):
 *   - billing-bypass-patterns.md: "Skip billing flow, credit product
 *     directly" — the canonical pattern for custom Java billing wrappers
 *     that delegate to Google Play Billing.
 *   - patcher-apis.md: addInstructions(0, ...) replaces the method body.
 *   - patch-examples.md: Pattern 3 (Return Value Override) and Pattern 5
 *     (Callback Replacement).
 *
 * Pure smali, no extension DEX, no native patching.
 */

package com.mobge.Oddmar.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.mobge.Oddmar.patches.shared.ODDMAR
import com.mobge.Oddmar.patches.shared.PurchaseProductFingerprint

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Makes every IAP purchase succeed instantly without " +
        "contacting Google Play Billing. When the user taps 'Buy' on " +
        "any in-app product, the game credits the purchase immediately " +
        "and skips the Play Store dialog. For Oddmar 0.111 the only " +
        "IAP product is 'unlock_all_levels', so this patch gives a " +
        "free full-game unlock on demand. Note: literal 'unlimited " +
        "coins' (the in-game currency counter) requires native " +
        "libil2cpp.so patching and is NOT covered by this Java patch.",
    default = false,
) {
    compatibleWith(ODDMAR)

    execute {
        // ============================================================
        // HOOK 1: PurchaseProduct -> always call OnCallback(true)
        // ============================================================
        // Pattern: callback replacement (morphe-ai Pattern 5).
        //
        // We replace the method body with three instructions:
        //   1. const/4 v0, 0x1            # load true into v0
        //   2. invoke-interface {p2, v0}, ...->OnCallback(Z)V
        //                                # call callback.OnCallback(true)
        //   3. return-void                # exit
        //
        // p2 is the PurchaseCallback parameter.
        // The original code (which calls BillingClient.isReady,
        // creates a PurchaseCall, calls queryProductDetailsAsync,
        // launchBillingFlow, etc.) becomes dead code.
        //
        // The C# side treats OnCallback(true) as "purchase succeeded"
        // and runs ProcessPurchase, crediting the product to the
        // player's account without ever showing the Play Store dialog
        // or charging money.
        // ============================================================
        PurchaseProductFingerprint.method.addInstructions(0, """
            const/4 v0, 0x1
            invoke-interface {p2, v0}, Lcom/mobge/oddmarbilling/PurchaseCallback;->OnCallback(Z)V
            return-void
        """.trimIndent())
    }
}
