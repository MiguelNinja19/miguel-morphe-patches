package app.morphe.patches.all.microg

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Verified against The Battle of Polytopia 2.17.2.16299 (2026-09):
 *  - GooglePlayServicesUtilLight.ensurePlayServicesAvailable(Context, I)V
 *    → MATCHES ServiceCheckFingerprint (public static, contains
 *      "Google Play Services not available")
 *  - GooglePlayServicesUtilLight.isGooglePlayServicesAvailable(Context, I)I
 *    → MATCHES IsGooglePlayServicesAvailableFingerprint (public static)
 *  - GoogleApiAvailabilityLight.isGooglePlayServicesAvailable(Context, I)I
 *    → MATCHES the "Light" fingerprint (public instance method)
 *  - GooglePlayUtilityFingerprint → does NOT match Polytopia (its
 *    MetadataValueReader helper is private with a different shape);
 *    kept because it matches other GMS-carrying apps. All uses are
 *    graceful (returnEarlyOrNull) so unmatched apps are skipped.
 */

internal object GooglePlayUtilityFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "I",
    parameters = listOf("L", "I"),
    filters = listOf(
        string("This should never happen."),
        string("MetadataValueReader"),
        string("com.google.android.gms"),
    )
)

internal object IsGooglePlayServicesAvailableFingerprint : Fingerprint(
    name = "isGooglePlayServicesAvailable",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "I",
    parameters = listOf("Landroid/content/Context;", "I")
)

internal object IsGooglePlayServicesAvailableLightFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/gms/common/GoogleApiAvailabilityLight;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "I",
    parameters = listOf("Landroid/content/Context;", "I")
)

internal object ServiceCheckFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("L", "I"),
    filters = listOf(
        string("Google Play Services not available")
    )
)

/** Built lazily by MicroGMetadataPatch (main activity onCreate). */
internal lateinit var MainActivityOnCreateFingerprint: Fingerprint
