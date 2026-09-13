/*
 * Spoof signature patch (universal "all apps").
 *
 * Makes a patched (re-signed) app report the ORIGINAL APK signature
 * through PackageManager: replaces PackageInfo.CREATOR with a proxy
 * that swaps in the original certificate for every parcel read.
 *
 * Ported from hoo-dles/morphe-patches (patches/all/signature), the
 * implementation confirmed working for The Battle of Polytopia
 * online multiplayer together with "MicroG integration"
 * (see issue #27).
 *
 * Why it is needed: Google Play Games / Google sign-in validates the
 * calling app by comparing the signature PackageManager reports with
 * the SHA-1 registered in Google's cloud console. A Morphe re-signed
 * APK fails that check; with the spoof, GMS (or ReVanced microG)
 * sees the original Midjiwan certificate and lets the account in.
 *
 * How it works:
 *  1. resourcePatch: reads the original signing certificate of the
 *     APK being patched and sets android:name on <application> to
 *     the spoof Application class (when the app has none — true for
 *     Polytopia and most Unity games).
 *  2. extendWith: injects the extension .mpe (contains
 *     SignatureSpoofApplication).
 *  3. finalize: replaces the two placeholder strings in the
 *     extension <clinit> with the real package name + Base64
 *     certificate, and re-parents any existing app Application
 *     subclass to run the spoof first.
 *
 * Original technique: ApkSignatureKillerEx (L-JINBIN).
 */

package app.morphe.patches.all.signature

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.util.getEndEntityCertificate
import app.morphe.patches.util.isCertMaybeInauthentic
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.w3c.dom.Element
import java.util.Base64

private lateinit var packageName: String
private lateinit var signature: String

private val manifestPatch = resourcePatch {
    execute {
        val cert = getEndEntityCertificate(packageMetadata.signingCertificates)

        if (isCertMaybeInauthentic(cert)) {
            throw PatchException(
                "Invalid signing certificate. Original APK from the Play Store is required " +
                    "(this one looks re-signed by another tool)."
            )
        }

        signature = Base64.getEncoder().encodeToString(cert.encoded)
        packageName = packageMetadata.packageName

        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as Element
            val applicationClass = application.getAttribute("android:name")
            // Only set our class when the app has no Application of its own.
            // (Polytopia and most Unity games have none.) Apps that already
            // declare one are handled in the finalize block by re-parenting.
            if (applicationClass.isEmpty()) {
                application.setAttribute("android:name", Constants.SPOOF_CLASS_JAVA_NAME)
            }
        }
    }
}

@Suppress("unused")
val spoofSignaturePatch = bytecodePatch(
    name = "Spoof signature",
    description = "Spoofs the package signature of the original APK so that " +
        "Google Play Games / Google sign-in accepts the patched app. " +
        "Use together with the 'MicroG integration' patch (required for " +
        "online features, e.g. Polytopia multiplayer with your account).",
    default = false,
) {
    dependsOn(manifestPatch)

    // Inject the runtime extension (contains SignatureSpoofApplication).
    extendWith("extensions/extension.mpe")

    finalize {
        val ctor = SignatureSpoofApplicationCtorFingerprint.method
        val strippedSig = signature.filter { !it.isWhitespace() }

        // Replace the two placeholder constants with the real values.
        val implementation = ctor.implementation
            ?: throw PatchException("Spoof signature: extension <clinit> has no code")

        var replaced = 0
        // Snapshot: replacing while iterating the live list is unsafe.
        implementation.instructions.toList().forEachIndexed { index, instruction ->
            val reference = (instruction as? Instruction21c)?.reference as? StringReference
                ?: return@forEachIndexed

            val newString = when (reference.string) {
                "PACKAGE_NAME_PLACEHOLDER" -> packageName
                "SIGNATURE_PLACEHOLDER" -> strippedSig
                else -> return@forEachIndexed
            }

            ctor.replaceInstruction(
                index,
                BuilderInstruction21c(
                    Opcode.CONST_STRING,
                    instruction.registerA,
                    ImmutableStringReference(newString),
                )
            )
            replaced++
        }

        if (replaced != 2) {
            throw PatchException(
                "Spoof signature: expected to replace 2 placeholders, replaced $replaced"
            )
        }

        // If the app declares its own Application subclass chain, insert our
        // spoof class right before android.app.Application so the spoof runs
        // first. (Not needed for Polytopia — it has no android:name.)
        // NOTE: requires Morphe patches plugin 1.3.4+ (setSuperClass).
        mutableClassDefByOrNull {
            it.type != Constants.SPOOF_CLASS_SMALI_NAME &&
                it.superclass == "Landroid/app/Application;"
        }?.setSuperClass(Constants.SPOOF_CLASS_SMALI_NAME)
    }
}
