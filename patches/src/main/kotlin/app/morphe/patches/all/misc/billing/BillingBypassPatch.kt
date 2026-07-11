package app.morphe.patches.all.misc.billing

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch
import java.util.logging.Logger

@Suppress("unused")
val billingBypassPatch = bytecodePatch(
    name = "Billing bypass",
    description = "Patches Google Play In-App Billing to simulate successful " +
        "purchases. Intercepts IInAppBillingService (v3/v6) and BillingClient " +
        "(v4+) methods. isBillingSupported returns OK, queryPurchases returns " +
        "void, consumePurchase returns success, isReady returns true, " +
        "startConnection is skipped. Based on Lucky Patcher's InApp emulation.",
    default = false,
) {
    execute {
        val logger = Logger.getLogger("BillingBypass")

        val billingPrefixes = setOf(
            "Lcom/android/billingclient/api/",
            "Lcom/android/vending/billing/",
            "Lcom/google/android/gms/iap/",
            "Lcom/amazon/inapp/purchasing/",
        )

        var patchedCount = 0

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

                // isBillingSupported → return 0 (OK)
                if ((methodName == "isBillingSupported" ||
                    methodName == "isBillingSupportedExtraParams") &&
                    returnType == "I"
                ) {
                    method.replaceInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->$methodName() = 0 (OK)")
                }

                // consumePurchase → return 0 (success)
                if ((methodName == "consumePurchase" ||
                    methodName == "consumePurchaseExtraParams") &&
                    returnType == "I"
                ) {
                    method.replaceInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->$methodName() = 0 (success)")
                }

                // stub → return 0
                if (methodName == "stub" && returnType == "I") {
                    method.replaceInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->$methodName() = 0")
                }

                // queryPurchasesAsync → return void (skip)
                if ((methodName == "queryPurchasesAsync" ||
                    methodName == "queryPurchasesExtraParams" ||
                    methodName == "queryPurchaseHistory" ||
                    methodName == "queryPurchaseHistoryAsync") &&
                    returnType == "V"
                ) {
                    method.replaceInstructions(0, """
                        return-void
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->$methodName() = void (skip)")
                }

                // consumeAsync → return void (success)
                if (methodName == "consumeAsync" && returnType == "V") {
                    method.replaceInstructions(0, """
                        return-void
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->$methodName() = void (success)")
                }

                // acknowledgePurchase → return void (success)
                if ((methodName == "acknowledgePurchase" ||
                    methodName == "acknowledgePurchaseExtraParams") &&
                    returnType == "V"
                ) {
                    method.replaceInstructions(0, """
                        return-void
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->$methodName() = void (success)")
                }

                // isReady → return true
                if (methodName == "isReady" && returnType == "Z") {
                    method.replaceInstructions(0, """
                        const/4 v0, 0x1
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->$methodName() = true")
                }

                // startConnection → return void (skip)
                if (methodName == "startConnection" && returnType == "V") {
                    method.replaceInstructions(0, """
                        return-void
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->$methodName() = void (skip)")
                }

                // endConnection → return void (no-op)
                if (methodName == "endConnection" && returnType == "V") {
                    method.replaceInstructions(0, """
                        return-void
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->$methodName() = void (no-op)")
                }

                // isFeatureSupported → return 0 (supported)
                if ((methodName == "isFeatureSupported" ||
                    methodName == "isFeatureSupportedExtraParams") &&
                    returnType == "I"
                ) {
                    method.replaceInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->$methodName() = 0 (supported)")
                }
            }
        }

        logger.info("Billing bypass: Patched $patchedCount billing methods")
    }
}
