package app.morphe.patches.all.misc.ads

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch
import java.util.logging.Logger

@Suppress("unused")
val universalOfflineModePatch = bytecodePatch(
    name = "Universal offline mode",
    description = "Makes the app think it has no internet connection by patching network-checking methods.",
    default = false,
) {
    execute {
        val logger = Logger.getLogger("UniversalOfflineMode")

        val offlineMethodNames = setOf(
            "isOnline", "isConnected", "isConnectedToInternet",
            "isNetworkAvailable", "hasInternet", "checkInternet",
            "isWifiConnected", "isMobileConnected", "hasNetwork",
        )

        var patchedCount = 0

        classDefForEach { classDef ->
            val className = classDef.type

            if (className.startsWith("Landroid/") ||
                className.startsWith("Ljava/") ||
                className.startsWith("Lkotlin/") ||
                className.startsWith("Lkotlinx/")
            ) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.methods.forEach { method ->
                if (method.implementation == null) return@forEach

                val methodName = method.name
                val returnType = method.returnType

                if (methodName in offlineMethodNames && returnType == "Z") {
                    method.replaceInstructions(0, """
                        const/4 v0, 0x0
                        return v0
                    """.trimIndent())
                    patchedCount++
                    logger.info("Patched: $className->$methodName() = false (offline)")
                }
            }
        }

        logger.info("Universal offline mode: Patched $patchedCount network-checking methods")
    }
}
