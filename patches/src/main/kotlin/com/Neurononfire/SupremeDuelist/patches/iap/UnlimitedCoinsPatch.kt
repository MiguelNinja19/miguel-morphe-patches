/*
 * Unlimited coins patch for Supreme Duelist Stickman.
 *
 * Uses HEX PATCHING on libil2cpp.so (ARM64) to skip SavePlayerData,
 * SavePlayerProfile, and SaveCoins. These methods save the player's coin
 * balance, XP, tokens, and skin data. By skipping them, coin deductions
 * are never persisted — the player's balance stays unlimited.
 *
 * IMPORTANT: We do NOT skip SaveWeapon, SaveSkin, SaveMap, etc.
 * Those save unlock states and must keep working so unlocks persist.
 *
 * Method offsets (from il2cppdumper):
 *   S_DataManager$$SavePlayerData @ 0x1a44c4c
 *   S_DataManager$$SavePlayerProfile @ 0x1a4553c
 *   DailyClaimUI$$SaveCoins @ 0x185f830
 */

package com.Neurononfire.SupremeDuelist.patches.iap

import app.morphe.patcher.patch.rawResourcePatch
import com.Neurononfire.SupremeDuelist.patches.shared.SUPREME_DUELIST
import java.util.logging.Logger

@Suppress("unused")
val unlimitedCoinsPatch = rawResourcePatch(
    name = "Unlimited coins",
    description = "Hex patches libil2cpp.so to skip SavePlayerData, " +
        "SavePlayerProfile, and SaveCoins. This prevents the game from " +
        "persisting coin deductions — when you spend coins on weapons, " +
        "skins, maps, or modes, the reduced amount is never saved. " +
        "Your coin balance stays unlimited across sessions. Does NOT " +
        "affect weapon/skin/map unlock saves (those keep working).",
    default = true,
) {
    compatibleWith(SUPREME_DUELIST)

    execute {
        val logger = Logger.getLogger("UnlimitedCoins")
        val libPath = "lib/arm64-v8a/libil2cpp.so"
        val libFile = get(libPath)
        val libBytes = libFile.readBytes()

        fun hb(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }
        val returnVoid = hb(0xc0, 0x03, 0x5f, 0xd6, 0x1f, 0x20, 0x03, 0xd5)

        val patches = listOf(
            // 1. SavePlayerData -> return void (don't save coin deductions)
            Triple(
                hb(0xe0, 0x03, 0x13, 0xaa, 0xf4, 0x4f, 0x43, 0xa9, 0xf6, 0x57, 0x42, 0xa9, 0x08, 0x61, 0x40, 0xf9, 0xf8, 0x5f, 0x41, 0xa9, 0x02, 0x39, 0x40, 0xf9, 0xfe, 0x67, 0xc4, 0xa8, 0xda, 0x22, 0x3c, 0x14),
                returnVoid,
                "SavePlayerData -> return void (don't save coin changes)"
            ),
            // 2. SavePlayerProfile -> return void (don't save profile changes)
            Triple(
                hb(0x00, 0x0c, 0x47, 0xf9, 0xe8, 0x83, 0xf3, 0x97),
                returnVoid,
                "SavePlayerProfile -> return void (don't save profile changes)"
            ),
            // 3. SaveCoins -> return void (don't save coin amount)
            Triple(
                hb(0x08, 0x21, 0x42, 0xf9, 0x08, 0x01, 0x40, 0xf9, 0x08, 0x5d, 0x40, 0xf9, 0x08, 0x01, 0x40, 0xf9, 0xe8, 0x00, 0x00, 0xb4, 0x01, 0x7d, 0x41, 0xf9, 0x61, 0x0e, 0x09, 0xf8, 0xe0, 0x03, 0x13, 0xaa, 0xf4, 0x4f, 0x41, 0xa9, 0xfe, 0x07, 0x42, 0xf8, 0x0d, 0x1b, 0xfb, 0x17, 0xb5, 0x1b, 0xfb, 0x97),
                returnVoid,
                "SaveCoins -> return void (don't save coin amount)"
            ),
        )

        logger.info("Unlimited coins: patching libil2cpp.so with " + patches.size + " hex patches")
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
