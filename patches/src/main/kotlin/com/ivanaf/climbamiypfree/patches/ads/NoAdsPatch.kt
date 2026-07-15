/*
 * Remove Ads patch for Climb!
 *
 * Simulates the purchase of the "climbnoads" IAP product.
 *
 * The game (GameMaker Studio) checks purchases via:
 *   1. GPBilling_QueryPurchasesAsync -> onQueryPurchasesResponse populates
 *      m_purchaseRequests HashMap with getPurchaseToken() as key
 *   2. GML receives JSON with purchases array (productId, purchaseToken, etc)
 *   3. GML finds productId="climbnoads", gets its purchaseToken
 *   4. GML calls GPBilling_Purchase_GetState(token) to verify state
 *
 * Problem: We can't easily inject into the JSON sent to GML.
 * Solution: Patch BOTH:
 *   - GetPurchases() to add fake Purchase with purchaseToken="climbnoads"
 *   - GPBilling_Purchase_GetState("climbnoads") to return 13001 (PURCHASED)
 *
 * When GML calls GPBilling_Purchase_GetState("climbnoads"), it gets 13001
 * (PURCHASED state) and treats it as bought.
 */

package com.ivanaf.climbamiypfree.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.ivanaf.climbamiypfree.patches.shared.CLIMB
import com.ivanaf.climbamiypfree.patches.shared.PurchaseGetStateFingerprint
import java.util.logging.Logger

@Suppress("unused")
val noAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Simulates the purchase of the 'climbnoads' IAP product " +
        "so the game thinks you bought No Ads and stops showing ads " +
        "naturally. Patches GPBilling_Purchase_GetState to return PURCHASED " +
        "when called with 'climbnoads'.",
    default = true,
) {
    compatibleWith(CLIMB)

    execute {
        val logger = Logger.getLogger("NoAds")

        // HOOK: GPBilling_Purchase_GetState(String)D
        // If argument is "climbnoads", return 13001.0 (PURCHASED state)
        // Otherwise, call original method.
        //
        // Smali flow:
        //   p1 = String argument (purchaseToken or productId)
        //   if p1 == "climbnoads" goto :fake
        //   [original code runs here - calls GetPurchases().get(p1)]
        //   :fake
        //   const-wide v0, 0x40c9648000000000L  # 13001.0
        //   return-wide v0
        val getStateMethod = PurchaseGetStateFingerprint.matchOrNull()?.method

        if (getStateMethod != null && getStateMethod.implementation != null) {
            // Inject at the END of the method (before the last return)
            // We add a check at the START: if p1 == "climbnoads", return 13001
            // The original code becomes dead code in that case.
            //
            // Register usage: p1 is the String argument (high register in large methods)
            // We need to compare p1 with "climbnoads"
            val sb = StringBuilder()
            sb.append("const-string v0, \"climbnoads\"\n")
            sb.append("invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z\n")
            sb.append("move-result v0\n")
            sb.append("if-eqz v0, :continue\n")
            sb.append("const-wide v0, 0x40c9648000000000L\n")
            sb.append("return-wide v0\n")
            sb.append(":continue\n")
            sb.append("nop")

            getStateMethod.addInstructions(0, sb.toString())
            logger.info("No ads COMPLETE: GPBilling_Purchase_GetState patched to return PURCHASED for 'climbnoads'")
        } else {
            logger.info("No ads FAILED: GPBilling_Purchase_GetState not found")
        }
    }
}
