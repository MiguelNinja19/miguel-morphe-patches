package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA

private const val EXTENSION_CLASS = "Ldiozz/cubex/patches/extension/BillingBypass;"

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips Google Play Billing by creating a fake Purchase " +
        "and calling nativeOnPurchasesUpdated directly via extension.",
    default = false,
) {
    compatibleWith(POLYTOPIA)
    extendWith("extensions/extension.mpe")

    execute {
        // Patch launchBillingFlow to call extension
        val billingClientImpl = classDefBy("Lcom/android/billingclient/api/BillingClientImpl;")
            ?: throw Exception("BillingClientImpl not found")

        val mutableClass = mutableClassDefBy(billingClientImpl)

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

        // Also patch onBillingSetupFinished to force success
        val bridgeClass = classDefByOrNull { classDef ->
            classDef.type.startsWith("Lcom/android/billingclient/api/zz") &&
            classDef.methods.any { it.name == "nativeOnPurchasesUpdated" }
        }

        if (bridgeClass != null) {
            val mutableBridge = mutableClassDefBy(bridgeClass)

            mutableBridge.methods.find {
                it.name == "onBillingSetupFinished" && it.implementation != null
            }?.let { method ->
                method.addInstructions(0, """
                    invoke-static {}, $EXTENSION_CLASS->handleBillingSetupFinished()V
                """.trimIndent())
                println("✓ Patched onBillingSetupFinished")
            }
        }
    }
}
