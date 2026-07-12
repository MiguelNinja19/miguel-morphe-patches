package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA

private const val EXTENSION_CLASS = "Ldiozz/cubex/patches/extension/BillingBypass;"

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips Google Play Billing and simulates successful " +
        "purchases by creating a fake Purchase object and calling the " +
        "billing callback directly via an extension.",
    default = true,
) {
    compatibleWith(POLYTOPIA)
    extendWith("extensions/extension.mpe")

    execute {
        // Encontrar a classe BillingClientImpl
        val billingClientImpl = classDefBy("Lcom/android/billingclient/api/BillingClientImpl;")
            ?: throw Exception("BillingClientImpl not found")

        val mutableClass = mutableClassDefBy(billingClientImpl)

        // Patchear launchBillingFlow para chamar a extensão
        mutableClass.methods.find { 
            it.name == "launchBillingFlow" && it.parameterTypes.size == 2 
        }?.let { method ->
            if (method.implementation != null) {
                method.addInstructions(0, """
                    invoke-static {p0, p1}, $EXTENSION_CLASS->handleLaunchBillingFlow(Lcom/android/billingclient/api/BillingClient;Ljava/lang/Object;)Lcom/android/billingclient/api/BillingResult;
                    move-result-object v0
                    return-object v0
                """.trimIndent())
                println("✓ Patched launchBillingFlow to call BillingBypass extension")
            }
        }
    }
}
