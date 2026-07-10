package com.ea.game.pvz.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.ea.game.pvz.patches.shared.Constants.PVZ
import com.ea.game.pvz.patches.shared.PurchaseItemFingerprint

@Suppress("unused")
val freePurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "When you try to buy something, the purchase is immediately " +
        "marked as successful without contacting Google Play. The EA Nimble " +
        "MTX system receives a null error (success) and grants the item.",
    default = true,
) {
    compatibleWith(PVZ)

    execute {
        val method = PurchaseItemFingerprint.method
        method.addInstructions(0, """
            const/4 v0, 0x0
            return-object v0
        """.trimIndent())
    }
}
