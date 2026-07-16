/*
 * Remove Ads patch for Climb!
 *
 * Injects a fake Purchase with productId="climbnoads" into the purchase
 * list when the game queries purchases. The game's GML code finds
 * "climbnoads" and treats No Ads as purchased.
 *
 * FIX: JSON quotes must be escaped as \" in smali string literals.
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
        "queries purchases. The game finds 'climbnoads' and treats No Ads " +
        "as purchased.",
    default = true,
) {
    compatibleWith(CLIMB)

    execute {
        val logger = Logger.getLogger("NoAds")
        var count = 0

        // In smali, string literals escape " as \"
        // So the JSON needs \" for each "
        // Kotlin: \\\" produces the literal \" in the smali source
        val fakeJson = "{\\\"productId\\\":\\\"climbnoads\\\",\\\"purchaseToken\\\":\\\"climbnoads\\\",\\\"packageName\\\":\\\"com.IvanAF.ClimbAMIYPfree\\\",\\\"purchaseState\\\":1,\\\"purchaseTime\\\":1700000000000,\\\"acknowledged\\\":true}"

        // ================================================================
        // HOOK 1: GooglePlayBillingService$1.onQueryPurchasesResponse
        // .locals 5 — v0-v4 available
        // p0=this, p1=BillingResult, p2=List<Purchase>
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
        // .locals 10 — plenty available
        // p0=this, p1=BillingResult, p2=List<Purchase>
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
    }
}
