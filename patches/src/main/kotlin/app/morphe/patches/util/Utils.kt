package app.morphe.patches.util

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.apk.ApkSignatureScheme
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * Local utilities that replace the helpers the official Morphe
 * patches library provides (app.morphe.util.*), since this repo does
 * not depend on that library.
 *
 * Ported from:
 *  - hoo-dles/morphe-patches — hoodles.morphe.util.ResourceUtils (cert helpers)
 *  - MorpheApp/morphe-patches — app.morphe.util.returnEarly
 */

/**
 * Prepend an early return to the method resolved by this fingerprint.
 *
 * @param intValue    value returned for I/Z/S/B/C returning methods (default 0)
 * @param stringValue value returned for reference returning methods
 * @throws PatchException when the fingerprint has no match or the
 *                        return type is unsupported
 */
fun Fingerprint.returnEarly(intValue: Int = 0, stringValue: String? = null) {
    val method = this.method
    val smali = when (method.returnType.firstOrNull()) {
        'V' -> "return-void"
        'I', 'Z', 'S', 'B', 'C' -> "const/4 v0, $intValue\nreturn v0"
        'J' -> "const-wide v0, 0x0\nreturn-wide v0"
        'L', '[' -> if (stringValue != null) {
            "const-string v0, \"$stringValue\"\nreturn-object v0"
        } else {
            "const/4 v0, 0x0\nreturn-object v0"
        }
        else -> throw PatchException("returnEarly: unsupported return type ${method.returnType}")
    }
    method.addInstructions(0, smali)
}

/**
 * Same as [returnEarly] but never throws: returns false when the
 * fingerprint does not match the app (useful for universal "all"
 * patches that must degrade gracefully on apps without GMS code).
 */
fun Fingerprint.returnEarlyOrNull(intValue: Int = 0, stringValue: String? = null): Boolean =
    runCatching {
        returnEarly(intValue, stringValue)
        true
    }.getOrDefault(false)

// ─────────────────────────────────────────────────────────────────────
// Certificate helpers (port of hoodles.morphe.util.ResourceUtils)
// ─────────────────────────────────────────────────────────────────────

class NoCertificateException : Exception("Unable to extract certificate from apk")

private fun sortOrder(scheme: ApkSignatureScheme) = when (scheme) {
    ApkSignatureScheme.V31 -> 0
    ApkSignatureScheme.V3 -> 1
    ApkSignatureScheme.V2 -> 2
    else -> 99
}

/**
 * Returns the end entity (leaf) certificate of the app's signing
 * certificate chain, preferring the highest available signature
 * scheme (v3.1 > v3 > v2).
 */
fun getEndEntityCertificate(
    schemeToCertsMap: Map<ApkSignatureScheme, List<X509Certificate>>
): X509Certificate {
    // Scheme map can have empty lists for some reason.
    val filteredMap = schemeToCertsMap.filterValues { it.isNotEmpty() }

    val highestSchemeVersion = filteredMap.keys.minByOrNull { sortOrder(it) }
        ?: throw NoCertificateException()
    val certsForScheme = filteredMap[highestSchemeVersion] ?: throw NoCertificateException()

    if (certsForScheme.isEmpty()) throw NoCertificateException()

    // If single/self-signed, it is the developer certificate.
    if (certsForScheme.size == 1) {
        return certsForScheme.first()
    }

    // If a certificate chain exists, find the leaf (end entity).
    val issuerPrincipals = certsForScheme.map { it.issuerX500Principal }.toSet()
    return certsForScheme.firstOrNull { cert ->
        !issuerPrincipals.contains(cert.subjectX500Principal)
    } ?: certsForScheme.first()
}

/** Well-known SHA-1 fingerprints of re-signed / debug APKs. */
val KNOWN_SHA1 = listOf(
    "e94e3afa40a54ecee4eef83f580393507fcd205a", // AntiSplit M
    "61ed377e85d386a8dfee6b864bd85b0bfaa5af81", // public debug certificate
)

/**
 * @return true if the certificate looks like it is NOT the original
 * app certificate (e.g. the APK was already re-signed by another
 * tool before patching — spoofing that would be pointless).
 */
fun isCertMaybeInauthentic(cert: X509Certificate): Boolean {
    if (cert.subjectX500Principal.name.contains("morphe", true))
        return true

    val digest = MessageDigest.getInstance("SHA-1").digest(cert.encoded)
    val sha1 = digest.joinToString("") { "%02x".format(it) }
    return KNOWN_SHA1.contains(sha1.lowercase())
}

/** SHA-1 fingerprint of a certificate as lowercase hex (no colons). */
fun X509Certificate.sha1(): String =
    MessageDigest.getInstance("SHA-1").digest(encoded)
        .joinToString("") { "%02x".format(it) }
