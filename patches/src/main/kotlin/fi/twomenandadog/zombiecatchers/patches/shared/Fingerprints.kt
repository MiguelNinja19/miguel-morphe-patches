package fi.twomenandadog.zombiecatchers.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * org.cocos2dx.lib.Cocos2dxHelper.getIntegerForKey(String, int)I
 * Reads an integer from Cocos2dxPrefsFile SharedPreferences.
 * Used by the game to read PlutoniumBalance, CoinsBalance, etc.
 *
 * We patch this to return 999999999 when the key ends with "Balance".
 *
 * Smali:
 *   .method public static getIntegerForKey(Ljava/lang/String;I)I
 *   .locals 3
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
 * Used by the game to check if items are unlocked/purchased.
 *
 * We patch this to return true for unlock/purchase keys.
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
 * Verifies purchase signature. Patch to always return true.
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
