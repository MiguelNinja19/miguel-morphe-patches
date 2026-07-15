/*
 * Remove Ads patch for Climb!
 *
 * Simulates the purchase of the "climbnoads" IAP product. When the game
 * checks if the user bought "No Ads", it finds the fake purchase in the
 * purchase list and stops showing ads naturally.
 *
 * The game (GameMaker Studio) checks GetPurchases() which returns
 * m_purchaseRequests HashMap. We hook GetPurchases() to inject a fake
 * Purchase with productId="climbnoads" before returning the map.
 *
 * Purchase JSON format:
 *   {"productId":"climbnoads","purchaseToken":"mod","packageName":"com.IvanAF.ClimbAMIYPfree",
 *    "purchaseState":1,"purchaseTime":1700000000000,"acknowledged":true}
 *
 * This is cleaner than patching ad methods directly — the game's own
 * logic handles removing ads when it thinks you purchased "climbnoads".
 */

package com.ivanaf.climbamiypfree.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.ivanaf.climbamiypfree.patches.shared.CLIMB
import java.util.logging.Logger

@Suppress("unused")
val noAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Simulates the purchase of the 'climbnoads' IAP product " +
        "so the game thinks you bought No Ads and stops showing ads " +
        "naturally. Injects a fake Purchase with productId='climbnoads' " +
        "into the purchase list when the game queries purchases.",
    default = true,
) {
    compatibleWith(CLIMB)

    execute {
        val logger = Logger.getLogger("NoAds")

        // Find GooglePlayBillingService.GetPurchases() which returns the
        // m_purchaseRequests HashMap. Hook it to inject a fake Purchase
        // with productId="climbnoads" before returning.
        val billingServiceClass = classDefByOrNull("Lcom/IvanAF/ClimbAMIYPfree/GooglePlayBillingService;")
        if (billingServiceClass == null) {
            logger.info("No ads FAILED: GooglePlayBillingService not found")
            return@execute
        }

        val mutableClass = mutableClassDefBy(billingServiceClass)
        val getPurchasesMethod = mutableClass.methods.find {
            it.name == "GetPurchases" && it.returnType == "Ljava/util/Map;"
        }

        if (getPurchasesMethod == null || getPurchasesMethod.implementation == null) {
            logger.info("No ads FAILED: GetPurchases method not found")
            return@execute
        }

        // The method is:
        //   .method public GetPurchases()Ljava/util/Map;
        //   .locals 1
        //   iget-object v0, p0, Lcom/.../GooglePlayBillingService;->m_purchaseRequests:Ljava/util/HashMap;
        //   return-object v0
        //
        // We inject BEFORE the iget-object to add a fake Purchase.
        // Smali flow:
        //   v0 = this.m_purchaseRequests (HashMap)
        //   v1 = new Purchase(json, "")  // fake purchase
        //   v0.put("climbnoads", v1)    // add to map
        //   return-object v0             // return map with fake purchase
        //
        // NOTE: Purchase constructor throws JSONException, but our JSON is valid.
        // The smali uses new-instance + invoke-direct to create Purchase.
        val className = "Lcom/IvanAF/ClimbAMIYPfree/GooglePlayBillingService;"
        val sb = StringBuilder()
        sb.append("iget-object v0, p0, ")
        sb.append(className)
        sb.append("->m_purchaseRequests:Ljava/util/HashMap;\n")
        // Build Purchase JSON
        sb.append("const-string v1, \"{\\\"productId\\\":\\\"climbnoads\\\",\\\"purchaseToken\\\":\\\"mod\\\",\\\"packageName\\\":\\\"com.IvanAF.ClimbAMIYPfree\\\",\\\"purchaseState\\\":1,\\\"purchaseTime\\\":1700000000000,\\\"acknowledged\\\":true}\"\n")
        // Create Purchase object: new Purchase(json, "")
        sb.append("new-instance v2, Lcom/android/billingclient/api/Purchase;\n")
        sb.append("const-string v3, \"\"\n")
        sb.append("invoke-direct {v2, v1, v3}, Lcom/android/billingclient/api/Purchase;-><init>(Ljava/lang/String;Ljava/lang/String;)V\n")
        // Put into HashMap: m_purchaseRequests.put("climbnoads", purchase)
        sb.append("const-string v1, \"climbnoads\"\n")
        sb.append("invoke-virtual {v0, v1, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;\n")
        // Return the map
        sb.append("return-object v0")

        // Replace the method body
        getPurchasesMethod.addInstructions(0, sb.toString())

        logger.info("No ads COMPLETE: injected fake 'climbnoads' purchase into GetPurchases")
        logger.info("  The game will think you bought No Ads and stop showing ads")
    }
}
