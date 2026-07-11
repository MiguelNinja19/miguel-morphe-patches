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
        "purchases. Calls billing callbacks (onBillingSetupFinished, " +
        "onPurchasesUpdated, onQueryPurchasesResponse, onConsumeResponse, " +
        "onAcknowledgePurchaseResponse) with success results via an extension. " +
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

            logger.info("Found billing class: $className")

            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.methods.forEach { method ->
                if (method.implementation == null) return@forEach

                val methodName = method.name
                val returnType = method.returnType
                val paramCount = method.parameterTypes.size

                // --- Methods that call callbacks via extension ---

                // startConnection(BillingClientStateListener) → call callback + return void
                if (methodName == "startConnection" && returnType == "V" && paramCount == 1) {
                    method.addInstructions(0, """
                        invoke-static {p1}, $EXTENSION_CLASS->handleStartConnection(Ljava/lang/Object;)V
                        return-void
                    """.trimIndent())
                    patchedCount++
                    callbackCount++
                    logger.info("  ✓ $methodName() → calls onBillingSetupFinished(OK)")
                }

                // queryPurchasesAsync(params, listener) → call callback + return void
                if ((methodName == "queryPurchasesAsync" ||
                    methodName == "queryPurchasesExtraParams") &&
                    returnType == "V" && paramCount == 2
                ) {
                    method.addInstructions(0, """
                        invoke-static {p2}, $EXTENSION_CLASS->handleQueryPurchases(Ljava/lang/Object;)V
                        return-void
                    """.trimIndent())
                    patchedCount++
                    callbackCount++
                    logger.info("  ✓ $methodName() → calls onQueryPurchasesResponse(OK, empty)")
                }

                // consumeAsync(params, listener) → call callback + return void
                if (methodName == "consumeAsync" && returnType == "V" && paramCount == 2) {
                    method.addInstructions(0, """
                        invoke-static {p1, p2}, $EXTENSION_CLASS->handleConsumeAsync(Ljava/lang/Object;Ljava/lang/Object;)V
                        return-void
                    """.trimIndent())
                    patchedCount++
                    callbackCount++
                    logger.info("  ✓ $methodName() → calls onConsumeResponse(OK)")
                }

                // acknowledgePurchase(params, listener) → call callback + return void
                if ((methodName == "acknowledgePurchase" ||
                    methodName == "acknowledgePurchaseExtraParams") &&
                    returnType == "V" && paramCount == 2
                ) {
                    method.addInstructions(0, """
                        invoke-static {p1, p2}, $EXTENSION_CLASS->handleAcknowledgePurchase(Ljava/lang/Object;Ljava/lang/Object;)V
                        return-void
                    """.trimIndent())
                    patchedCount++
                    callbackCount++
                    logger.info("  ✓ $methodName() → calls onAcknowledgePurchaseResponse(OK)")
                }

                // launchBillingFlow(activity, params) → call callback + return BillingResult.OK
                if (methodName == "launchBillingFlow" && paramCount == 2) {
                    method.addInstructions(0, """
                        invoke-static {p0}, $EXTENSION_CLASS->handleLaunchBillingFlow(Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v0
                        check-cast v0, Lcom/android/billingclient/api/BillingResult;
                        return-object v0
                    """.trimIndent())
                    patchedCount++
                    callbackCount++
                    logger.info("  ✓ $methodName() → calls onPurchasesUpdated(OK) + returns OK")
                }

                // --- Simple return methods (no callback needed) ---

                // isBillingSupported → return 0 (OK)
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

                // consumePurchase → return 0 (success) — IInAppBillingService v3
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

                // isReady → return true
                if (methodName == "isReady" && returnType == "Z") {
                    method.addInstructions(0, """
                        const/4 v0, 0x1
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $methodName() = true")
                }

                // endConnection → return void (no-op)
                if (methodName == "endConnection" && returnType == "V") {
                    method.addInstructions(0, """
                        return-void
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $methodName() = void (no-op)")
                }

                // isFeatureSupported → return 0 (supported)
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

                // queryPurchaseHistory → return void (skip)
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
