package app.morphe.patches.all.misc.signature

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import java.util.logging.Logger

private const val EXTENSION_CLASS = "Ldiozz/cubex/patches/extension/PmsHookApplication;"

@Suppress("unused")
val universalSignatureBypassPatch = bytecodePatch(
    name = "Universal signature bypass",
    description = "Bypasses signature verification by injecting a Proxy of " +
        "IPackageManager that returns the original APK signatures. " +
        "Also spoofs getInstallerPackageName to return 'com.android.vending'. " +
        "Provide the original signatures via the 'signatures-base64' option " +
        "(from 'apksigner verify --print-certs'). " +
        "Equivalent to Lucky Patcher's 'Signature verification killer' (sigkill.dex).",
    default = false,
) {
    extendWith("extensions/extension.mpe")

    val signaturesOption = stringOption(
        key = "signatures-base64",
        default = "",
        title = "Original signatures (Base64)",
        description = "Base64-encoded original APK signatures. " +
            "If empty, only installer package name is spoofed. " +
            "Format: 1 byte count + (4 byte length + signature bytes) for each cert.",
    )

    execute {
        val logger = Logger.getLogger("UniversalSignatureBypass")

        var targetMethod: MutableMethod? = null
        var targetIndex = 0
        var isApplication = false

        classDefForEach { classDef ->
            if (targetMethod != null) return@classDefForEach

            val superclass = classDef.superclass ?: return@classDefForEach

            if (superclass == "Landroid/app/Application;") {
                val mutableClass = mutableClassDefBy(classDef)
                val onCreate = mutableClass.methods.find {
                    it.name == "onCreate" &&
                        it.parameterTypes.isEmpty() &&
                        it.returnType == "V" &&
                        it.implementation != null
                }
                if (onCreate != null) {
                    targetMethod = onCreate
                    targetIndex = 0
                    isApplication = true
                    logger.info("Found Application.onCreate in ${classDef.type}")
                }
            } else if (superclass.contains("Activity") && targetMethod == null) {
                val mutableClass = mutableClassDefBy(classDef)
                val onCreate = mutableClass.methods.find {
                    it.name == "onCreate" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0].toString() == "Landroid/os/Bundle;" &&
                        it.returnType == "V" &&
                        it.implementation != null
                }
                if (onCreate != null) {
                    targetMethod = onCreate
                    targetIndex = 1
                    isApplication = false
                    logger.info("Found Activity.onCreate in ${classDef.type}")
                }
            }
        }

        val method = targetMethod ?: run {
            logger.warning("Could not find Application.onCreate or Activity.onCreate.")
            return@execute
        }

        val signaturesBase64 = signaturesOption.value ?: ""

        val smali = if (signaturesBase64.isBlank()) {
            """
                |invoke-static {p0}, $EXTENSION_CLASS->hook(Landroid/content/Context;)V
            """.trimMargin()
        } else {
            """
                |const-string v0, "$signaturesBase64"
                |invoke-static {p0, v0}, $EXTENSION_CLASS->hook(Landroid/content/Context;Ljava/lang/String;)V
            """.trimMargin()
        }

        method.addInstructions(targetIndex, smali)

        logger.info("Injected signature bypass hook into ${if (isApplication) "Application" else "Activity"}.onCreate")
        if (signaturesBase64.isBlank()) {
            logger.info("No signatures provided — only spoofing installer package name")
        } else {
            logger.info("Signatures provided — full signature bypass enabled")
        }
    }
}
