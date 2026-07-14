package app.morphe.patches.all.misc.billing

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.rawResourcePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.logging.Logger

@Suppress("unused")
val billingBypassPatch = bytecodePatch(
    name = "Billing bypass",
    description = "Attempts to credit purchases by scanning the app for " +
        "billing code and applying the appropriate bypass. Runs 5 phases: " +
        "(1) Cocos2d-x helper — finds app-level success methods. " +
        "(2) Google Play Billing — patches Purchase.isAcknowledged and " +
        "getPurchaseState. (3) Unity billing — patches zzbq bridge " +
        "callbacks. (4) Unity IL2CPP hex patch — patches libil2cpp.so " +
        "to bypass IsProductUnlocked and suppress error dialogs. " +
        "(5) Fallback — patches billing to return success without crediting.",
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

        logger.info("Billing bypass: starting 5-phase scan")

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
        // Phase 4: Unity IL2CPP Hex Patch
        // Scans libil2cpp.so (if present) for known unlock/error method
        // signatures and patches ARM64 code to bypass them.
        //
        // Patterns based on Polytopia dump (Il2CppInspectorRedux, metadata v39).
        // Each 32-byte pattern is unique. May match other Unity IL2CPP games
        // with similar method signatures.
        // ================================================================
        logger.info("Phase 4 (Unity IL2CPP hex patch): scanning for libil2cpp.so...")

        val libil2cppPath = try {
            val f = get("lib/arm64-v8a/libil2cpp.so")
            if (f != null) "lib/arm64-v8a/libil2cpp.so" else null
        } catch (e: Exception) { null }

        if (libil2cppPath != null) {
            logger.info("Phase 4 (Unity IL2CPP hex patch): found $libil2cppPath")

            try {
                val libFile = get(libil2cppPath)
                val libBytes = libFile.readBytes()

                val returnVoid = byteArrayOf(
                    0xC0.toByte(), 0x03.toByte(), 0x5F.toByte(), 0xD6.toByte(),
                    0x1F.toByte(), 0x20.toByte(), 0x03.toByte(), 0xD5.toByte()
                )
                val returnTrue = byteArrayOf(
                    0x20.toByte(), 0x00.toByte(), 0x80.toByte(), 0x52.toByte(),
                    0xC0.toByte(), 0x03.toByte(), 0x5F.toByte(), 0xD6.toByte()
                )

                // Known IL2CPP method signatures (from Polytopia dump)
                // These may match other Unity games with similar billing code
                val hexPatches = listOf(
                    Triple(
                        byteArrayOf(
                            0xFE.toByte(), 0x5F.toByte(), 0xBD.toByte(), 0xA9.toByte(),
                            0xF6.toByte(), 0x57.toByte(), 0x01.toByte(), 0xA9.toByte(),
                            0xF4.toByte(), 0x4F.toByte(), 0x02.toByte(), 0xA9.toByte(),
                            0x74.toByte(), 0xB1.toByte(), 0x00.toByte(), 0x90.toByte(),
                            0xF3.toByte(), 0x03.toByte(), 0x00.toByte(), 0x2A.toByte(),
                            0x88.toByte(), 0x96.toByte(), 0x57.toByte(), 0x39.toByte(),
                            0xA8.toByte(), 0x05.toByte(), 0x00.toByte(), 0x37.toByte(),
                            0x80.toByte(), 0x94.toByte(), 0x00.toByte(), 0x90.toByte()
                        ),
                        returnVoid,
                        "ShowPurchaseErrorPopup -> ret"
                    ),
                    Triple(
                        byteArrayOf(
                            0xFF.toByte(), 0x83.toByte(), 0x01.toByte(), 0xD1.toByte(),
                            0xFE.toByte(), 0x6F.toByte(), 0x01.toByte(), 0xA9.toByte(),
                            0xFA.toByte(), 0x67.toByte(), 0x02.toByte(), 0xA9.toByte(),
                            0xF8.toByte(), 0x5F.toByte(), 0x03.toByte(), 0xA9.toByte(),
                            0xF6.toByte(), 0x57.toByte(), 0x04.toByte(), 0xA9.toByte(),
                            0xF4.toByte(), 0x4F.toByte(), 0x05.toByte(), 0xA9.toByte(),
                            0x77.toByte(), 0xB1.toByte(), 0x00.toByte(), 0x90.toByte(),
                            0xF3.toByte(), 0x03.toByte(), 0x03.toByte(), 0x2A.toByte()
                        ),
                        returnVoid,
                        "OnProductPurchasedCallback -> ret"
                    ),
                    Triple(
                        byteArrayOf(
                            0xFF.toByte(), 0x03.toByte(), 0x02.toByte(), 0xD1.toByte(),
                            0xFD.toByte(), 0x7B.toByte(), 0x02.toByte(), 0xA9.toByte(),
                            0xFC.toByte(), 0x6F.toByte(), 0x03.toByte(), 0xA9.toByte(),
                            0xFA.toByte(), 0x67.toByte(), 0x04.toByte(), 0xA9.toByte(),
                            0xF8.toByte(), 0x5F.toByte(), 0x05.toByte(), 0xA9.toByte(),
                            0xF6.toByte(), 0x57.toByte(), 0x06.toByte(), 0xA9.toByte(),
                            0xF4.toByte(), 0x4F.toByte(), 0x07.toByte(), 0xA9.toByte(),
                            0x54.toByte(), 0xB1.toByte(), 0x00.toByte(), 0x90.toByte()
                        ),
                        returnTrue,
                        "IsProductUnlocked -> return true"
                    ),
                    Triple(
                        byteArrayOf(
                            0xFE.toByte(), 0x5F.toByte(), 0xBD.toByte(), 0xA9.toByte(),
                            0xF6.toByte(), 0x57.toByte(), 0x01.toByte(), 0xA9.toByte(),
                            0xF4.toByte(), 0x4F.toByte(), 0x02.toByte(), 0xA9.toByte(),
                            0x57.toByte(), 0xB1.toByte(), 0x00.toByte(), 0x90.toByte(),
                            0x76.toByte(), 0x94.toByte(), 0x00.toByte(), 0x90.toByte(),
                            0xF5.toByte(), 0x03.toByte(), 0x02.toByte(), 0xAA.toByte(),
                            0xE8.toByte(), 0xEE.toByte(), 0x57.toByte(), 0x39.toByte(),
                            0xD6.toByte(), 0x3A.toByte(), 0x45.toByte(), 0xF9.toByte()
                        ),
                        returnVoid,
                        "OnPurchaseProduct -> ret"
                    )
                )

                var hexPatchedCount = 0
                for ((pattern, replacement, description) in hexPatches) {
                    val idx = findPattern(libBytes, pattern)
                    if (idx >= 0) {
                        for (i in replacement.indices) {
                            libBytes[idx + i] = replacement[i]
                        }
                        hexPatchedCount++
                        patchedMethods.add("libil2cpp.so: $description")
                        logger.info("  patched: libil2cpp.so $description")
                    }
                }

                if (hexPatchedCount > 0) {
                    libFile.writeBytes(libBytes)
                    logger.info("  Phase 4: $hexPatchedCount hex patches applied to libil2cpp.so")
                } else {
                    logger.info("  Phase 4: no known IL2CPP patterns matched")
                }
            } catch (e: Exception) {
                logger.info("  Phase 4: failed to patch libil2cpp.so: ${e.message}")
            }
        } else {
            logger.info("Phase 4 (Unity IL2CPP hex patch): no libil2cpp.so found")
        }

        // ================================================================
        // Check if Phase 2 + 3 + 4 succeeded
        // ================================================================
        if (patchedMethods.isNotEmpty()) {
            logger.info("Billing bypass COMPLETE (Phases 2+3+4)")
            logger.info("Patched ${patchedMethods.size} method(s):")
            patchedMethods.forEach { logger.info("  - $it") }
            return@execute
        }

        // ================================================================
        // Phase 5: Fallback
        // ================================================================
        logger.info("Phase 5 (Fallback): patching generic billing methods")

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
   
