/*
 * Fingerprints for the PairIP license check in Duck Life 4
 * (com.mad.ducklife, v2026.5.12 / versionCode 300084).
 *
 * Duck Life 4 ships the full PairIP V2 client (com.pairip.licensecheck)
 * in classes2.dex of the base APK, WITHOUT the native libpairipcore.so
 * VM (the arm64 split only has libunity, libil2cpp, libmain and
 * lib_burst_generated - verified). So the license check lives purely
 * at the Java level and no-opping two methods disables it completely:
 *
 *  - LicenseContentProvider.onCreate() (registered in the manifest,
 *    runs before the Application class) does:
 *        new LicenseClient(getContext()).initializeLicenseCheck()
 *  - LicenseClient.checkLicense(Context) (public static convenience)
 *    does exactly the same thing.
 *
 * Neither the game's own Java code, the C# IL2CPP side (zero "pairip"
 * strings in global-metadata.dat) nor the native libraries reference
 * PairIP anywhere else, so after these two no-ops the licensing
 * service is never contacted and the LicenseActivity paywall /
 * close-app screen can never trigger.
 *
 * This is the same PairIP build as Supreme Duelist (same method set,
 * including the V2 ILicenseV2ResultListener callback), so the proven
 * Supreme-style two no-ops apply.
 */

package com.mad.ducklife.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * LicenseClient.checkLicense(Context) - public static, returns V.
 *
 * Smali (classes2.dex, v2026.5.12):
 *   .method public static checkLicense(Landroid/content/Context;)V
 *     new-instance v0, Lcom/pairip/licensecheck/LicenseClient;
 *     invoke-direct {v0, v1}, Lcom/pairip/licensecheck/LicenseClient;-><init>(Landroid/content/Context;)V
 *     invoke-virtual {v0}, Lcom/pairip/licensecheck/LicenseClient;->initializeLicenseCheck()V
 *     return-void
 *
 * The methodCall filter (invoke of initializeLicenseCheck on the same
 * class) disambiguates it from every other static method in the class.
 */
object LicenseCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    name = "checkLicense",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
            name = "initializeLicenseCheck",
        ),
    )
)

/**
 * LicenseClient.initializeLicenseCheck() - public, returns V.
 *
 * Smali head (classes2.dex, v2026.5.12): a LicenseCheckState switch
 * that ends up calling connectToLicensingService() ->
 * LicenseResponseHelper.validateResponse(...) -> paywall / closeApp
 * on failure. The name is required for disambiguation because the
 * class also has a public void reportSuccessfulLicenseCheck() with
 * the same access flags and no parameters.
 */
object InitializeLicenseCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    name = "initializeLicenseCheck",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
)
