/*
 * Free Ads Rewards patch for Supreme Duelist Stickman v4.0.5.
 *
 * WHAT THIS DOES:
 * Some weapons/skins/maps are locked behind "Watch Ad to Unlock" gates.
 * When the player taps "Watch Ad", the game calls ShowRewardedWeaponAd,
 * which loads the ad, waits for it to finish, then calls UnlockWeapon
 * to grant the unlock.
 *
 * This patch makes ShowRewardedWeaponAd skip the ad display and jump
 * straight to UnlockWeapon, so the player gets the unlock instantly
 * without watching any ad.
 *
 * HOW IT WORKS:
 * ShowRewardedWeaponAd has a state machine:
 *   ldr w9, [x8, #0xec]   ; load ad state (0=not ready, 7=ready, 9=finished)
 *   cmp w9, #7             ; if state == 7 (ready to show)
 *   b.eq #show_ad_branch   ; -> show the ad (skipped by patch)
 *   cmp w9, #9             ; if state == 9 (ad finished)
 *   b.ne #skip_reward      ; -> otherwise skip reward
 *   ... unlock weapon code ...
 *
 * NOP the conditional branches so the code falls through
 * to the unlock code regardless of ad state.
 *
 * The pattern is:
 *   cmp w9, #7
 *   b.eq #show_ad          ; <- NOP this (don't show ad)
 *   cmp w9, #9
 *   b.ne #skip_reward      ; <- NOP this (always proceed to unlock)
 *   ... unlock code ...
 *
 * Strategy: Pattern-based NOP. Search for:
 *   "ldr w9, [x8, #0xec]; cmp w9, #7; b.eq; cmp w9, #9; b.ne"
 *   bytes: 09 ED 40 B9 3F 1D 00 71 ?? ?? ?? 54 3F 25 00 71 ?? ?? ?? 54
 *
 * When found, NOP both b.eq and b.ne. This makes the code skip the ad
 * display and always proceed to the unlock branch.
 */

package com.Neurononfire.SupremeDuelist.patches.iap

import app.morphe.patcher.patch.rawResourcePatch
import com.Neurononfire.SupremeDuelist.patches.shared.SUPREME_DUELIST
import java.util.logging.Logger

private val NOP = byteArrayOf(0x1f, 0x20, 0x03, 0xd5)

// Pattern: ldr w9, [x8, #0xec]; cmp w9, #7; b.eq; cmp w9, #9; b.ne
private val LDR_W9_0xEC = byteArrayOf(0x09, 0xed.toByte(), 0x40, 0xb9.toByte())
private val CMP_W9_7 = byteArrayOf(0x3f, 0x1d, 0x00, 0x71)
private val CMP_W9_9 = byteArrayOf(0x3f, 0x25, 0x00, 0x71)

// b.eq: 0x54xxxxx0 (condition 0 = EQ, lowest 4 bits = 0)
// b.ne: 0x54xxxxx1 (condition 1 = NE, lowest 4 bits = 1)
private fun isBCond(word: Int, cond: Int): Boolean =
    (word and 0xFF000000) == 0x54000000 && (word and 0xF) == cond

@Suppress("unused")
val freeAdsRewardsPatch = rawResourcePatch(
    name = "Free ads rewards (skip ad, grant unlock)",
    description = "Patches ShowRewardedWeaponAd (and similar ad-reward " +
        "methods) to skip the ad display and immediately grant the " +
        "reward (weapon/skin/map unlock). Searches for the pattern " +
        "'ldr w9, [x8, #0xec]; cmp w9, #7; b.eq; cmp w9, #9; b.ne' and " +
        "NOPs both conditional branches. This makes the code always " +
        "fall through to the unlock branch regardless of ad state. " +
        "Default OFF — enable only if you want all ad-rewarded " +
        "content unlocked for free.",
    default = false,
) {
    compatibleWith(SUPREME_DUELIST)

    execute {
        val logger = Logger.getLogger("FreeAdsRewards")
        val libPath = "lib/arm64-v8a/libil2cpp.so"
        val libFile = get(libPath)
        val libBytes = libFile.readBytes()

        logger.info("FreeAdsRewards: loaded libil2cpp.so (${libBytes.size} bytes)")

        var patchedCount = 0
        var scannedCount = 0

        // Scan for the pattern
        var i = 0
        val lastStart = libBytes.size - 20  // 5 instructions = 20 bytes
        while (i <= lastStart) {
            // Check LDR
            if (libBytes[i] != LDR_W9_0xEC[0] ||
                libBytes[i+1] != LDR_W9_0xEC[1] ||
                libBytes[i+2] != LDR_W9_0xEC[2] ||
                libBytes[i+3] != LDR_W9_0xEC[3]) {
                i += 4
                continue
            }

            // Check CMP w9, #7
            if (libBytes[i+4] != CMP_W9_7[0] ||
                libBytes[i+5] != CMP_W9_7[1] ||
                libBytes[i+6] != CMP_W9_7[2] ||
                libBytes[i+7] != CMP_W9_7[3]) {
                i += 4
                continue
            }

            // Check b.eq (any offset, condition = 0)
            val word_beq = (libBytes[i+8].toInt() and 0xFF) or
                ((libBytes[i+9].toInt() and 0xFF) shl 8) or
                ((libBytes[i+10].toInt() and 0xFF) shl 16) or
                ((libBytes[i+11].toInt() and 0xFF) shl 24)
            if (!isBCond(word_beq, 0)) {
                i += 4
                continue
            }

            // Check CMP w9, #9
            if (libBytes[i+12] != CMP_W9_9[0] ||
                libBytes[i+13] != CMP_W9_9[1] ||
                libBytes[i+14] != CMP_W9_9[2] ||
                libBytes[i+15] != CMP_W9_9[3]) {
                i += 4
                continue
            }

            // Check b.ne (any offset, condition = 1)
            val word_bne = (libBytes[i+16].toInt() and 0xFF) or
                ((libBytes[i+17].toInt() and 0xFF) shl 8) or
                ((libBytes[i+18].toInt() and 0xFF) shl 16) or
                ((libBytes[i+19].toInt() and 0xFF) shl 24)
            if (!isBCond(word_bne, 1)) {
                i += 4
                continue
            }

            // Found a match!
            scannedCount++
            val beqOffset = i + 8
            val bneOffset = i + 16

            // NOP both branches
            libBytes[beqOffset] = NOP[0]
            libBytes[beqOffset+1] = NOP[1]
            libBytes[beqOffset+2] = NOP[2]
            libBytes[beqOffset+3] = NOP[3]
            libBytes[bneOffset] = NOP[0]
            libBytes[bneOffset+1] = NOP[1]
            libBytes[bneOffset+2] = NOP[2]
            libBytes[bneOffset+3] = NOP[3]
            patchedCount++
            if (patchedCount <= 5) {
                logger.info("  patched ad-reward at 0x${i.toString(16)} (b.eq at 0x${beqOffset.toString(16)}, b.ne at 0x${bneOffset.toString(16)})")
            }

            i += 20  // Skip past this match
        }

        logger.info("FreeAdsRewards: found and patched $patchedCount ad-reward patterns")
        logger.info("FreeAdsRewards: scanned $scannedCount candidate locations")

        if (patchedCount > 0) {
            libFile.writeBytes(libBytes)
            logger.info("FreeAdsRewards: SUCCESS! Ad-rewarded unlocks will now skip the ad and grant the reward immediately.")
        } else {
            logger.severe("FreeAdsRewards: NO patterns found! Ad-reward bypass not applied.")
        }
    }
}
