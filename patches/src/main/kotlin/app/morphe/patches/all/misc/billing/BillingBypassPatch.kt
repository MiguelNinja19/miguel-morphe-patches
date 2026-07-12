package app.morphe.patches.all.misc.billing

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
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

        val successMethodPatterns = listOf(
            "nativeOnSuccess", "onPurchaseSuccess", "onIAPSuccess",
            "onBillingSuccess", "onSuccess", "purchaseSuccess",
            "deliverItem", "unlockItem", "creditPurchase",
            "handlePurchase", "processPurchase",
            "giveItem", "addPurchase", "grantPurchase",
        )

        var foundSmartBypass = false
        var successMethodClass = ""
        var successMethodName = ""
        var successMethodParamTypes: List<CharSequence> = emptyList()
        var successMethodIsStatic = false
        var wrapperClassName = ""
        var wrapperMethodName = ""

        logger.info("Scanning for billing success methods...")

        // Phase 1: Find classes that call launchBillingFlow and have a success method
        classDefForEach { classDef ->
            if (foundSmartBypass) return@classDefForEach

            val className = classDef.type

            if (billingPrefixes.any { className.startsWith(it) }) return@classDefForEach
            if (className.startsWith("Landroid/") ||
                className.startsWith("Ljava/") ||
                className.startsWith("Lkotlin/")
            ) return@classDefForEach

            // Check if this class calls launchBillingFlow
            val callsLaunchBillingFlow = classDef.methods.any { method ->
                method.implementation?.instructions?.any { insn ->
                    if (insn is ReferenceInstruction) {
                        val ref = insn.reference
                        if (ref is MethodReference) {
                            ref.name == "launchBillingFlow"
                        } else false
                    } else false
                } ?: false
            }

            if (!callsLaunchBillingFlow) return@classDefForEach

            logger.info("Found billing wrapper class: $className")

            // Find success method
            val successMethod = classDef.methods.find { method ->
                val name = method.name
                val matchedPattern = successMethodPatterns.any { pattern ->
                    name.equals(pattern, ignoreCase = true) ||
                    name.contains(pattern, ignoreCase = true)
                }
                if (!matchedPattern) return@find false

                val params = method.parameterTypes
                if (params.isEmpty()) return@find false

                val firstParam = params[0].toString()
                firstParam == "Ljava/lang/String;" ||
                firstParam == "Ljava/lang/Object;" ||
                firstParam.startsWith("L")
            }

            if (successMethod != null) {
                foundSmartBypass = true
                successMethodClass = className
                successMethodName = successMethod.name
                successMethodParamTypes = successMethod.parameterTypes
                successMethodIsStatic = successMethod.accessFlags.and(0x8) != 0
                logger.info("Found success method: $className->${successMethod.name}(${successMethod.parameterTypes.joinToString()})")
                logger.info("  static=$successMethodIsStatic, params=${successMethodParamTypes.size}")
            }

            // Find wrapper method
            val wrapperMethod = classDef.methods.find { method ->
                method.implementation?.instructions?.any { insn ->
                    if (insn is ReferenceInstruction) {
                        val ref = insn.reference
                        ref is MethodReference && ref.name == "launchBillingFlow"
                    } else false
                } ?: false &&
                method.parameterTypes.isNotEmpty() &&
                method.parameterTypes[0].toString() == "Ljava/lang/String;"
            }

            if (wrapperMethod != null) {
                wrapperClassName = className
                wrapperMethodName = wrapperMethod.name
                logger.info("Found wrapper method: $className->${wrapperMethod.name}")
            }
        }

        // Phase 2: Apply smart bypass
        if (foundSmartBypass && wrapperClassName.isNotEmpty()) {
            logger.info("Applying SMART billing bypass...")

            val mutableClass = mutableClassDefBy(wrapperClassName)
            val wrapperMethod = mutableClass.methods.find {
                it.name == wrapperMethodName &&
                it.parameterTypes.isNotEmpty() &&
                it.parameterTypes[0].toString() == "Ljava/lang/String;"
            }

            if (wrapperMethod != null && wrapperMethod.implementation != null) {
                val paramCount = successMethodParamTypes.size
                val isStatic = successMethodIsStatic

                // Build the parameter type string for the smali signature
                val paramSig = successMethodParamTypes.joinToString("") { it.toString() }

                // Build smali — match the working AOS5 patch pattern exactly
                val smali = when {
                    isStatic && paramCount == 2 -> {
                        // static nativeOnSuccess(String, boolean)
                        // Match AOS5: const/4 v0, 0x1; invoke-static {p1, v0}
                        """
                            const/4 v0, 0x1
                            invoke-static {p1, v0}, $successMethodClass->$successMethodName($paramSig)V
                        """.trimIndent()
                    }
                    isStatic && paramCount == 1 -> {
                        // static onSuccess(String)
                        """
                            invoke-static {p1}, $successMethodClass->$successMethodName($paramSig)V
                        """.trimIndent()
                    }
                    !isStatic && paramCount == 2 -> {
                        // instance method(String, boolean)
                        """
                            const/4 v0, 0x1
                            invoke-virtual {p0, p1, v0}, $successMethodClass->$successMethodName($paramSig)V
                        """.trimIndent()
                    }
                    !isStatic && paramCount == 1 -> {
                        // instance method(String)
                        """
                            invoke-virtual {p0, p1}, $successMethodClass->$successMethodName($paramSig)V
                        """.trimIndent()
                    }
                    else -> {
                        // Fallback: just pass p1
                        """
                            invoke-static {p1}, $successMethodClass->$successMethodName($paramSig)V
                        """.trimIndent()
                    }
                }

                wrapperMethod.addInstructions(0, smali)
                logger.info("✓ Injected: $smali")
                logger.info("Billing bypass complete (SMART mode)")
                return@execute
            }
        }

        // Phase 3: Fallback
        logger.info("No success method found. Applying fallback billing bypass...")

        var patchedCount = 0

        classDefForEach { classDef ->
            val className = classDef.type

            if (!billingPrefixes.any { className.startsWith(it) }) return@classDefForEach

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
                }

                if (methodName == "isReady" && returnType == "Z") {
                    method.addInstructions(0, """
                        const/4 v0, 0x1
                        return v0
                    """.trimIndent())
                    patchedCount++
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
                }

                if (methodName == "getPurchaseState" && returnType == "I") {
                    method.addInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                }
            }
        }

        if (patchedCount == 0) {
            throw PatchException("No Google Play Billing classes found in this app.")
        }

        logger.info("Billing bypass complete (fallback): $patchedCount methods patched")
    }
}
