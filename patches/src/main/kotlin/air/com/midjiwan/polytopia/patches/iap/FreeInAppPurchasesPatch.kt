package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA

private const val EXTENSION_CLASS = "Ldiozz/cubex/patches/extension/BillingBypass;"

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips Google Play Billing by intercepting launchBillingFlow. " +
        "Creates a fake Purchase and calls nativeOnPurchasesUpdated directly.",
    default = false,
) {
    compatibleWith(POLYTOPIA)
    extendWith("extensions/extension.mpe")

    execute {
        val billingClientImpl = classDefBy("Lcom/android/billingclient/api/BillingClientImpl;")
            ?: throw Exception("BillingClientImpl not found")

        val mutableClass = mutableClassDefBy(billingClientImpl)

        // Only patch launchBillingFlow — don't touch startConnection
        // The app will try to connect to Play Store and fail, but
        // launchBillingFlow will still be intercepted to credit purchases
        mutableClass.methods.find {
            it.name == "launchBillingFlow" && it.parameterTypes.size == 2
        }?.let { method ->
            if (method.implementation != null) {
                method.addInstructions(0, """
                    invoke-static/range {p0 .. p2}, $EXTENSION_CLASS->handleLaunchBillingFlow(Lcom/android/billingclient/api/BillingClient;Ljava/lang/Object;Ljava/lang/Object;)Lcom/android/billingclient/api/BillingResult;
                    move-result-object v0
                    return-object v0
                """.trimIndent())
                println("✓ Patched launchBillingFlow")
            }
        }
    }
}
