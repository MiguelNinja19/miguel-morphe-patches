package jpark.aos5.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

object PurchaseFingerprint : Fingerprint(
    definingClass = "Lorg/cocos2dx/cpp/AppActivity;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        methodCall(
            definingClass = "Lorg/cocos2dx/cpp/AppActivity;",
            name = "launchPurchaseFlow",
            parameters = listOf("Ljava/lang/String;"),
            returnType = "V",
        ),
    )
)

object LaunchPurchaseFlowFingerprint : Fingerprint(
    definingClass = "Lorg/cocos2dx/cpp/AppActivity;",
    accessFlags = listOf(AccessFlags.PRIVATE),
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/android/billingclient/api/BillingClient;",
            name = "launchBillingFlow",
        ),
    )
)

object BillingStartLambdaFingerprint : Fingerprint(
    definingClass = "Lorg/cocos2dx/cpp/AppActivity;",
    accessFlags = listOf(AccessFlags.SYNTHETIC),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lorg/cocos2dx/cpp/AppActivity;",
            name = "handleConnectionFailure",
            returnType = "V",
        ),
    )
)

object SetRestoreFingerprint : Fingerprint(
    definingClass = "Lorg/cocos2dx/cpp/AppActivity;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/android/billingclient/api/BillingClient;",
            name = "newBuilder",
            returnType = "Lcom/android/billingclient/api/BillingClient\$Builder;",
        ),
    )
)

object RestorePurchasesFingerprint : Fingerprint(
    definingClass = "Lorg/cocos2dx/cpp/AppActivity;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/android/billingclient/api/BillingClient;",
            name = "newBuilder",
            returnType = "Lcom/android/billingclient/api/BillingClient\$Builder;",
        ),
    )
)
