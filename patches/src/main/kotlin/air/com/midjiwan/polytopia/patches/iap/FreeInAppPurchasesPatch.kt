package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA

private const val EXTENSION_CLASS = "Ldiozz/cubex/patches/extension/BillingBypass;"

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips Google Play Billing by intercepting startConnection " +
        "and launchBillingFlow. Store is marked as connected immediately, " +
        "and purchases are credited via fake Purchase objects.",
    default = false,
) {
    compatibleWith(POLYTOPIA)
    extendWith("extensions/extension.mpe")

    execute {
        val billingClientImpl = classDefBy("Lcom/android/billingclient/api/BillingClientImpl;")
            ?: throw Exception("BillingClientImpl not found")

        val mutableClass = mutableClassDefBy(billingClientImpl)

        // 1. Patch startConnection(BillingClientStateListener) to call onBillingSetupFinished(OK)
        // This fixes the "Waiting..." issue — app thinks store is connected
        mutableClass.methods.find {
            it.name == "startConnection" &&
            it.parameterTypes.size == 1 &&
            it.implementation != null
        }?.let { method ->
            method.addInstructions(0, """
                invoke-static {p1}, $EXTENSION_CLASS->handleStartConnection(Lcom/android/billingclient/api/BillingClientStateListener;)V
                return-void
            """.trimIndent())
            println("✓ Patched startConnection → calls onBillingSetupFinished(OK)")
        }

        // 2. Patch launchBillingFlow to create fake Purchase
        mutableClass.methods.find {
            it.name == "launchBillingFlow" && it.parameterTypes.size == 2
        }?.let { method ->
            if (method.implementation != null) {
                method.addInstructions(0, """
                    invoke-static/range {p0 .. p2}, $EXTENSION_CLASS->handleLaunchBillingFlow(Lcom/android/billingclient/api/BillingClient;Ljava/lang/Object;Ljava/lang/Object;)Lcom/android/billingclient/api/BillingResult;
                    move-result-object v0
                    return-object v0
                """.trimIndent())
                println("✓ Patched launchBillingFlow → creates fake Purchase")
            }
        }
    }
}
