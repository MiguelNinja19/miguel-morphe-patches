package com.ea.game.pvz.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.ea.game.pvz.patches.shared.Constants.PVZ
import com.ea.game.pvz.patches.shared.ShowAdFingerprint

@Suppress("unused")
val freeRewardAdsPatch = bytecodePatch(
    name = "Free reward ads",
    description = "When the game tries to show a rewarded ad, it immediately " +
        "fires the ad-clicked and ad-closed callbacks without actually " +
        "showing the ad. You get the reward instantly.",
    default = true,
) {
    compatibleWith(PVZ)

    execute {
        val method = ShowAdFingerprint.method
        method.addInstructions(0, """
            iget-object v0, p0, Lcom/popcap/pcsp/marketing/GoogleImaDriver;->mDriver:Ljava/lang/Long;
            invoke-virtual {v0}, Ljava/lang/Long;->longValue()J
            move-result-wide v0
            invoke-direct {p0, v0, v1}, Lcom/popcap/pcsp/marketing/GoogleImaDriver;->notifyAdClicked(J)V
            invoke-direct {p0, v0, v1}, Lcom/popcap/pcsp/marketing/GoogleImaDriver;->notifyAdClosed(J)V
            const/4 v0, 0x1
            return v0
        """.trimIndent())
    }
}
