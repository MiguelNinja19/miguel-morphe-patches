package jpark.aos5.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import jpark.aos5.patches.shared.Constants.ANGER_OF_STICK_5
import jpark.aos5.patches.shared.LaunchPurchaseFlowFingerprint
import jpark.aos5.patches.shared.RestorePurchasesFingerprint
import jpark.aos5.patches.shared.SetRestoreFingerprint

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips Google Play Billing and credits IAP items (gem packs, " +
        "coin packs, starter packs) directly. Tap any buy button in the shop " +
        "and the item is granted instantly without payment. If the Contacting " +
        "screen stays, just press the back button to dismiss it. Also disables " +
        "the startup purchase-restore flow.",
    default = true,
) {
    compatibleWith(ANGER_OF_STICK_5)

    execute {
        val method = LaunchPurchaseFlowFingerprint.method
        method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                invoke-static {p1, v0}, Lorg/cocos2dx/cpp/AppActivity;->nativeOnSuccess(Ljava/lang/String;Z)V
                return-void
            """.trimIndent()
        )

        SetRestoreFingerprint.method.addInstructions(0, "return-void")
        RestorePurchasesFingerprint.method.addInstructions(0, "return-void")
    }
}
