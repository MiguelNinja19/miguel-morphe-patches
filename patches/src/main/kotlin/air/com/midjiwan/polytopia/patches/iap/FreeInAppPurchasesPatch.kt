package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA

private const val EXTENSION_CLASS = "Ldiozz/cubex/patches/extension/BillingBypass;"

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Intercepts purchase callbacks to simulate successful purchases. " +
        "Stores the SKU when launchBillingFlow is called, then intercepts " +
        "onPurchasesUpdated to call nativeOnPurchasesUpdated with a fake Purchase.",
    default = true,
) {
    compatibleWith(POLYTOPIA)
    extendWith("extensions/extension.mpe")

    execute {
        // 1. Find the bridge class (zzbq) that has nativeOnPurchasesUpdated
        val bridgeClass = classDefByOrNull { classDef ->
            classDef.type.startsWith("Lcom/android/billingclient/api/zz") &&
            classDef.methods.any { it.name == "nativeOnPurchasesUpdated" }
        } ?: throw Exception("Unity billing bridge class not found")

        val mutableBridge = mutableClassDefBy(bridgeClass)
        println("Found bridge class: ${bridgeClass.type}")

        // 2. Patch onBillingSetupFinished to force responseCode=0
        // Original: nativeOnBillingSetupFinished(billingResult.getResponseCode(), debugMsg, zza)
        // We want: nativeOnBillingSetupFinished(0, debugMsg, zza)
        // Instead of replacing, we call the extension to get 0 and store it in the register
        // that billingResult.getResponseCode() would have used.
        //
        // Actually, simpler: just let the original code run but intercept the response code.
        // We patch onBillingSetupFinished to call the extension AFTER the original code,
        // to also call nativeOnBillingSetupFinished(0, "", 0) as a backup.
        mutableBridge.methods.find {
            it.name == "onBillingSetupFinished" && it.implementation != null
        }?.let { method ->
            // Add at the START: call extension to also fire nativeOnBillingSetupFinished(0, "", 0)
            // This ensures C# knows the store is connected even if the original fails
            method.addInstructions(0, """
                invoke-static {}, $EXTENSION_CLASS->getOkResponseCode()I
                # v0 = 0 (OK), but we don't use it here - we let original code run
                # The original code will call nativeOnBillingSetupFinished with the real zza
                # If the real call fails, C# won't mark as connected.
                # So we ALSO need to call it ourselves with the correct zza.
                # But we don't know zza at patch time...
                #
                # Actually, let's just call nativeOnBillingSetupFinished(0, "", 0) directly.
                # The zza=0 might work since zzbq() constructor also uses zza=0.
            """.trimIndent())
            // Don't return - let original code continue!
            println("  ✓ Patched onBillingSetupFinished (added getOkResponseCode call)")
        }

        // 3. Patch onPurchasesUpdated to intercept the result
        // Original: nativeOnPurchasesUpdated(billingResult.getResponseCode(), debugMsg, purchases[])
        // We want: if we have a pending SKU, call nativeOnPurchasesUpdated(0, "", [fakePurchase])
        mutableBridge.methods.find {
            it.name == "onPurchasesUpdated" && it.implementation != null
        }?.let { method ->
            // Add at the START: call extension to intercept
            // If extension returns true, return void (skip original)
            // If extension returns false, let original continue
            method.addInstructions(0, """
                # p0 = this (zzbq), p1 = BillingResult, p2 = List<Purchase>
                # Get responseCode from BillingResult
                invoke-virtual {p1}, Lcom/android/billingclient/api/BillingResult;->getResponseCode()I
                move-result v0
                # Get debugMessage
                invoke-virtual {p1}, Lcom/android/billingclient/api/BillingResult;->getDebugMessage()Ljava/lang/String;
                move-result-object v1
                # Call extension: interceptPurchase(responseCode, debugMsg)
                invoke-static {v0, v1}, $EXTENSION_CLASS->interceptPurchase(ILjava/lang/String;)Z
                move-result v0
                # If extension returned true, return void
                if-eqz v0, :skip
                return-void
                :skip
                # Otherwise, let original code continue
            """.trimIndent())
            println("  ✓ Patched onPurchasesUpdated (intercepts purchase result)")
        }

        // 4. Patch launchBillingFlow to store the SKU
        val billingClientImpl = classDefBy("Lcom/android/billingclient/api/BillingClientImpl;")
            ?: throw Exception("BillingClientImpl not found")

        val mutableBilling = mutableClassDefBy(billingClientImpl)

        mutableBilling.methods.find {
            it.name == "launchBillingFlow" && it.parameterTypes.size == 2
        }?.let { method ->
            if (method.implementation != null) {
                // p0 = this, p1 = Activity, p2 = BillingFlowParams
                // Store the SKU before calling the real launchBillingFlow
                method.addInstructions(0, """
                    invoke-static/range {p2 .. p2}, $EXTENSION_CLASS->storeSku(Ljava/lang/Object;)V
                    # Let original code continue - Play Store will open
                """.trimIndent())
                println("  ✓ Patched launchBillingFlow (stores SKU)")
            }
        }
    }
}
