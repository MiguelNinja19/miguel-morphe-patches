package app.morphe.patches.all.misc.billing

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch
import java.util.logging.Logger

@Suppress("unused")
val universalBillingBypassPatch = bytecodePatch(
    name = "Universal billing bypass",
    description = "Patches Google Play In-App Billing classes to simulate successful purchases.",
    default = false,
) {
    execute {
        val logger = Logger.getLogger("UniversalBillingBypass")

        val billingPrefixes = setOf(
            "Lcom/android/billingclient/api/",
            "Lcom/android/vending/billing/",
            "Lcom/google/android/gms/iap/",
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

                if (methodName == "queryPurchasesAsync" && returnType == "V") {
                    method.replaceInstructions(0, """
                        return-void
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->queryPurchasesAsync() = void")
                }

                if (methodName == "consumePurchase" && returnType == "I") {
                    method.replaceInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->consumePurchase() = 0")
                }

                if (methodName == "consumeAsync" && returnType == "V") {
                    method.replaceInstructions(0, """
                        return-void
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->consumeAsync() = void")
                }

                if (methodName == "isFeatureSupported" && returnType == "I") {
                    method.replaceInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->isFeatureSupported() = 0")
                }

                if (methodName == "isReady" && returnType == "Z") {
                    method.replaceInstructions(0, """
                        const/4 v0, 0x1
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->isReady() = true")
                }

                if (methodName == "startConnection" && returnType == "V") {
                    method.replaceInstructions(0, """
                        return-void
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->startConnection() = void")
                }
            }
        }

        logger.info("Universal billing bypass: Patched $patchedCount billing methods")
    }
}
