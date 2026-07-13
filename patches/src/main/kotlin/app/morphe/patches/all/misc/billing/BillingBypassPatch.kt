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
    description = "Attempts to credit purchases by scanning the app for " +
        "billing code and applying the appropriate bypass. Runs 4 phases: " +
        "(1) Cocos2d-x helper — finds app-level success methods. " +
        "(2) Google Play Billing — patches Purchase.isAcknowledged and " +
        "getPurchaseState. (3) Unity billing — patches zzbq bridge " +
        "callbacks (onBillingSetupFinished, onPurchasesUpdated). " +
        "(4) Fallback — patches billing to return success without crediting.",
    default = false,
) {
    execute {
        val logger = Logger.getLogger("BillingBypass")

        val billingPrefixes = setOf(
            "Lcom/android/billingclient/api/",
            "Lcom/android/vending/billing/",
            "Lcom/google/android/gms/iap/",
        )

        val patchedMethods = mutableListOf<String>()

        logger.info("Billing bypass: starting 4-phase scan")

        // ================================================================
        // Phase 1: Cocos2d-x Helper
        // ================================================================
        val successMethodPatterns = listOf(
            "nativeOnSuccess", "onPurchaseSuccess", "onIAPSuccess",
            "onBillingSuccess", "onSuccess", "purchaseSuccess",
            "deliverItem", "unlockItem", "creditPurchase",
            "handlePurchase", "processPurchase", "giveItem",
            "addPurchase", "grantPurchase",
        )

        var foundSmartBypass = false
        var successMethodClass = ""
        var successMethodName = ""
        var successMethodParamTypes: List<String> = emptyList()
        var successMethodIsStatic = false
        var wrapperClassName = ""
        var wrapperMethodName = ""

        classDefForEach { classDef ->
            if (foundSmartBypass) return@classDefForEach
            val className = classDef.type
            if (billingPrefixes.any { className.startsWith(it) }) return@classDefForEach
            if (className.startsWith("Landroid/") || className.startsWith("Ljava/") ||
                className.startsWith("Lkotlin/") || className.startsWith("Lcom/google/") ||
                className.startsWith("Lcom/unity3d/")) return@classDefForEach

            val callsLaunchBillingFlow = classDef.methods.any { method ->
                method.implementation?.instructions?.any { insn ->
                    if (insn is ReferenceInstruction) {
                        val ref = insn.reference
                        if (ref is MethodReference) ref.name == "launchBillingFlow" else false
                    } else false
                } ?: false
            }
            if (!callsLaunchBillingFlow) return@classDefForEach

            val successMethod = classDef.methods.find { method ->
                val name = method.name
                val matchedPattern = successMethodPatterns.any { pattern ->
                    name.equals(pattern, ignoreCase = true) || name.contains(pattern, ignoreCase = true)
                }
                if (!matchedPattern) return@find false
                val params = method.parameterTypes
                if (params.isEmpty()) return@find false
                val firstParam = params[0].toString()
                firstParam == "Ljava/lang/String;" || firstParam == "Ljava/lang/Object;" || firstParam.startsWith("L")
            }

            if (successMethod != null) {
                foundSmartBypass = true
                successMethodClass = className
                successMethodName = successMethod.name
                // FIX: parameterTypes returns List<CharSequence>, convert to List<String>
                successMethodParamTypes = successMethod.parameterTypes.map { it.toString() }
                successMethodIsStatic = successMethod.accessFlags.and(0x8) != 0
            }

            val wrapperMethod = classDef.methods.find { method ->
                method.implementation?.instructions?.any { insn ->
                    if (insn is ReferenceInstruction) {
                        val ref = insn.reference
                        ref is MethodReference && ref.name == "launchBillingFlow"
                    } else false
                } ?: false
                && method.parameterTypes.isNotEmpty()
                && method.parameterTypes[0].toString() == "Ljava/lang/String;"
            }
            if (wrapperMethod != null) {
                wrapperClassName = className
                wrapperMethodName = wrapperMethod.name
            }
        }

        if (foundSmartBypass && wrapperClassName.isNotEmpty()) {
            logger.info("Phase 1 (Cocos2d-x helper): found $successMethodClass->$successMethodName")

            val mutableClass = mutableClassDefBy(wrapperClassName)
            val wrapperMethod = mutableClass.methods.find {
                it.name == wrapperMethodName && it.parameterTypes.isNotEmpty() &&
                it.parameterTypes[0].toString() == "Ljava/lang/String;"
            }

            if (wrapperMethod != null && wrapperMethod.implementation != null) {
                val paramCount = successMethodParamTypes.size
                val isStatic = successMethodIsStatic
                val paramSig = successMethodParamTypes.joinToString("")

                val smali = when {
                    isStatic && paramCount == 2 -> "const/4 v0, 0x1\ninvoke-static {p1, v0}, $successMethodClass->$successMethodName($paramSig)V"
                    isStatic && paramCount == 1 -> "invoke-static {p1}, $successMethodClass->$successMethodName($paramSig)V"
                    !isStatic && paramCount == 2 -> "const/4 v0, 0x1\ninvoke-virtual {p0, p1, v0}, $successMethodClass->$successMethodName($paramSig)V"
                    !isStatic && paramCount == 1 -> "invoke-virtual {p0, p1}, $successMethodClass->$successMethodName($paramSig)V"
                    else -> "invoke-static {p1}, $successMethodClass->$successMethodName($paramSig)V"
                }

                wrapperMethod.addInstructions(0, smali)
                patchedMethods.add("$successMethodClass->$successMethodName (wrapper: $wrapperClassName->$wrapperMethodName)")
                logger.info("Billing bypass COMPLETE (Phase 1: Cocos2d-x helper)")
                logger.info("Patched ${patchedMethods.size} method(s):")
                patchedMethods.forEach { logger.info("  - $it") }
                return@execute
            }
        }

        logger.info("Phase 1 (Cocos2d-x helper): no success method found")

        // ================================================================
        // Phase 2: Google Play Billing — Purchase methods
        // ================================================================
        val purchaseClass = classDefByOrNull("Lcom/android/billingclient/api/Purchase;")

        if (purchaseClass != null) {
            logger.info("Phase 2 (Google Play Billing): found Purchase class")
            val mutablePurchase = mutableClassDefBy(purchaseClass)

            mutablePurchase.methods.find { it.name == "isAcknowledged" && it.returnType == "Z" }?.let { method ->
                if (method.implementation != null) {
                    method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
                    patchedMethods.add("Purchase.isAcknowledged -> true")
                    logger.info("  patched: Purchase.isAcknowledged -> true")
                }
            }

            mutablePurchase.methods.find { it.name == "getPurchaseState" && it.returnType == "I" }?.let { method ->
                if (method.implementation != null) {
                    method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
                    patchedMethods.add("Purchase.getPurchaseState -> 1 (PURCHASED)")
                    logger.info("  patched: Purchase.getPurchaseState -> 1 (PURCHASED)")
                }
            }
        } else {
            logger.info("Phase 2 (Google Play Billing): no Purchase class found")
        }

        // ================================================================
        // Phase 3: Unity Billing — zzbq bridge callbacks
        // ================================================================
        var foundBridge = false

        classDefForEach { classDef ->
            val className = classDef.type
            if (!className.startsWith("Lcom/android/billingclient/api/zz")) return@classDefForEach
            if (!classDef.methods.any { it.name == "nativeOnPurchasesUpdated" }) return@classDefForEach

            foundBridge = true
            logger.info("Phase 3 (Unity billing): found bridge $className")

            val mutableBridge = mutableClassDefBy(classDef)

            mutableBridge.methods.find {
                it.name == "onBillingSetupFinished" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0].toString() == "Lcom/android/billingclient/api/BillingResult;"
            }?.let { method ->
                if (method.implementation != null) {
                    val nativeField = classDef.fields.find { it.type == "J" }
                    val fieldName = nativeField?.name ?: "zza"
                    method.addInstructions(0, """
                        iget-wide v0, p0, $className->$fieldName:J
                        const/4 v2, 0x0
                        const-string v3, ""
                        invoke-static {v2, v3, v0, v1}, ${className}->nativeOnBillingSetupFinished(ILjava/lang/String;J)V
                        return-void
                    """.trimIndent())
                    patchedMethods.add("$className.onBillingSetupFinished -> force success")
                    logger.info("  patched: $className.onBillingSetupFinished -> force success")
                }
            }

            mutableBridge.methods.find { it.name == "onPurchasesUpdated" && it.parameterTypes.size == 2 }?.let { method ->
                if (method.implementation != null) {
                    method.addInstructions(0, """
                        invoke-virtual {p1}, Lcom/android/billingclient/api/BillingResult;->getResponseCode()I
                        move-result v0
                        if-eqz v0, :continue
                        return-void
                        :continue
                        nop
                    """.trimIndent())
                    patchedMethods.add("$className.onPurchasesUpdated -> swallow errors")
                    logger.info("  patched: $className.onPurchasesUpdated -> swallow errors")
                }
            }
        }

        if (!foundBridge) {
            logger.info("Phase 3 (Unity billing): no zzbq bridge found")
        }

        // ================================================================
        // Check if Phase 2 + 3 succeeded
        // ================================================================
        if (patchedMethods.isNotEmpty()) {
            logger.info("Billing bypass COMPLETE (Phase 2+3: Google Play Billing + Unity bridge)")
            logger.info("Patched ${patchedMethods.size} method(s):")
            patchedMethods.forEach { logger.info("  - $it") }
            return@execute
        }

        // ================================================================
        // Phase 4: Fallback
        // ================================================================
        logger.info("Phase 4 (Fallback): patching generic billing methods")

        classDefForEach { classDef ->
            val className = classDef.type
            if (!billingPrefixes.any { className.startsWith(it) }) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)
            mutableClass.methods.forEach { method ->
                if (method.implementation == null) return@forEach
                val methodName = method.name
                val returnType = method.returnType

                if ((methodName == "isBillingSupported" || methodName == "isBillingSupportedExtraParams") && returnType == "I") {
                    method.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
                    patchedMethods.add("$className.$methodName -> 0")
                    logger.info("  patched: $className.$methodName -> 0")
                }
                if (methodName == "isReady" && returnType == "Z") {
                    method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
                    patchedMethods.add("$className.isReady -> true")
                    logger.info("  patched: $className.isReady -> true")
                }
                if (methodName == "getPurchaseState" && returnType == "I") {
                    method.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
                    patchedMethods.add("$className.getPurchaseState -> 0")
                    logger.info("  patched: $className.getPurchaseState -> 0")
                }
            }
        }

        if (patchedMethods.isEmpty()) {
            throw PatchException("No Google Play Billing classes found in this app.")
        }

        logger.info("Billing bypass COMPLETE (Phase 4: Fallback)")
        logger.info("WARNING: Purchases NOT credited. Only billing errors suppressed.")
        logger.info("Patched ${patchedMethods.size} method(s):")
        patchedMethods.forEach { logger.info("  - $it") }
    }
}
