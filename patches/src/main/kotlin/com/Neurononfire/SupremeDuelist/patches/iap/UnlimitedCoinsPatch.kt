/*
 * Unlimited coins patch for Supreme Duelist Stickman.
 *
 * FIXES the crash from the previous version:
 * - Removed SavePlayerData patch (was causing black screen — game
 *   couldn't load player data that was saved as empty)
 * - Removed SavePlayerProfile patch (same issue)
 *
 * Current approach (SAFE):
 * Only patches SaveCoins -> return void. This prevents the game from
 * persisting coin deductions. When you spend coins on weapons, skins,
 * maps, or modes, the purchase goes through fully (no crash) but the
 * reduced coin amount is never saved. Your balance stays the same
 * across sessions.
 *
 * Combined with the "Unlock all" patch (which skips rewarded ads but
 * still grants rewards), the player earns coins instantly and never
 * loses them.
 *
 * Method offset (from il2cppdumper):
 *   DailyClaimUI$$SaveCoins @ 0x185f830
 */

package com.Neurononfire.SupremeDuelist.patches.iap

import app.morphe.patcher.patch.rawResourcePatch
import com.Neurononfire.SupremeDuelist.patches.shared.SUPREME_DUELIST
import java.util.logging.Logger

@Suppress("unused")
val unlimitedCoinsPatch = rawResourcePatch(
    name = "Unlimited coins",
    description = "Hex patches libil2cpp.so to skip SaveCoins only. " +
        "This prevents the game from persisting coin deductions — when " +
        "you spend coins, the reduced amount is never saved. Your " +
        "balance stays unlimited across sessions. Does NOT affect " +
        "SavePlayerData or SavePlayerProfile (those caused crashes).",
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
            // SaveCoins -> return void (don't save coin amount)
            // @ 0x185f830 - 48 bytes for unique pattern
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
