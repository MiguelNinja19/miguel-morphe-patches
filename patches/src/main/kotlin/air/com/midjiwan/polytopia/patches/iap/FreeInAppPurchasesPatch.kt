package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA

private const val EXTENSION_CLASS = "Ldiozz/cubex/patches/extension/BillingBypass;"

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips Google Play Billing and simulates successful " +
        "purchases by calling the Unity JNI bridge directly. Also ensures " +
        "the store is marked as connected during initialization.",
    default = true,
) {
    compatibleWith(POLYTOPIA)
    extendWith("extensions/extension.mpe")

    execute {
        // 1. Patch onBillingSetupFinished to call nativeOnBillingSetupFinished(0, "", 0)
        //    This tells C# the store is "connected"
        val bridgeClass = classDefByOrNull { classDef ->
            classDef.type.startsWith("Lcom/android/billingclient/api/zz") &&
            classDef.methods.any { it.name == "nativeOnPurchasesUpdated" }
        } ?: throw Exception("Unity billing bridge class not found")

        val mutableBridge = mutableClassDefBy(bridgeClass)

        // Patch onBillingSetupFinished
        mutableBridge.methods.find {
            it.name == "onBillingSetupFinished" && it.implementation != null
        }?.let { method ->
            method.addInstructions(0, """
                invoke-static {}, $EXTENSION_CLASS->handleBillingSetupFinished()V
                return-void
            """.trimIndent())
            println("✓ Patched onBillingSetupFinished → calls nativeOnBillingSetupFinished(0)")
        }

        // 2. Patch launchBillingFlow to create fake purchase
        val billingClientImpl = classDefBy("Lcom/android/billingclient/api/BillingClientImpl;")
            ?: throw Exception("BillingClientImpl not found")

        val mutableBilling = mutableClassDefBy(billingClientImpl)

        mutableBilling.methods.find {
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
