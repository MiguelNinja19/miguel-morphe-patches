package fi.twomenandadog.zombiecatchers.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * org.cocos2dx.lib.Cocos2dxHelper.getIntegerForKey(String, int)I
 * Reads an integer from Cocos2dxPrefsFile SharedPreferences.
 */
object GetIntegerForKeyFingerprint : Fingerprint(
    definingClass = "Lorg/cocos2dx/lib/Cocos2dxHelper;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "I",
    parameters = listOf("Ljava/lang/String;", "I"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/content/Context;",
            name = "getSharedPreferences",
        ),
    )
)

/**
 * org.cocos2dx.lib.Cocos2dxHelper.getBoolForKey(String, boolean)Z
 * Reads a boolean from Cocos2dxPrefsFile SharedPreferences.
 */
object GetBoolForKeyFingerprint : Fingerprint(
    definingClass = "Lorg/cocos2dx/lib/Cocos2dxHelper;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;", "Z"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/content/Context;",
            name = "getSharedPreferences",
        ),
    )
)

/**
 * fi.twomenandadog.zombiecatchers.util.Security.verifyPurchase(String, String, String)Z
 */
object VerifyPurchaseFingerprint : Fingerprint(
    definingClass = "Lfi/twomenandadog/zombiecatchers/util/Security;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/text/TextUtils;",
            name = "isEmpty",
        ),
    )
)

/**
 * com.pairip.SignatureCheck.verifyIntegrity(Context)V
 * PairIP signature verification. Checks APK signature hash against
 * expected value. If mismatch (re-signed), throws SignatureTamperedException
 * which redirects to Play Store.
 *
 * Patch to return-void (skip check entirely).
 */
object SignatureCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/SignatureCheck;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/content/pm/PackageManager;",
            name = "getPackageInfo",
        ),
    )
)

/**
 * com.pairip.licensecheck.LicenseClient.checkLicense(Context)V
 * PairIP license verification. Connects to Google Play Licensing
 * Service to verify the app was installed from Play Store.
 * If not licensed, shows "Get this app from Play" screen.
 *
 * Patch to return-void (skip check entirely).
 */
object LicenseCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
)
