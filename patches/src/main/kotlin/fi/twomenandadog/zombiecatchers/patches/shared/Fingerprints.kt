package fi.twomenandadog.zombiecatchers.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

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

object LicenseCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
)

/**
 * com.pairip.StartupLauncher.launch()V
 * Executes encrypted VM bytecode. Patch to return-void.
 */
object StartupLauncherFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/StartupLauncher;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/pairip/VMRunner;",
            name = "invoke",
        ),
    )
)

/**
 * com.pairip.licensecheck.LicenseActivity.onStart()V
 * Patch to finish() immediately.
 */
object LicenseActivityOnStartFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseActivity;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/pairip/licensecheck/LicenseActivity" + "${'$'}" + "ActivityType;",
            name = "ordinal",
        ),
    )
)
/**
 * fi.twomenandadog.zombiecatchers.ZCActivity.openPlayStoreZCPage()V
 * Called by C++ via JNI to open the Play Store listing.
 * This is what redirects the user to "Get this app from Play".
 * Patch to return-void to prevent the redirect.
 */
object OpenPlayStoreFingerprint : Fingerprint(
    definingClass = "Lfi/twomenandadog/zombiecatchers/ZCActivity;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/content/Context;",
            name = "startActivity",
        ),
    )
)
