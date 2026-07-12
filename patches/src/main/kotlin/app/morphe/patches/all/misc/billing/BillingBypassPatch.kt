package app.morphe.patches.all.misc.billing

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.logging.Logger

@Suppress("unused")
val billingBypassPatch = bytecodePatch(
    name = "Billing bypass",
    description = "Attempts to credit purchases by finding the app's own " +
        "success callback method and calling it directly. Scans for methods " +
        "like nativeOnSuccess, onPurchaseSuccess, onIAPSuccess, etc. " +
        "If no success method is found, patches billing to return success " +
        "without crediting.",
    default = false,
) {
    execute {
        val logger = Logger.getLogger("BillingBypass")

        val billingPrefixes = setOf(
            "Lcom/android/billingclient/api/",
            "Lcom/android/vending/billing/",
            "Lcom/google/android/gms/iap/",
        )

        // Success method name patterns to search for
        val successMethodPatterns = listOf(
            "nativeOnSuccess", "onPurchaseSuccess", "onIAPSuccess",
            "onBillingSuccess", "onSuccess", "purchaseSuccess",
            "deliverItem", "unlockItem", "creditPurchase",
            "onPurchasesUpdated", "handlePurchase", "processPurchase",
            "giveItem", "addPurchase", "grantPurchase",
        )

        // Method names that are app-level purchase wrappers
        val purchaseWrapperNames = setOf(
            "launchPurchaseFlow", "purchase", "buyItem", "buy",
            "startPurchase", "makePurchase", "initiatePurchase",
        )

        var foundSmartBypass = false
        var successMethodClass = ""
        var successMethodName = ""
        var successMethodParams = ""
        var wrapperClassName = ""
        var wrapperMethodName = ""

        logger.info("Scanning for billing success methods...")

        // Phase 1: Find classes that call launchBillingFlow
        // and have a success-like method
        classDefForEach { classDef ->
            if (foundSmartBypass) return@classDefForEach

            val className = classDef.type

            // Skip billing library classes — we want app classes
            if (billingPrefixes.any { className.startsWith(it) }) return@classDefForEach
            // Skip Android/Java framework
            if (className.startsWith("Landroid/") ||
                className.startsWith("Ljava/") ||
                className.startsWith("Lkotlin/")
            ) return@classDefForEach

            // Check if this class calls launchBillingFlow
            val callsLaunchBillingFlow = classDef.methods.any { method ->
                method.implementation?.instructions?.any { insn ->
                    if (insn is com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction) {
                        val ref = insn.reference
                        if (ref is MethodReference) {
                            ref.name == "launchBillingFlow"
                        } else false
                    } else false
                } ?: false
            }

            if (!callsLaunchBillingFlow) return@classDefForEach

            logger.info("Found billing wrapper class: $className")

            // In this class, find a method that takes a String and has a success-like name
            val successMethod = classDef.methods.find { method ->
                val name = method.name
                val matchedPattern = successMethodPatterns.any { pattern ->
                    name.equals(pattern, ignoreCase = true) ||
                    name.contains(pattern, ignoreCase = true)
                }
                if (!matchedPattern) return@find false

                // Check if it takes at least 1 parameter (usually String sku)
                val params = method.parameterTypes
                if (params.isEmpty()) return@find false

                // First param should be String or similar
                val firstParam = params[0].toString()
                firstParam == "Ljava/lang/String;" ||
                firstParam == "Ljava/lang/Object;" ||
                firstParam.startsWith("L")
            }

            if (successMethod != null) {
                foundSmartBypass = true
                successMethodClass = className
                successMethodName = successMethod.name
                successMethodParams = successMethod.parameterTypes.joinToString("")
                logger.info("Found success method: $className->${successMethod.name}($successMethodParams)")
            }

            // Also find the wrapper method (the one that takes String and calls launchBillingFlow)
            val wrapperMethod = classDef.methods.find { method ->
                method.name in purchaseWrapperNames &&
                method.parameterTypes.isNotEmpty() &&
                method.parameterTypes[0].toString() == "Ljava/lang/String;"
            }

            if (wrapperMethod != null) {
                wrapperClassName = className
                wrapperMethodName = wrapperMethod.name
                logger.info("Found wrapper method: $className->${wrapperMethod.name}")
            } else if (successMethod != null) {
                // If no named wrapper, use any method that calls launchBillingFlow and takes String
                val fallbackWrapper = classDef.methods.find { method ->
                    method.implementation?.instructions?.any { insn ->
                        if (insn is com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction) {
                            val ref = insn.reference
                            ref is MethodReference && ref.name == "launchBillingFlow"
                        } else false
                    } ?: false &&
                    method.parameterTypes.isNotEmpty() &&
                    method.parameterTypes[0].toString() == "Ljava/lang/String;"
                }
                if (fallbackWrapper != null) {
                    wrapperClassName = className
                    wrapperMethodName = fallbackWrapper.name
                    logger.info("Found fallback wrapper: $className->${fallbackWrapper.name}")
                }
            }
        }

        // Phase 2: Apply smart bypass if found
        if (foundSmartBypass && wrapperClassName.isNotEmpty()) {
            logger.info("Applying SMART billing bypass...")

            val mutableClass = mutableClassDefBy(wrapperClassName)
            val wrapperMethod = mutableClass.methods.find {
                it.name == wrapperMethodName &&
                it.parameterTypes.isNotEmpty() &&
                it.parameterTypes[0].toString() == "Ljava/lang/String;"
            }

            if (wrapperMethod != null && wrapperMethod.implementation != null) {
                // Determine the success method signature
                val successMethod = mutableClassDefBy(successMethodClass).methods.find {
                    it.name == successMethodName
                }

                if (successMethod != null) {
                    val paramCount = successMethod.parameterTypes.size
                    val isStatic = successMethod.accessFlags.and(0x8) != 0 // ACC_STATIC

                    // Build smali to call the success method
                    // p0 = this (if not static), p1 = sku (String)
                    val smali = if (isStatic) {
                        if (paramCount == 1) {
                            // static method(String)
                            """
                                invoke-static {p1}, $successMethodClass->$successMethodName(Ljava/lang/String;)V
                            """.trimIndent()
                        } else if (paramCount == 2) {
                            // static method(String, boolean) — like nativeOnSuccess(sku, true)
                            """
                                const/4 v0, 0x1
                                invoke-static/range {p1 .. p1}, $successMethodClass->$successMethodName(Ljava/lang/String;Z)V
                            """.trimIndent()
                        } else {
                            // static method(String, ...) — just pass sku and zeros
                            """
                                invoke-static/range {p1 .. p1}, $successMethodClass->$successMethodName(Ljava/lang/String;)V
                            """.trimIndent()
                        }
                    } else {
                        // instance method — use p0 (this)
                        if (paramCount == 1) {
                            """
                                invoke-virtual {p0, p1}, $successMethodClass->$successMethodName(Ljava/lang/String;)V
                            """.trimIndent()
                        } else if (paramCount == 2) {
                            """
                                const/4 v0, 0x1
                                invoke-virtual/range {p0 .. p1}, $successMethodClass->$successMethodName(Ljava/lang/String;Z)V
                            """.trimIndent()
                        } else {
                            """
                                invoke-virtual {p0, p1}, $successMethodClass->$successMethodName(Ljava/lang/String;)V
                            """.trimIndent()
                        }
                    }

                    wrapperMethod.addInstructions(0, smali)
                    logger.info("✓ Injected success call: $successMethodName at start of $wrapperMethodName")
                    logger.info("Billing bypass complete (SMART mode)")
                    return@execute
                }
            }
        }

        // Phase 3: Fallback — patch billing library methods only
        logger.info("No success method found. Applying fallback billing bypass...")

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

                if ((methodName == "isBillingSupported" ||
                    methodName == "isBillingSupportedExtraParams") &&
                    returnType == "I"
                ) {
                    method.addInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = 0 (OK)")
                }

                if (methodName == "isReady" && returnType == "Z") {
                    method.addInstructions(0, """
                        const/4 v0, 0x1
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("  ✓ $className->$methodName() = true")
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
                    logger.info("  ✓ $className->$methodName() = 0 (supported)")
                }

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

        logger.info("Billing bypass complete (fallback mode): $patchedCount methods patched")
        logger.info("NOTE: Could not find a success method to credit purchases.")
        logger.info("For free purchases, use an app-specific patch.")
    }
}
