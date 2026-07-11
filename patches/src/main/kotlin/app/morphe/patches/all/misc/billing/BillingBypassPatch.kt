package app.morphe.patches.all.misc.billing

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import java.util.logging.Logger

private const val EXTENSION_CLASS = "Ldiozz/cubex/patches/extension/BillingBypass;"

@Suppress("unused")
val billingBypassPatch = bytecodePatch(
    name = "Billing bypass",
    description = "Patches Google Play In-App Billing to simulate successful " +
        "purchases. Calls billing callbacks with success results via an extension. " +
        "Based on Lucky Patcher's InApp emulation approach.",
    default = false,
) {
    extendWith("extensions/extension.mpe")

    execute {
        val logger = Logger.getLogger("BillingBypass")

        val billingPrefixes = setOf(
            "Lcom/android/billingclient/api/",
            "Lcom/android/vending/billing/",
            "Lcom/google/android/gms/iap/",
        )

        var patchedCount = 0
        var callbackCount = 0

        logger.info("Scanning for billing classes...")

        classDefForEach { classDef ->
            val className = classDef.type

            val isBillingClass = billingPrefixes.any { prefix ->
                className.startsWith(prefix)
            }

            if (!isBillingClass) return@classDefForEach

            val hasBillingMethods = classDef.methods.any { method ->
                method.name in setOf(
                    "startConnection", "queryPurchasesAsync", "queryPurchasesExtraParams",
                    "consumeAsync", "acknowledgePurchase", "acknowledgePurchaseExtraParams",
                    "launchBillingFlow", "isBillingSupported", "isBillingSupportedExtraParams",
                    "consumePurchase", "consumePurchaseExtraParams",
                    "isReady", "endConnection",
                    "isFeatureSupported", "isFeatureSupportedExtraParams",
                    "queryPurchaseHistory", "queryPurchaseHistoryAsync",
                )
            }

            if (!hasBillingMethods) return@classDefForEach

            logger.info("Found billing class: $className")

            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.methods.forEach { method ->
                if (method.implementation == null) return@forEach

                val methodName = method.name
                val returnType = method.returnType
                val paramCount = method.parameterTypes.size

                if (methodName == "startConnection" && returnType == "V" && paramCount == 1) {
                    method.addInstructions(0, """
                        invoke-static/range {p1 .. p1}, $EXTENSION_CLASS->handleStartConnection(Ljava/lang/Object;)V
                        return-void
                    """.trimIndent())
                    patchedCount++
                    callbackCount++
                    logger.info("  ✓ $methodName() → calls onBillingSetupFinished(OK)")
                }

                if ((methodName == "queryPurchasesAsync" ||
                    methodName == "queryPurchasesExtraParams") &&
                    returnType == "V" && paramCount == 2
                ) {
                    method.addInstructions(0, """
                        invoke-static/range {p2 .. p2}, $EXTENSION_CLASS->handleQueryPurchases(Ljava/lang/Object;)V
                        return-void
                    """.trimIndent())
                    patchedCount++
                    callbackCount++
                    logger.info("  ✓ $methodName() → calls onQueryPurchasesResponse(OK, empty)")
                }

                if (methodName == "consumeAsync" && returnType == "V" && paramCount == 2) {
                    method.addInstructions(0, """
                        invoke-static/range {p1 .. p1}, $EXTENSION_CLASS->handleConsumeAsync(Ljava/lang/Object;Ljava/lang/Object;)V
                        return-void
                    """.trimIndent())
                    patchedCount++
                    callbackCount++
                    logger.info("  ✓ $methodName() → calls onConsumeResponse(OK)")
                }

                if ((methodName == "acknowledgePurchase" ||
                    methodName == "acknowledgePurchaseExtraParams") &&
                    returnType == "V" && paramCount == 2
                ) {
                    method.addInstructions(0, """
                        invoke-static/range {p1 .. p1}, $EXTENSION_CLASS->handleAcknowledgePurchase(Ljava/lang/Object;Ljava/lang/Object;)V
                        return-void
                    """.trimIndent())
                    patchedCount++
                    callbackCount++
                    logger.info("  ✓ $methodName() → calls onAcknowledgePurchaseResponse(OK)")
                }

                if (methodName == "launchBillingFlow" && paramCount == 2) {
                    method.addInstructions(0, """
                        invoke-static/range {p0 .. p0}, $EXTENSION_CLASS->handleLaunchBillingFlow(Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v0
                        return-object v0
                    """.trimIndent())
                    patchedCount++
                    callbackCount++
                    logger.info("  ✓ $methodName() → calls onPurchasesUpdated(OK) + returns OK")
                }

                if ((methodName == "isBillingSupported" ||
                    methodName == "isBillingSupportedExtraParams") &&
                    returnType == "I"
                ) {
                    method.addInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $methodName() = 0 (OK)")
                }

                if ((methodName == "consumePurchase" ||
                    methodName == "consumePurchaseExtraParams") &&
                    returnType == "I"
                ) {
                    method.addInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $methodName() = 0 (success)")
                }

                if (methodName == "isReady" && returnType == "Z") {
                    method.addInstructions(0, """
                        const/4 v0, 0x1
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $methodName() = true")
                }

                if (methodName == "endConnection" && returnType == "V") {
                    method.addInstructions(0, """
                        return-void
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $methodName() = void (no-op)")
                }

                if ((methodName == "isFeatureSupported" ||
                    methodName == "isFeatureSupportedExtraParams") &&
                    returnType == "I"
                ) {
                    method.addInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $methodName() = 0 (supported)")
                }

                if ((methodName == "queryPurchaseHistory" ||
                    methodName == "queryPurchaseHistoryAsync") &&
                    returnType == "V"
                ) {
                    method.addInstructions(0, """
                        return-void
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $methodName() = void (skip)")
                }
            }
        }

        if (patchedCount == 0) {
            throw PatchException(
                "No Google Play Billing classes found in this app. " +
                "The app may not use Google Play In-App Billing."
            )
        }

        logger.info("Billing bypass complete: $patchedCount methods patched ($callbackCount with callbacks)")
    }
}
