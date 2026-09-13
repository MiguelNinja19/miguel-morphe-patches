package app.morphe.patches.all.microg

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.util.getEndEntityCertificate
import app.morphe.patches.util.isCertMaybeInauthentic
import app.morphe.patches.util.sha1
import com.android.tools.smali.dexlib2.analysis.reflection.util.ReflectionUtils.javaToDexName
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/**
 * Metadata shared by the MicroG patches:
 *  - packageName of the app being patched
 *  - SHA-1 of its ORIGINAL signing certificate (embedded in the
 *    manifest as the SPOOFED_PACKAGE_SIGNATURE meta-data that
 *    ReVanced GmsCore reads)
 *  - fingerprint of the main activity's onCreate (hook point for the
 *    checkGmsCore runtime injection)
 *
 * Ported from hoo-dles/morphe-patches (patches/all/microg/MicroGMetadataPatch.kt).
 */
internal object MicroGMetadata {
    lateinit var packageName: String
    lateinit var signatureSHA1: String
}

private lateinit var mainActivity: String

private val resourceMetadataPatch = resourcePatch {
    execute {
        MicroGMetadata.packageName = packageMetadata.packageName

        val cert = getEndEntityCertificate(packageMetadata.signingCertificates)

        if (isCertMaybeInauthentic(cert)) {
            throw PatchException(
                "Invalid signing certificate. Original APK from the Play Store is required " +
                    "(this one looks re-signed by another tool)."
            )
        }

        MicroGMetadata.signatureSHA1 = cert.sha1()

        document("AndroidManifest.xml").use { document ->
            val xPath = XPathFactory.newInstance().newXPath()

            // First (activity | activity-alias) with a MAIN/LAUNCHER
            // intent-filter: its name, or its targetActivity when it is
            // an alias.
            val expression = """
                (//activity | //activity-alias)[
                    intent-filter/action/@*[local-name()='name'] = 'android.intent.action.MAIN'
                ][1]/@*[
                    (local-name() = 'targetActivity' and parent::activity-alias) or
                    (local-name() = 'name' and parent::activity)
                ]
            """.trimIndent()

            val activityName = xPath.evaluate(expression, document, XPathConstants.STRING) as String
            if (activityName.isBlank()) {
                throw PatchException("Could not find main activity in manifest")
            }

            mainActivity = if (activityName.startsWith(".")) {
                MicroGMetadata.packageName + activityName
            } else {
                activityName
            }
        }
    }
}

internal val microGMetadataPatch = bytecodePatch {
    dependsOn(resourceMetadataPatch)

    execute {
        // Walk up the class hierarchy until the first class that
        // actually declares onCreate (e.g. for Polytopia:
        // MessagingUnityPlayerActivity already declares it; for other
        // apps it may live on a superclass).
        fun firstOnCreateClass(className: String): String {
            val clazz = classDefBy(className)
            val hasOnCreate = clazz.methods.any { it.name == "onCreate" }
            if (hasOnCreate) return className

            return clazz.superclass?.let { firstOnCreateClass(it) }
                ?: throw PatchException("Could not find onCreate method for MicroG hook")
        }

        val onCreateClass = firstOnCreateClass(javaToDexName(mainActivity))
        MainActivityOnCreateFingerprint = Fingerprint(
            definingClass = onCreateClass,
            name = "onCreate",
            returnType = "V",
        )
    }
}
