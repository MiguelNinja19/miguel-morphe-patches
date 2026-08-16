/*
 * Unlock all + No ads patch for Supreme Duelist Stickman.
 *
 * FIXES the crash (black screen) from the previous version:
 * - Removed AchatWeapon/AchatSkin/AchatColor/buyMap/buyMiniGames patches
 *   (these methods also INITIALIZE the mode/map/weapon — skipping them
 *   caused black screen when entering any mode)
 * - Removed unlockBattleMode/unlockMiniGame patches (same issue)
 * - Removed OnRemoveAds patch (was causing issues)
 *
 * Current approach (SAFE, no crash):
 * 1. get_AdsRemoved -> return true (No Ads purchased)
 * 2. ShowRewardedWeaponAd -> return void (skip ad, reward still granted)
 * 3. WatchReward -> return void (skip ad, reward still granted)
 * 4. WatchRewardedAd -> return void (skip ad, reward still granted)
 * 5. SkinAd -> return void (skip ad, reward still granted)
 *
 * The player earns coins instantly (ads are skipped but rewards still
 * granted by the game's callback system). Combined with the "Unlimited
 * coins" patch (SaveCoins -> return void), coins never decrease.
 * The player can then buy weapons/skins/maps/modes normally — the
 * purchase goes through fully (no crash) but coins aren't deducted.
 */

package com.Neurononfire.SupremeDuelist.patches.iap

import app.morphe.patcher.patch.rawResourcePatch
import com.Neurononfire.SupremeDuelist.patches.shared.SUPREME_DUELIST
import java.util.logging.Logger

@Suppress("unused")
val unlockAllPatch = rawResourcePatch(
    name = "Unlock all (no ads + skip rewarded ads)",
    description = "Hex patches libil2cpp.so to: (1) make get_AdsRemoved " +
        "return true (No Ads purchased), (2) skip all rewarded ads " +
        "(ShowRewardedWeaponAd, WatchReward, WatchRewardedAd, SkinAd) " +
        "while still granting the rewards. Combined with the 'Unlimited " +
        "coins' patch, the player can buy everything without losing coins.",
    default = true,
) {
    compatibleWith(SUPREME_DUELIST)

    execute {
        val logger = Logger.getLogger("UnlockAll")
        val libPath = "lib/arm64-v8a/libil2cpp.so"
        val libFile = get(libPath)
        val libBytes = libFile.readBytes()

        fun hb(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }
        val returnTrue = hb(0x20, 0x00, 0x80, 0x52, 0xc0, 0x03, 0x5f, 0xd6)
        val returnVoid = hb(0xc0, 0x03, 0x5f, 0xd6, 0x1f, 0x20, 0x03, 0xd5)

        val patches = listOf(
            // 1. get_AdsRemoved -> return true (No Ads purchased)
            Triple(hb(0xe0, 0x03, 0x13, 0xaa, 0xf4, 0x4f, 0x42, 0xa9, 0xf6, 0x57, 0x41, 0xa9, 0xfe, 0x07, 0x43, 0xf8, 0x82, 0xf6, 0xff, 0x17), returnTrue, "get_AdsRemoved -> return true"),
            // 2. ShowRewardedWeaponAd -> return void (skip ad)
            Triple(hb(0xf5, 0x03, 0x13, 0xaa, 0x08, 0x5d, 0x40, 0xf9, 0x01, 0x01, 0x40, 0xf9, 0xa1, 0x0e, 0x03, 0xf8, 0xe0, 0x03, 0x15, 0xaa, 0x16, 0x83, 0xf9, 0x97), returnVoid, "ShowRewardedWeaponAd -> skip"),
            // 3. WatchReward -> return void (skip ad)
            Triple(hb(0x68, 0x1a, 0x40, 0xb9, 0x13, 0x01, 0x00, 0x0b, 0xe0, 0x03, 0x1f, 0xaa, 0x0a, 0x93, 0x15, 0x94), returnVoid, "WatchReward -> skip"),
            // 4. WatchRewardedAd -> return void (skip ad)
            Triple(hb(0xe0, 0x03, 0x16, 0xaa, 0xab, 0xee, 0xf0, 0x97), returnVoid, "WatchRewardedAd -> skip"),
            // 5. SkinAd -> return void (skip ad)
            Triple(hb(0x48, 0x00, 0x00, 0x35, 0xc9, 0x76, 0xfb, 0x97), returnVoid, "SkinAd -> skip"),
        )

        logger.info("Unlock all: patching libil2cpp.so with " + patches.size + " hex patches")
        var patchedCount = 0
        for ((pattern, replacement, description) in patches) {
            val idx = findPattern(libBytes, pattern)
            if (idx >= 0) {
                for (i in replacement.indices) { libBytes[idx + i] = replacement[i] }
                patchedCount++
                logger.info("  patched: " + description + " (0x" + idx.toString(16) + ")")
            } else {
                logger.info("  NOT FOUND: " + description)
            }
        }
        if (patchedCount > 0) { libFile.writeBytes(libBytes) }
    }
}

private fun findPattern(haystack: ByteArray, needle: ByteArray): Int {
    if (needle.isEmpty() || haystack.size < needle.size) return -1
    val lastStart = haystack.size - needle.size
    for (i in 0..lastStart) {
        var found = true
        for (j in needle.indices) { if (haystack[i + j] != needle[j]) { found = false; break } }
        if (found) return i
    }
    return -1
}
