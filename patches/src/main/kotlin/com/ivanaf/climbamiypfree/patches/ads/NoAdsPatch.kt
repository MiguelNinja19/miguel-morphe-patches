/*
 * Remove Ads patch for Climb!
 *
 * HOW IT WORKS:
 *
 * The game (GameMaker Studio) receives purchases via async event JSON:
 *   1. GML calls GPBilling_QueryPurchasesAsync()
 *   2. Java calls GooglePlayBillingService$1.onQueryPurchasesResponse()
 *   3. That method iterates the Purchase list, builds a JSON array, and
 *      sends it to GML via RunnerJNILib.CreateAsynEventWithDSMap()
 *   4. GML parses the JSON, checks if any purchase has productId="climbnoads"
 *
 * This patch injects a fake Purchase with productId="climbnoads" into the
 * Purchase list at the START of onQueryPurchasesResponse, BEFORE the
 * iteration. This way:
 *   - The fake purchase is added to m_purchaseRequests HashMap
 *   - Its JSON is included in the array sent to GML
 *   - GML finds "climbnoads" and treats No Ads as purchased
 *
 * Also patches onPurchasesUpdated (YYPurchasesUpdatedListener) for when
 * the user taps "Buy" — the fake purchase is added there too.
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
        "by injecting a fake Purchase into the purchase list when the game " +
        "queries purchases. The game's GML code finds 'climbnoads' in the " +
        "purchase list and treats No Ads as purchased.",
    default = true,
) {
    compatibleWith(CLIMB)

    execute {
        val logger = Logger.getLogger("NoAds")
        var count = 0

        // The fake Purchase JSON — productId must be "climbnoads"
        // purchaseToken is also "climbnoads" so it's used as HashMap key
        val fakeJson = "{\"productId\":\"climbnoads\",\"purchaseToken\":\"climbnoads\",\"packageName\":\"com.IvanAF.ClimbAMIYPfree\",\"purchaseState\":1,\"purchaseTime\":1700000000000,\"acknowledged\":true}"

        // ================================================================
        // HOOK 1: GooglePlayBillingService$1.onQueryPurchasesResponse
        // ================================================================
        // .locals 5 (v0-v4 available), p0=this, p1=BillingResult, p2=List
        //
        // Inject at index 0 (before :try_start_0):
        //   if p2 == null, skip
        //   v0 = new Purchase(json, "")
        //   p2.add(v0)
        //   :skip
        //   nop
        // ================================================================
        val queryListenerClass = classDefByOrNull("Lcom/IvanAF/ClimbAMIYPfree/GooglePlayBillingService\$1;")
        if (queryListenerClass != null) {
            val mutableClass = mutableClassDefBy(queryListenerClass)
            mutableClass.methods.find {
                it.name == "onQueryPurchasesResponse" && it.parameterTypes.size == 2
            }?.let { method ->
                if (method.implementation != null) {
                    val sb = StringBuilder()
                    sb.append("if-eqz p2, :skip_query\n")
                    sb.append("new-instance v0, Lcom/android/billingclient/api/Purchase;\n")
                    sb.append("const-string v1, \"")
                    sb.append(fakeJson)
                    sb.append("\"\n")
                    sb.append("const-string v2, \"\"\n")
                    sb.append("invoke-direct {v0, v1, v2}, Lcom/android/billingclient/api/Purchase;-><init>(Ljava/lang/String;Ljava/lang/String;)V\n")
                    sb.append("invoke-interface {p2, v0}, Ljava/util/List;->add(Ljava/lang/Object;)Z\n")
                    sb.append(":skip_query\n")
                    sb.append("nop")
                    method.addInstructions(0, sb.toString())
                    count++
                    logger.info("  patched: GooglePlayBillingService\$1.onQueryPurchasesResponse -> inject climbnoads")
                }
            }
        } else {
            logger.info("  GooglePlayBillingService\$1 not found")
        }

        // ================================================================
        // HOOK 2: YYPurchasesUpdatedListener.onPurchasesUpdated
        // ================================================================
        // .locals 10 (plenty available), p0=this, p1=BillingResult, p2=List
        // Same injection: add fake Purchase to p2 before iteration.
        // ================================================================
        val updatedListenerClass = classDefByOrNull("Lcom/IvanAF/ClimbAMIYPfree/GooglePlayBillingService\$YYPurchasesUpdatedListener;")
        if (updatedListenerClass != null) {
            val mutableClass = mutableClassDefBy(updatedListenerClass)
            mutableClass.methods.find {
                it.name == "onPurchasesUpdated" && it.parameterTypes.size == 2
            }?.let { method ->
                if (method.implementation != null) {
                    val sb = StringBuilder()
                    sb.append("if-eqz p2, :skip_update\n")
                    sb.append("new-instance v0, Lcom/android/billingclient/api/Purchase;\n")
                    sb.append("const-string v1, \"")
                    sb.append(fakeJson)
                    sb.append("\"\n")
                    sb.append("const-string v2, \"\"\n")
                    sb.append("invoke-direct {v0, v1, v2}, Lcom/android/billingclient/api/Purchase;-><init>(Ljava/lang/String;Ljava/lang/String;)V\n")
                    sb.append("invoke-interface {p2, v0}, Ljava/util/List;->add(Ljava/lang/Object;)Z\n")
                    sb.append(":skip_update\n")
                    sb.append("nop")
                    method.addInstructions(0, sb.toString())
                    count++
                    logger.info("  patched: YYPurchasesUpdatedListener.onPurchasesUpdated -> inject climbnoads")
                }
            }
        } else {
            logger.info("  YYPurchasesUpdatedListener not found")
        }

        logger.info("No ads COMPLETE: " + count + " methods patched")
        logger.info("  Fake Purchase with productId='climbnoads' injected into purchase list")
    }
}
