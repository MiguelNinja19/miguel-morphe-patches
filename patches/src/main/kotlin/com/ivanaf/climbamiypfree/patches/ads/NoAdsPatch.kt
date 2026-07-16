/*
 * Remove Ads patch for Climb!
 *
 * Injects a fake Purchase with productId="climbnoads" into the purchase
 * list when the game queries purchases.
 *
 * FIX: The List<Purchase> from Google Play Billing is IMMUTABLE. We
 * create a new ArrayList, copy existing purchases, add the fake one,
 * and replace p2 with the new list.
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

        // Fake Purchase JSON — productId must be "climbnoads"
        // In smali, " is escaped as \", and in Kotlin string that's \\\"
        val fakeJson = "{\\\"productId\\\":\\\"climbnoads\\\",\\\"purchaseToken\\\":\\\"climbnoads\\\",\\\"packageName\\\":\\\"com.IvanAF.ClimbAMIYPfree\\\",\\\"purchaseState\\\":1,\\\"purchaseTime\\\":1700000000000,\\\"acknowledged\\\":true}"

        // ================================================================
        // HOOK 1: GooglePlayBillingService$1.onQueryPurchasesResponse
        // .locals 5 — v0-v4 available
        // p0=this, p1=BillingResult, p2=List<Purchase>
        //
        // The List from Google Play is IMMUTABLE. We create a new ArrayList,
        // copy existing items, add fake Purchase, and replace p2.
        // ================================================================
        val queryListenerClass = classDefByOrNull("Lcom/IvanAF/ClimbAMIYPfree/GooglePlayBillingService\$1;")
        if (queryListenerClass != null) {
            val mutableClass = mutableClassDefBy(queryListenerClass)
            mutableClass.methods.find {
                it.name == "onQueryPurchasesResponse" && it.parameterTypes.size == 2
            }?.let { method ->
                if (method.implementation != null) {
                    val sb = StringBuilder()
                    // v0 = new ArrayList (mutable copy)
                    sb.append("new-instance v0, Ljava/util/ArrayList;\n")
                    sb.append("invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V\n")
                    // If p2 != null, add all existing purchases to v0
                    sb.append("if-eqz p2, :skip_copy_query\n")
                    sb.append("invoke-interface {v0, p2}, Ljava/util/List;->addAll(Ljava/util/Collection;)Z\n")
                    sb.append(":skip_copy_query\n")
                    // v1 = new Purchase(json, "")
                    sb.append("new-instance v1, Lcom/android/billingclient/api/Purchase;\n")
                    sb.append("const-string v2, \"")
                    sb.append(fakeJson)
                    sb.append("\"\n")
                    sb.append("const-string v3, \"\"\n")
                    sb.append("invoke-direct {v1, v2, v3}, Lcom/android/billingclient/api/Purchase;-><init>(Ljava/lang/String;Ljava/lang/String;)V\n")
                    // v0.add(v1) — add fake Purchase to ArrayList
                    sb.append("invoke-virtual {v0, v1}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z\n")
                    // p2 = v0 — replace immutable list with our mutable one
                    sb.append("move-object p2, v0")
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
        // Same approach: create new ArrayList, copy, add fake, replace p2
        // ================================================================
        val updatedListenerClass = classDefByOrNull("Lcom/IvanAF/ClimbAMIYPfree/GooglePlayBillingService\$YYPurchasesUpdatedListener;")
        if (updatedListenerClass != null) {
            val mutableClass = mutableClassDefBy(updatedListenerClass)
            mutableClass.methods.find {
                it.name == "onPurchasesUpdated" && it.parameterTypes.size == 2
            }?.let { method ->
                if (method.implementation != null) {
                    val sb = StringBuilder()
                    sb.append("new-instance v0, Ljava/util/ArrayList;\n")
                    sb.append("invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V\n")
                    sb.append("if-eqz p2, :skip_copy_update\n")
                    sb.append("invoke-interface {v0, p2}, Ljava/util/List;->addAll(Ljava/util/Collection;)Z\n")
                    sb.append(":skip_copy_update\n")
                    sb.append("new-instance v1, Lcom/android/billingclient/api/Purchase;\n")
                    sb.append("const-string v2, \"")
                    sb.append(fakeJson)
                    sb.append("\"\n")
                    sb.append("const-string v3, \"\"\n")
                    sb.append("invoke-direct {v1, v2, v3}, Lcom/android/billingclient/api/Purchase;-><init>(Ljava/lang/String;Ljava/lang/String;)V\n")
                    sb.append("invoke-virtual {v0, v1}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z\n")
                    sb.append("move-object p2, v0")
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
