package com.ea.game.pvz.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.ea.game.pvz.patches.shared.Constants.PVZ
import com.ea.game.pvz.patches.shared.LoadAdFingerprint

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Prevents video ads from loading. When the game tries to " +
        "load an ad, nothing happens. Ads will not be shown.",
    default = true,
) {
    compatibleWith(PVZ)

    execute {
        LoadAdFingerprint.method.addInstructions(0, "return-void")
    }
}
