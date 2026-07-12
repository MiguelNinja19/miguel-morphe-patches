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
        "Also supports Unity IL2CPP games by finding nativeOnPurchasesUpdated " +
        "JNI bridge methods and creating fake Purchase objects. " +
        "If no success method is found, patches billing to return success " +
        "without crediting.",
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

        // ================================================================
        // Phase 1: SMART bypass — find app-level success methods
        // Works for: Cocos2d-x games (AOS5), custom Java apps
        // ================================================================
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

        logger.info("Phase 1: Scanning for app-level success methods...")

        classDefForEach { classDef ->
            if (foundSmartBypass) return@classDefForEach

            val className = classDef.type

            if (billingPrefixes.any { className.startsWith(it) }) return@classDefForEach
            if (className.startsWith("Landroid/") ||
                className.startsWith("Ljava/") ||
                className.startsWith("Lkotlin/")
            ) return@classDefForEach

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

            logger.info("  Found billing wrapper class: $className")

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
                logger.info("  Found success method: $className->${successMethod.name}")
            }

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
            }
        }

        if (foundSmartBypass && wrapperClassName.isNotEmpty()) {
            logger.info("Phase 1: Applying SMART billing bypass...")

            val mutableClass = mutableClassDefBy(wrapperClassName)
            val wrapperMethod = mutableClass.methods.find {
                it.name == wrapperMethodName &&
                it.parameterTypes.isNotEmpty() &&
                it.parameterTypes[0].toString() == "Ljava/lang/String;"
            }

            if (wrapperMethod != null && wrapperMethod.implementation != null) {
                val paramCount = successMethodParamTypes.size
                val isStatic = successMethodIsStatic
                val paramSig = successMethodParamTypes.joinToString("") { it.toString() }

                val smali = when {
                    isStatic && paramCount == 2 -> {
                        """
                            const/4 v0, 0x1
                            invoke-static {p1, v0}, $successMethodClass->$successMethodName($paramSig)V
                        """.trimIndent()
                    }
                    isStatic && paramCount == 1 -> {
                        """
                            invoke-static {p1}, $successMethodClass->$successMethodName($paramSig)V
                        """.trimIndent()
                    }
                    !isStatic && paramCount == 2 -> {
                        """
                            const/4 v0, 0x1
                            invoke-virtual {p0, p1, v0}, $successMethodClass->$successMethodName($paramSig)V
                        """.trimIndent()
                    }
                    !isStatic && paramCount == 1 -> {
                        """
                            invoke-virtual {p0, p1}, $successMethodClass->$successMethodName($paramSig)V
                        """.trimIndent()
                    }
                    else -> {
                        """
                            invoke-static {p1}, $successMethodClass->$successMethodName($paramSig)V
                        """.trimIndent()
                    }
                }

                wrapperMethod.addInstructions(0, smali)
                logger.info("  ✓ Injected: $smali")
                logger.info("Billing bypass complete (SMART mode)")
                return@execute
            }
        }

        // ================================================================
        // Phase 2: Unity IL2CPP bypass — find nativeOnPurchasesUpdated
        // Works for: Unity IL2CPP games (Polytopia, etc.)
        // Uses extension to create fake Purchase and call native method
        // ================================================================
        logger.info("Phase 2: Scanning for Unity IL2CPP billing bridge...")

        var unityBridgeClass = ""
        var unityBridgeFound = false

        classDefForEach { classDef ->
            if (unityBridgeFound) return@classDefForEach

            val className = classDef.type

            if (!className.startsWith("Lcom/android/billingclient/api/")) return@classDefForEach

            val hasNativeOnPurchasesUpdated = classDef.methods.any { method ->
                method.name == "nativeOnPurchasesUpdated"
            }

            if (hasNativeOnPurchasesUpdated) {
                unityBridgeClass = className
                unityBridgeFound = true
                logger.info("  Found Unity IL2CPP billing bridge: $className")
            }
        }

        if (unityBridgeFound) {
            logger.info("Phase 2: Applying Unity IL2CPP billing bypass...")

            val billingClientImpl = classDefByOrNull("Lcom/android/billingclient/api/BillingClientImpl;")
            if (billingClientImpl != null) {
                val mutableBilling = mutableClassDefBy(billingClientImpl)

                mutableBilling.methods.find {
                    it.name == "launchBillingFlow" && it.parameterTypes.size == 2
                }?.let { method ->
                    if (method.implementation != null) {
                        val returnType = method.returnType
                        if (returnType == "Lcom/android/billingclient/api/BillingResult;") {
                            method.addInstructions(0, """
                                invoke-static/range {p0 .. p2}, Ldiozz/cubex/patches/extension/BillingBypass;->handleLaunchBillingFlow(Lcom/android/billingclient/api/BillingClient;Ljava/lang/Object;Ljava/lang/Object;)Lcom/android/billingclient/api/BillingResult;
                                move-result-object v0
                                return-object v0
                            """.trimIndent())
                            logger.info("  ✓ launchBillingFlow → BillingBypass extension")
                        }
                    }
                }
            }

            logger.info("Billing bypass complete (Unity IL2CPP mode)")
            return@execute
        }

        // ================================================================
        // Phase 3: Fallback — patch billing library methods only
        // ================================================================
        logger.info("Phase 3: No success method found. Applying fallback...")

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
