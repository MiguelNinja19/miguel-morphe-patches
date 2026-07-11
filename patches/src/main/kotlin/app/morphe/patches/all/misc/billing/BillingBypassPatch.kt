package app.morphe.patches.all.misc.billing

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
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

        val targetMethodNames = setOf(
            "startConnection", "queryPurchasesAsync", "queryPurchasesExtraParams",
            "consumeAsync", "acknowledgePurchase", "acknowledgePurchaseExtraParams",
            "launchBillingFlow", "isBillingSupported", "isBillingSupportedExtraParams",
            "consumePurchase", "consumePurchaseExtraParams",
            "isReady", "endConnection",
            "isFeatureSupported", "isFeatureSupportedExtraParams",
            "queryPurchaseHistory", "queryPurchaseHistoryAsync",
            "getPurchaseState", "getOriginalJson", "getSignature",
        )

        var patchedCount = 0

        logger.info("Scanning for billing classes...")

        classDefForEach { classDef ->
            val className = classDef.type

            val isBillingClass = billingPrefixes.any { prefix ->
                className.startsWith(prefix)
            }

            if (!isBillingClass) return@classDefForEach

            val hasBillingMethods = classDef.methods.any { method ->
                method.name in targetMethodNames
            }

            if (!hasBillingMethods) return@classDefForEach

            logger.info("Found billing class: $className")

            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.methods.forEach { method ->
                if (method.implementation == null) return@forEach

                val methodName = method.name
                val returnType = method.returnType
                val paramCount = method.parameterTypes.size

                fun replaceMethodBody(smali: String) {
                    val insnCount = method.implementation!!.instructions.size
                    method.removeInstructions(0, insnCount)
                    method.addInstructions(0, smali)
                }

                // startConnection → return void (skip, don't call callback)
                if (methodName == "startConnection" && returnType == "V") {
                    replaceMethodBody("return-void")
                    patchedCount++
                    logger.info("  ✓ $methodName() = void (skip)")
                }

                // queryPurchasesAsync → return void (skip)
                if ((methodName == "queryPurchasesAsync" ||
                    methodName == "queryPurchasesExtraParams") &&
                    returnType == "V"
                ) {
                    replaceMethodBody("return-void")
                    patchedCount++
                    logger.info("  ✓ $methodName() = void (skip)")
                }

                // consumeAsync → return void (skip)
                if (methodName == "consumeAsync" && returnType == "V") {
                    replaceMethodBody("return-void")
                    patchedCount++
                    logger.info("  ✓ $methodName() = void (skip)")
                }

                // acknowledgePurchase → return void (skip)
                if ((methodName == "acknowledgePurchase" ||
                    methodName == "acknowledgePurchaseExtraParams") &&
                    returnType == "V"
                ) {
                    replaceMethodBody("return-void")
                    patchedCount++
                    logger.info("  ✓ $methodName() = void (skip)")
                }

                // launchBillingFlow → return void if void, or return 0 if int
                if (methodName == "launchBillingFlow") {
                    when (returnType) {
                        "V" -> {
                            replaceMethodBody("return-void")
                            patchedCount++
                            logger.info("  ✓ $methodName() = void (skip)")
                        }
                        "I" -> {
                            replaceMethodBody("""
                                const/4 v0, 0x0
                                return v0
                            """.trimIndent())
                            patchedCount++
                            logger.info("  ✓ $methodName() = 0 (OK)")
                        }
                        // BillingResult return type — can't create without extension
                        // Leave it unpatched (app will try Google Play and fail gracefully)
                    }
                }

                // isBillingSupported → return 0 (OK)
                if ((methodName == "isBillingSupported" ||
                    methodName == "isBillingSupportedExtraParams") &&
                    returnType == "I"
                ) {
                    replaceMethodBody("""
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $methodName() = 0 (OK)")
                }

                // consumePurchase → return 0 (success)
                if ((methodName == "consumePurchase" ||
                    methodName == "consumePurchaseExtraParams") &&
                    returnType == "I"
                ) {
                    replaceMethodBody("""
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $methodName() = 0 (success)")
                }

                // isReady → return true
                if (methodName == "isReady" && returnType == "Z") {
                    replaceMethodBody("""
                        const/4 v0, 0x1
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $methodName() = true")
                }

                // endConnection → return void
                if (methodName == "endConnection" && returnType == "V") {
                    replaceMethodBody("return-void")
                    patchedCount++
                    logger.info("  ✓ $methodName() = void (no-op)")
                }

                // isFeatureSupported → return 0
                if ((methodName == "isFeatureSupported" ||
                    methodName == "isFeatureSupportedExtraParams") &&
                    returnType == "I"
                ) {
                    replaceMethodBody("""
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $methodName() = 0 (supported)")
                }

                // queryPurchaseHistory → return void
                if ((methodName == "queryPurchaseHistory" ||
                    methodName == "queryPurchaseHistoryAsync") &&
                    returnType == "V"
                ) {
                    replaceMethodBody("return-void")
                    patchedCount++
                    logger.info("  ✓ $methodName() = void (skip)")
                }

                // getPurchaseState → return 0 (PURCHASED)
                if (methodName == "getPurchaseState" && returnType == "I") {
                    replaceMethodBody("""
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $methodName() = 0 (PURCHASED)")
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
