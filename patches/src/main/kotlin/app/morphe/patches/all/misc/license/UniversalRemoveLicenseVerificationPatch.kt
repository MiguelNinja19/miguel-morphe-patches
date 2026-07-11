package app.morphe.patches.all.misc.license

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch
import java.util.logging.Logger

@Suppress("unused")
val universalRemoveLicenseVerificationPatch = bytecodePatch(
    name = "Universal remove license verification",
    description = "Patches Google Play License Verification Library (LVL) classes to always return licensed.",
    default = false,
) {
    execute {
        val logger = Logger.getLogger("UniversalRemoveLicenseVerification")

        val lvlPrefixes = setOf(
            "Lcom/android/vending/licensing/",
            "Lcom/google/android/finsky/licensing/",
        )

        var patchedCount = 0

        classDefForEach { classDef ->
            val className = classDef.type

            val isLvlClass = lvlPrefixes.any { prefix ->
                className.startsWith(prefix)
            }

            if (!isLvlClass) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.methods.forEach { method ->
                if (method.implementation == null) return@forEach

                val methodName = method.name
                val returnType = method.returnType

                if (methodName == "allowAccess" && returnType == "Z") {
                    method.replaceInstructions(0, """
                        const/4 v0, 0x1
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->allowAccess() = true")
                }

                if (methodName == "processServerData" && returnType == "V") {
                    method.replaceInstructions(0, """
                        return-void
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->processServerData() = void")
                }

                if (methodName == "verify" && returnType == "Z") {
                    method.replaceInstructions(0, """
                        const/4 v0, 0x1
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->verify() = true")
                }

                if (methodName == "checkAccess" && returnType == "V") {
                    method.replaceInstructions(0, """
                        return-void
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->checkAccess() = void")
                }
            }
        }

        logger.info("Universal remove license verification: Patched $patchedCount LVL methods")
    }
}
