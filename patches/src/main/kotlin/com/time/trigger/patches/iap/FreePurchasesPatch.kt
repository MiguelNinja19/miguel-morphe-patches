package com.time.trigger.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.time.trigger.patches.shared.BillingResultGetResponseCodeFingerprint
import com.time.trigger.patches.shared.Constants.JOHNNY_TRIGGER

@Suppress("unused")
val freePurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Forces the purchase result handler to always report success, " +
        "regardless of the actual billing result. When you tap buy, the Play " +
        "Store may open and give an error, but the game treats it as a " +
        "successful purchase and grants the item.",
    default = true,
) {
    compatibleWith(JOHNNY_TRIGGER)

    execute {
        val getResponseCodeMethod = BillingResultGetResponseCodeFingerprint.method
        getResponseCodeMethod.replaceInstruction(0, "const/4 v0, 0x1")
        getResponseCodeMethod.replaceInstruction(1, "return v0")
    }
}
