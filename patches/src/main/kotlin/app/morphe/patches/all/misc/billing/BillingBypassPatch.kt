package app.morphe.patches.all.misc.billing

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.rawResourcePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import java.util.logging.Logger

private fun findPattern(haystack: ByteArray, needle: ByteArray): Int {
    if (needle.isEmpty() || haystack.size < needle.size) return -1
    val lastStart = haystack.size - needle.size
    for (i in 0..lastStart) {
        var found = true
        for (j in needle.indices) {
            if (haystack[i + j] != needle[j]) {
                found = false
                break
            }
        }
        if (found) return i
    }
    return -1
}

@Suppress("unused")
val unityIl2CppHexPatch = rawResourcePatch(
    name = "Unity IL2CPP hex patch",
    description = "Patches libil2cpp.so to bypass Unity IL2CPP billing validation.",
    default = false,
) {
    execute {
        val logger = Logger.getLogger("Il2CppHex")
        val libPath = try {
            val f = get("lib/arm64-v8a/libil2cpp.so")
            if (f != null && f.exists()) "lib/arm64-v8a/libil2cpp.so" else null
        } catch (e: Exception) { null }

        if (libPath == null) {
            logger.info("IL2CPP hex patch: no libil2cpp.so found, skipping")
            return@execute
        }
        logger.info("IL2CPP hex patch: found " + libPath)

        try {
            val libFile = get(libPath)
            val libBytes = libFile.readBytes()
            val returnVoid = byteArrayOf(
                0xC0.toByte(), 0x03.toByte(), 0x5F.toByte(), 0xD6.toByte(),
                0x1F.toByte(), 0x20.toByte(), 0x03.toByte(), 0xD5.toByte()
            )
            val returnTrue = byteArrayOf(
                0x20.toByte(), 0x00.toByte(), 0x80.toByte(), 0x52.toByte(),
                0xC0.toByte(), 0x03.toByte(), 0x5F.toByte(), 0xD6.toByte()
            )

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

            var patchedCount = 0
            for ((pattern, replacement, description) in hexPatches) {
                val idx = findPattern(libBytes, pattern)
                if (idx >= 0) {
                    for (i in replacement.indices) {
                        libBytes[idx + i] = replacement[i]
                    }
                    patchedCount++
                    logger.info("  patched: " + description)
                }
            }

            if (patchedCount > 0) {
                libFile.writeBytes(libBytes)
                logger.info("IL2CPP hex patch COMPLETE: " + patchedCount + "/" + hexPatches.size + " patches applied")
            } else {
                logger.info("IL2CPP hex patch: no known patterns matched")
            }
        } catch (e: Exception) {
            logger.info("IL2CPP hex patch failed: " + e.message)
        }
    }
}

@Suppress("unused")
val billingBypassPatch = bytecodePatch(
    name = "Billing bypass",
    description = "Attempts to credit purchases by scanning the app for " +
        "billing code and applying the appropriate bypass. Runs 4 phases.",
    default = false,
) {
    dependsOn(unityIl2CppHexPatch)

    execute {
        val logger = Logger.getLogger("BillingBypass")
        val billingPrefixes = setOf(
            "Lcom/android/billingclient/api/",
            "Lcom/android/vending/billing/",
            "Lcom/google/android/gms/iap/",
        )
        val patchedMethods = mutableListOf<String>()
        logger.info("Billing bypass: starting 4-phase scan")

        // Phase 1: Cocos2d-x Helper
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
            logger.info("Phase 1 (Cocos2d-x helper): found " + successMethodClass + "->" + successMethodName)
            val mutableClass = mutableClassDefBy(wrapperClassName)
            val wrapperMethod = mutableClass.methods.find {
                it.name == wrapperMethodName && it.parameterTypes.isNotEmpty() &&
                it.parameterTypes[0].toString() == "Ljava/lang/String;"
            }
            if (wrapperMethod != null && wrapperMethod.implementation != null) {
                val paramCount = successMethodParamTypes.size
                val isStatic = successMethodIsStatic
                val paramSig = successMethodParamTypes.joinToString("")
                val sb = StringBuilder()
                if (isStatic && paramCount == 2) {
                    sb.append("const/4 v0, 0x1\n")
                    sb.append("invoke-static {p1, v0}, ")
                } else if (isStatic && paramCount == 1) {
                    sb.append("invoke-static {p1}, ")
                } else if (!isStatic && paramCount == 2) {
                    sb.append("const/4 v0, 0x1\n")
                    sb.append("invoke-virtual {p0, p1, v0}, ")
                } else if (!isStatic && paramCount == 1) {
                    sb.append("invoke-virtual {p0, p1}, ")
                } else {
                    sb.append("invoke-static {p1}, ")
                }
                sb.append(successMethodClass)
                sb.append("->")
                sb.append(successMethodName)
                sb.append("(")
                sb.append(paramSig)
                sb.append(")V")
                wrapperMethod.addInstructions(0, sb.toString())
                patchedMethods.add(successMethodClass + "->" + successMethodName)
                logger.info("Billing bypass COMPLETE (Phase 1)")
                logger.info("Patched " + patchedMethods.size + " method(s):")
                patchedMethods.forEach { logger.info("  - " + it) }
                return@execute
            }
        }
        logger.info("Phase 1: no success method found")

        // Phase 2: Google Play Billing
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
                    patchedMethods.add("Purchase.getPurchaseState -> 1")
                    logger.info("  patched: Purchase.getPurchaseState -> 1")
                }
            }
        } else {
            logger.info("Phase 2: no Purchase class found")
        }

        // Phase 3: Unity Billing
        var foundBridge = false
        classDefForEach { classDef ->
            val className = classDef.type
            if (!className.startsWith("Lcom/android/billingclient/api/zz")) return@classDefForEach
            if (!classDef.methods.any { it.name == "nativeOnPurchasesUpdated" }) return@classDefForEach
            foundBridge = true
            logger.info("Phase 3 (Unity billing): found bridge " + className)
            val mutableBridge = mutableClassDefBy(classDef)

            mutableBridge.methods.find {
                it.name == "onBillingSetupFinished" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0].toString() == "Lcom/android/billingclient/api/BillingResult;"
            }?.let { method ->
                if (method.implementation != null) {
                    val nativeField = classDef.fields.find { it.type == "J" }
                    val fieldName = nativeField?.name ?: "zza"
                    val sb = StringBuilder()
                    sb.append("iget-wide v0, p0, ")
                    sb.append(className)
                    sb.append("->")
                    sb.append(fieldName)
                    sb.append(":J\n")
                    sb.append("const/4 v2, 0x0\n")
                    sb.append("const-string v3, \"\"\n")
                    sb.append("invoke-static {v2, v3, v0, v1}, ")
                    sb.append(className)
                    sb.append("->nativeOnBillingSetupFinished(ILjava/lang/String;J)V\n")
                    sb.append("return-void")
                    method.addInstructions(0, sb.toString())
                    patchedMethods.add(className + ".onBillingSetupFinished -> force success")
                    logger.info("  patched: " + className + ".onBillingSetupFinished -> force success")
                }
            }

            mutableBridge.methods.find { it.name == "onPurchasesUpdated" && it.parameterTypes.size == 2 }?.let { method ->
                if (method.implementation != null) {
                    val sb = StringBuilder()
                    sb.append("invoke-virtual {p1}, Lcom/android/billingclient/api/BillingResult;->getResponseCode()I\n")
                    sb.append("move-result v0\n")
                    sb.append("if-eqz v0, :continue\n")
                    sb.append("return-void\n")
                    sb.append(":continue\n")
                    sb.append("nop")
                    method.addInstructions(0, sb.toString())
                    patchedMethods.add(className + ".onPurchasesUpdated -> swallow errors")
                    logger.info("  patched: " + className + ".onPurchasesUpdated -> swallow errors")
                }
            }
        }
        if (!foundBridge) {
            logger.info("Phase 3: no zzbq bridge found")
        }

        if (patchedMethods.isNotEmpty()) {
            logger.info("Billing bypass COMPLETE (Phases 2+3 + IL2CPP hex patch)")
            logger.info("Patched " + patchedMethods.size + " method(s):")
            patchedMethods.forEach { logger.info("  - " + it) }
            return@execute
        }
                // Phase 4: Fallback
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
                    patchedMethods.add(className + "." + methodName + " -> 0")
                    logger.info("  patched: " + className + "." + methodName + " -> 0")
                }
                if (methodName == "isReady" && returnType == "Z") {
                    method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
                    patchedMethods.add(className + ".isReady -> true")
                    logger.info("  patched: " + className + ".isReady -> true")
                }
                if (methodName == "getPurchaseState" && returnType == "I") {
                    method.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
                    patchedMethods.add(className + ".getPurchaseState -> 0")
                    logger.info("  patched: " + className + ".getPurchaseState -> 0")
                }
            }
        }

        if (patchedMethods.isEmpty()) {
            throw PatchException("No Google Play Billing classes found in this app.")
        }

        logger.info("Billing bypass COMPLETE (Phase 4: Fallback)")
        logger.info("WARNING: Purchases NOT credited. Only billing errors suppressed.")
        logger.info("Patched " + patchedMethods.size + " method(s):")
        patchedMethods.forEach { logger.info("  - " + it) }
    }
}
