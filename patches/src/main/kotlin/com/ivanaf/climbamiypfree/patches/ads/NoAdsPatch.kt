/*
 * No Ads patch for Climb!
 *
 * Patches all AdMob show/create methods in GoogleMobileAdsGM to return 0,
 * preventing ads from displaying. Covers interstitial, banner, rewarded
 * video, rewarded interstitial, and app open ads.
 *
 * GameMaker Studio game uses GoogleMobileAdsGM class for all ad operations.
 * Each ad show method returns D (double) - we patch them to return 0.0.
 */

package com.ivanaf.climbamiypfree.patches.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.ivanaf.climbamiypfree.patches.shared.CLIMB
import com.ivanaf.climbamiypfree.patches.shared.InterstitialShowFingerprint
import com.ivanaf.climbamiypfree.patches.shared.BannerShowFingerprint
import com.ivanaf.climbamiypfree.patches.shared.BannerCreateFingerprint
import com.ivanaf.climbamiypfree.patches.shared.RewardedVideoShowFingerprint
import com.ivanaf.climbamiypfree.patches.shared.RewardedInterstitialShowFingerprint
import com.ivanaf.climbamiypfree.patches.shared.AppOpenAdEnableFingerprint
import java.util.logging.Logger

@Suppress("unused")
val noAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Removes all ads (interstitial, banner, rewarded video, " +
        "rewarded interstitial, app open) by patching GoogleMobileAdsGM " +
        "show/create methods to return 0.",
    default = true,
) {
    compatibleWith(CLIMB)

    execute {
        val logger = Logger.getLogger("NoAds")
        var count = 0

        // Smali to return 0.0 (double)
        val returnZero = "const-wide/16 v0, 0x0\nreturn-wide v0"

        // Patch interstitial show
        InterstitialShowFingerprint.matchOrNull?.let {
            it.method.addInstructions(0, returnZero)
            count++
            logger.info("  patched: AdMob_Interstitial_Show -> return 0")
        }

        // Patch banner show
        BannerShowFingerprint.matchOrNull?.let {
            it.method.addInstructions(0, returnZero)
            count++
            logger.info("  patched: AdMob_Banner_Show -> return 0")
        }

        // Patch banner create
        BannerCreateFingerprint.matchOrNull?.let {
            it.method.addInstructions(0, returnZero)
            count++
            logger.info("  patched: AdMob_Banner_Create -> return 0")
        }

        // Patch rewarded video show
        RewardedVideoShowFingerprint.matchOrNull?.let {
            it.method.addInstructions(0, returnZero)
            count++
            logger.info("  patched: AdMob_RewardedVideo_Show -> return 0")
        }

        // Patch rewarded interstitial show
        RewardedInterstitialShowFingerprint.matchOrNull?.let {
            it.method.addInstructions(0, returnZero)
            count++
            logger.info("  patched: AdMob_RewardedInterstitial_Show -> return 0")
        }

        // Patch app open ad enable
        AppOpenAdEnableFingerprint.matchOrNull?.let {
            it.method.addInstructions(0, returnZero)
            count++
            logger.info("  patched: AdMob_AppOpenAd_Enable -> return 0")
        }

        logger.info("No ads COMPLETE: " + count + " methods patched")
    }
}
