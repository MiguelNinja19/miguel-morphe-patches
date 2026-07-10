/*
 * Auto Reward Ads patch for Hunter Assassin.
 *
 * The game uses Rovio Beacon AdsSdk for ads. When a rewarded ad is
 * closed, onAdHidden(boolean rewarded) is called. The 'rewarded'
 * parameter is true if the user watched the entire ad.
 *
 * This patch forces 'rewarded' to always be true, so the user gets
 * the reward even if they skip the ad.
 */

package com.rubygames.assassin.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.rubygames.assassin.patches.shared.Constants.HUNTER_ASSASSIN
import com.rubygames.assassin.patches.shared.OnAdHiddenFingerprint

@Suppress("unused")
val autoRewardAdsPatch = bytecodePatch(
    name = "Auto reward ads",
    description = "When a rewarded ad is closed, the game always thinks " +
        "you watched the entire ad. You get the reward even if you skip " +
        "or close the ad immediately.",
    default = true,
) {
    compatibleWith(HUNTER_ASSASSIN)

    execute {
        val method = OnAdHiddenFingerprint.method
        method.addInstructions(0, "const/4 p1, 0x1")
    }
}
