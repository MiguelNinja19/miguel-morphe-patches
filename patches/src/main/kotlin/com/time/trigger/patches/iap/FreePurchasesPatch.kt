package com.time.trigger.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.time.trigger.patches.shared.BillingResultGetResponseCodeFingerprint
import com.time.trigger.patches.shared.Constants.JOHNNY_TRIGGER

@Suppress("unused")
val freePurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Patches BillingResult.getResponseCode() to always return 0 (OK). " +
        "This makes all billing operations report success, including purchases. " +
        "When you tap buy, the game thinks the purchase was successful without " +
        "contacting Google Play.",
    default = true,
) {
    compatibleWith(JOHNNY_TRIGGER)

    execute {
        val method = BillingResultGetResponseCodeFingerprint.method
        method.replaceInstruction(0, "const/4 v0, 0x0")
        method.replaceInstruction(1, "return v0")
    }
}
