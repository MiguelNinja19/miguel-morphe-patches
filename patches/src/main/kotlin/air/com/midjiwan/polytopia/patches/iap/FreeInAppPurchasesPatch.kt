package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA

private const val EXTENSION_CLASS = "Ldiozz/cubex/patches/extension/BillingBypass;"

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips Google Play Billing by forcing store connection " +
        "and creating fake Purchase objects.",
    default = false,
) {
    compatibleWith(POLYTOPIA)
    extendWith("extensions/extension.mpe")

    execute {
        // 1. Patch zzbq.onBillingSetupFinished to force responseCode=0
        // This makes C# think the store is connected
        val bridgeClass = classDefByOrNull { classDef ->
            classDef.type.startsWith("Lcom/android/billingclient/api/zz") &&
            classDef.methods.any { it.name == "nativeOnPurchasesUpdated" }
        } ?: throw Exception("Unity billing bridge class not found")

        val mutableBridge = mutableClassDefBy(bridgeClass)

        mutableBridge.methods.find {
            it.name == "onBillingSetupFinished" && it.implementation != null
        }?.let { method ->
            method.addInstructions(0, """
                invoke-static {p0, p1}, $EXTENSION_CLASS->handleBillingSetupFinished(Ljava/lang/Object;Lcom/android/billingclient/api/BillingResult;)V
                return-void
            """.trimIndent())
            println("✓ Patched onBillingSetupFinished → forces OK")
        }

        // 2. Patch BillingClientImpl.launchBillingFlow to create fake Purchase
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
    }
}
