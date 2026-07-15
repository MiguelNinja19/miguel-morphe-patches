/*
 * Free In-App Purchases patch for Climb!
 *
 * GameMaker Studio game uses GooglePlayBillingService + RunnerBillingSecurity
 * for IAP. The billing bridge is zzbu (not zzbq like Polytopia).
 *
 * HOOKS:
 * 1. RunnerBillingSecurity.verifyPurchase(String, String) -> return true
 * 2. Purchase.isAcknowledged() -> return true
 * 3. Purchase.getPurchaseState() -> return 1 (PURCHASED)
 * 4. zzbu.onBillingSetupFinished -> force responseCode=0 (success)
 * 5. zzbu.onPurchasesUpdated -> swallow errors (conditional return-void)
 */

package com.ivanaf.climbamiypfree.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.ivanaf.climbamiypfree.patches.shared.CLIMB
import com.ivanaf.climbamiypfree.patches.shared.VerifyPurchaseFingerprint
import java.util.logging.Logger

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips purchase signature verification and forces billing " +
        "to report success. Patches RunnerBillingSecurity.verifyPurchase " +
        "to always return true, Purchase.isAcknowledged/getPurchaseState " +
        "to return valid values, and the billing bridge callbacks.",
    default = false,
) {
    compatibleWith(CLIMB)

    execute {
        val logger = Logger.getLogger("FreeIAP")
        var count = 0

        // HOOK 1: RunnerBillingSecurity.verifyPurchase -> return true
        // FIX: matchOrNull is a FUNCTION, must call matchOrNull()
        VerifyPurchaseFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
            count++
            logger.info("  patched: RunnerBillingSecurity.verifyPurchase -> return true")
        }

        // HOOK 2: Purchase.isAcknowledged -> return true
        val purchaseClass = classDefByOrNull("Lcom/android/billingclient/api/Purchase;")
        if (purchaseClass != null) {
            val mutablePurchase = mutableClassDefBy(purchaseClass)
            mutablePurchase.methods.find { it.name == "isAcknowledged" && it.returnType == "Z" }?.let { method ->
                if (method.implementation != null) {
                    method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
                    count++
                    logger.info("  patched: Purchase.isAcknowledged -> return true")
                }
            }

            // HOOK 3: Purchase.getPurchaseState -> return 1 (PURCHASED)
            mutablePurchase.methods.find { it.name == "getPurchaseState" && it.returnType == "I" }?.let { method ->
                if (method.implementation != null) {
                    method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
                    count++
                    logger.info("  patched: Purchase.getPurchaseState -> return 1")
                }
            }
        }

        // HOOK 4+5: zzbu billing bridge callbacks
        classDefForEach { classDef ->
            val className = classDef.type
            if (!className.startsWith("Lcom/android/billingclient/api/zz")) return@classDefForEach
            if (!classDef.methods.any { it.name == "nativeOnPurchasesUpdated" }) return@classDefForEach

            logger.info("  found billing bridge: " + className)
            val mutableBridge = mutableClassDefBy(classDef)

            // onBillingSetupFinished -> force success
            mutableBridge.methods.find {
                it.name == "onBillingSetupFinished" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0].toString() == "Lcom/android/billingclient/api/BillingResult;"
            }?.let { method ->
                if (method.implementation != null) {
                    val nativeField = classDef.fields.find { it.type == "J" }
                    val fieldName = nativeField?.name ?: "zza"
                    val sb = StringBuilder()
                    sb.append("iget-wide v0, p0, ")
                    sb.append(className)
                    sb.append("->")
                    sb.append(fieldName)
                    sb.append(":J\n")
                    sb.append("const/4 v2, 0x0\n")
                    sb.append("const-string v3, \"\"\n")
                    sb.append("invoke-static {v2, v3, v0, v1}, ")
                    sb.append(className)
                    sb.append("->nativeOnBillingSetupFinished(ILjava/lang/String;J)V\n")
                    sb.append("return-void")
                    method.addInstructions(0, sb.toString())
                    count++
                    logger.info("  patched: " + className + ".onBillingSetupFinished -> force success")
                }
            }

            // onPurchasesUpdated -> swallow errors
            mutableBridge.methods.find { it.name == "onPurchasesUpdated" && it.parameterTypes.size == 2 }?.let { method ->
                if (method.implementation != null) {
                    val sb = StringBuilder()
                    sb.append("invoke-virtual {p1}, Lcom/android/billingclient/api/BillingResult;->getResponseCode()I\n")
                    sb.append("move-result v0\n")
                    sb.append("if-eqz v0, :continue\n")
                    sb.append("return-void\n")
                    sb.append(":continue\n")
                    sb.append("nop")
                    method.addInstructions(0, sb.toString())
                    count++
                    logger.info("  patched: " + className + ".onPurchasesUpdated -> swallow errors")
                }
            }
        }

        logger.info("Free IAP COMPLETE: " + count + " methods patched")
    }
}
