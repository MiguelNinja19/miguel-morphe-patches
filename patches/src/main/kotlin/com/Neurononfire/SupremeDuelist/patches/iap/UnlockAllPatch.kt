/*
 * Unlock all + No ads patch for Supreme Duelist Stickman.
 *
 * Uses HEX PATCHING on libil2cpp.so (ARM64) with 14 patches:
 *
 * 1. get_AdsRemoved -> return true (No Ads purchased)
 * 2. AchatWeapon -> return void (free weapon purchase with coins)
 * 3. AchatSkin -> return void (free skin purchase with coins)
 * 4. AchatColor -> return void (free color purchase with coins)
 * 5. buyMap -> return void (free map purchase with coins)
 * 6. buyMiniGames -> return void (free mini-game purchase with coins)
 * 7. buyMiniGamesWithAds -> return void (free mini-game purchase with ads)
 * 8. ShowRewardedWeaponAd -> return void (skip rewarded ad for weapon)
 * 9. WatchReward -> return void (skip rewarded ad for daily reward)
 * 10. WatchRewardedAd -> return void (skip rewarded ad)
 * 11. SkinAd -> return void (skip rewarded ad for skin)
 * 12. unlockBattleMode -> return void (free, skip coin check)
 * 13. unlockMiniGame -> return void (free, skip coin check)
 * 14. OnRemoveAds -> return void (prevent crash when ads already removed)
 *
 * NOT patched (must keep working):
 * - SaveWeapon, SaveSkin, SaveMap, SaveRandWeapon, SaveSurvivalMap
 *   (these save unlock states so they persist)
 * - SaveGameSettings (saves game settings)
 *
 * Method offsets found via il2cppdumper.
 */

package com.Neurononfire.SupremeDuelist.patches.iap

import app.morphe.patcher.patch.rawResourcePatch
import com.Neurononfire.SupremeDuelist.patches.shared.SUPREME_DUELIST
import java.util.logging.Logger

@Suppress("unused")
val unlockAllPatch = rawResourcePatch(
    name = "Unlock all (weapons, skins, colors, modes, maps, no ads)",
    description = "Hex patches libil2cpp.so to: (1) make get_AdsRemoved " +
        "return true (No Ads purchased), (2) make all purchases free " +
        "(AchatWeapon, AchatSkin, AchatColor, buyMap, buyMiniGames), " +
        "(3) skip all rewarded ads (ShowRewardedWeaponAd, WatchReward, " +
        "WatchRewardedAd, SkinAd), (4) make unlockBattleMode and " +
        "unlockMiniGame free. 14 hex patches total.",
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
            // 1. get_AdsRemoved -> return true
            Triple(hb(0xe0, 0x03, 0x13, 0xaa, 0xf4, 0x4f, 0x42, 0xa9, 0xf6, 0x57, 0x41, 0xa9, 0xfe, 0x07, 0x43, 0xf8, 0x82, 0xf6, 0xff, 0x17), returnTrue, "get_AdsRemoved -> return true"),
            // 2. AchatWeapon -> return void (free weapon purchase)
            Triple(hb(0xc8, 0x1a, 0x40, 0xb9, 0x1f, 0xc9, 0x00, 0x71), returnVoid, "AchatWeapon -> free"),
            // 3. AchatSkin -> return void (free skin purchase)
            Triple(hb(0x29, 0x0d, 0x00, 0x34, 0x0a, 0xb1, 0x00, 0x91, 0x09, 0x81, 0x00, 0x91, 0x0b, 0xa1, 0x00, 0x91, 0x08, 0x91, 0x00, 0x91, 0x50, 0x00, 0x00, 0x14, 0x68, 0x8a, 0x40, 0xf9, 0x28, 0x0c, 0x00, 0xb4), returnVoid, "AchatSkin -> free"),
            // 4. AchatColor -> return void (free color purchase)
            Triple(hb(0x1f, 0x22, 0x81, 0x94, 0x80, 0x1c, 0x00, 0xb4), returnVoid, "AchatColor -> free"),
            // 5. buyMap -> return void (free map purchase)
            Triple(hb(0x38, 0xa3, 0x12, 0x94, 0xf6, 0x03, 0x00, 0xaa), returnVoid, "buyMap -> free"),
            // 6. buyMiniGames -> return void (free mini-game purchase)
            Triple(hb(0x39, 0x04, 0x83, 0x94, 0x68, 0x02, 0x43, 0xf9), returnVoid, "buyMiniGames -> free"),
            // 7. buyMiniGamesWithAds -> return void (free mini-game with ads)
            Triple(hb(0xa8, 0x6e, 0x73, 0x39, 0x94, 0xee, 0x40, 0xf9), returnVoid, "buyMiniGamesWithAds -> free"),
            // 8. ShowRewardedWeaponAd -> return void (skip ad)
            Triple(hb(0xf5, 0x03, 0x13, 0xaa, 0x08, 0x5d, 0x40, 0xf9, 0x01, 0x01, 0x40, 0xf9, 0xa1, 0x0e, 0x03, 0xf8, 0xe0, 0x03, 0x15, 0xaa, 0x16, 0x83, 0xf9, 0x97), returnVoid, "ShowRewardedWeaponAd -> skip"),
            // 9. WatchReward -> return void (skip ad)
            Triple(hb(0x68, 0x1a, 0x40, 0xb9, 0x13, 0x01, 0x00, 0x0b, 0xe0, 0x03, 0x1f, 0xaa, 0x0a, 0x93, 0x15, 0x94), returnVoid, "WatchReward -> skip"),
            // 10. WatchRewardedAd -> return void (skip ad)
            Triple(hb(0xe0, 0x03, 0x16, 0xaa, 0xab, 0xee, 0xf0, 0x97), returnVoid, "WatchRewardedAd -> skip"),
            // 11. SkinAd -> return void (skip ad)
            Triple(hb(0x48, 0x00, 0x00, 0x35, 0xc9, 0x76, 0xfb, 0x97), returnVoid, "SkinAd -> skip"),
            // 12. unlockBattleMode -> return void (free)
            Triple(hb(0xc0, 0x21, 0x01, 0xd0, 0x00, 0xec, 0x40, 0xf9, 0x98, 0xd1, 0xf4, 0x97), returnVoid, "unlockBattleMode -> free"),
            // 13. unlockMiniGame -> return void (free)
            Triple(hb(0x08, 0x24, 0x00, 0xb4, 0x09, 0x19, 0x40, 0xb9, 0x3f, 0x01, 0x14, 0x6b), returnVoid, "unlockMiniGame -> free"),
            // 14. OnRemoveAds -> return void (prevent crash)
            Triple(hb(0xf4, 0x4f, 0x42, 0xa9, 0xf6, 0x57, 0x41, 0xa9, 0xfe, 0x07, 0x43, 0xf8, 0xc0, 0x03, 0x5f, 0xd6, 0xd7, 0x61, 0xf4, 0x97), returnVoid, "OnRemoveAds -> prevent crash"),
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
