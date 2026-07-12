package app.morphe.patches.all.misc.billing

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import java.util.logging.Logger

@Suppress("unused")
val billingBypassPatch = bytecodePatch(
    name = "Billing bypass",
    description = "Patches Google Play In-App Billing to simulate successful " +
        "purchases without contacting Google Play.",
    default = false,
) {
    execute {
        val logger = Logger.getLogger("BillingBypass")

        val billingPrefixes = setOf(
            "Lcom/android/billingclient/api/",
            "Lcom/android/vending/billing/",
            "Lcom/google/android/gms/iap/",
        )

        var patchedCount = 0

        logger.info("Scanning for billing classes...")

        classDefForEach { classDef ->
            val className = classDef.type

            val isBillingClass = billingPrefixes.any { prefix ->
                className.startsWith(prefix)
            }

            if (!isBillingClass) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.methods.forEach { method ->
                if (method.implementation == null) return@forEach

                val methodName = method.name
                val returnType = method.returnType

                // startConnection → return void immediately
                if (methodName == "startConnection" && returnType == "V") {
                    method.addInstructions(0, "return-void")
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = void")
                }

                // queryPurchasesAsync → return void immediately
                if ((methodName == "queryPurchasesAsync" ||
                    methodName == "queryPurchasesExtraParams") &&
                    returnType == "V"
                ) {
                    method.addInstructions(0, "return-void")
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = void")
                }

                // consumeAsync → return void immediately
                if (methodName == "consumeAsync" && returnType == "V") {
                    method.addInstructions(0, "return-void")
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = void")
                }

                // acknowledgePurchase → return void immediately
                if ((methodName == "acknowledgePurchase" ||
                    methodName == "acknowledgePurchaseExtraParams") &&
                    returnType == "V"
                ) {
                    method.addInstructions(0, "return-void")
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = void")
                }

                // launchBillingFlow → return void if void
                if (methodName == "launchBillingFlow" && returnType == "V") {
                    method.addInstructions(0, "return-void")
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = void")
                }

                // isBillingSupported → return 0
                if ((methodName == "isBillingSupported" ||
                    methodName == "isBillingSupportedExtraParams") &&
                    returnType == "I"
                ) {
                    method.addInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = 0")
                }

                // consumePurchase → return 0
                if ((methodName == "consumePurchase" ||
                    methodName == "consumePurchaseExtraParams") &&
                    returnType == "I"
                ) {
                    method.addInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = 0")
                }

                // isReady → return true
                if (methodName == "isReady" && returnType == "Z") {
                    method.addInstructions(0, """
                        const/4 v0, 0x1
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = true")
                }

                // endConnection → return void
                if (methodName == "endConnection" && returnType == "V") {
                    method.addInstructions(0, "return-void")
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = void")
                }

                // isFeatureSupported → return 0
                if ((methodName == "isFeatureSupported" ||
                    methodName == "isFeatureSupportedExtraParams") &&
                    returnType == "I"
                ) {
                    method.addInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = 0")
                }

                // queryPurchaseHistory → return void
                if ((methodName == "queryPurchaseHistory" ||
                    methodName == "queryPurchaseHistoryAsync") &&
                    returnType == "V"
                ) {
                    method.addInstructions(0, "return-void")
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = void")
                }

                // getPurchaseState → return 0 (PURCHASED)
                if (methodName == "getPurchaseState" && returnType == "I") {
                    method.addInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = 0 (PURCHASED)")
                }
            }
        }

        if (patchedCount == 0) {
            throw PatchException(
                "No Google Play Billing classes found in this app."
            )
        }

        logger.info("Billing bypass complete: $patchedCount methods patched")
    }
}
