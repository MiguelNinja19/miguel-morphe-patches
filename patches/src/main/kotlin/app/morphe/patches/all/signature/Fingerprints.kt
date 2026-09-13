package app.morphe.patches.all.signature

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

/**
 * Matches the static constructor of the injected
 * SignatureSpoofApplication extension class, which holds the two
 * placeholder constants patched at patch time with the real package
 * name and original signature of the app.
 */
object SignatureSpoofApplicationCtorFingerprint : Fingerprint(
    definingClass = Constants.SPOOF_CLASS_SMALI_NAME,
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        string("PACKAGE_NAME_PLACEHOLDER"),
        string("SIGNATURE_PLACEHOLDER")
    )
)
