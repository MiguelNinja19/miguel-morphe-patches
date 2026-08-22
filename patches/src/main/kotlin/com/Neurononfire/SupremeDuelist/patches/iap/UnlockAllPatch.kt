/*
 * Unlock all patch for Supreme Duelist Stickman v4.0.5.
 *
 * STRATEGY: Offset-based hex patches (coin checks + ads) +
 *           Pattern-based NOP (level checks).
 *
 * IMPORTANT: The user's APK has a 67,619,064 byte libil2cpp.so
 * (NOT the 61MB one from APKMirror). The offsets below were verified
 * against the 67MB lib in the original analysis.
 *
 * Level checks are pattern-based because I couldn't verify exact
 * offsets in the 67MB lib (it was wiped between sessions). The
 * patterns search for unique cmp values:
 *   - cmp w9, #0x33 (51) + b.lt  → level >= 51 check
 *   - cmp w9, #0x1f3 (499) + b.lt → counter >= 499 check
 *
 * Combined with UnlimitedCoinsPatch, the player has:
 * - Unlimited coins (never decrease)
 * - All weapons unlocked for free (no level requirement!)
 * - All skins unlocked for free
 * - All colors unlocked for free
 * - All modes unlocked for free
 * - No ads
 *
 * If verification fails (e.g., game updated), patch is skipped
 * with a warning — doesn't break other patches.
 */

package com.Neurononfire.SupremeDuelist.patches.iap

import app.morphe.patcher.patch.rawResourcePatch
import com.Neurononfire.SupremeDuelist.patches.shared.SUPREME_DUELIST
import java.util.logging.Logger

// Helper to create byte arrays from hex ints (avoids .toByte() on each)
private fun hb(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

// ARM64 instruction encodings (little-endian byte order)
private val NOP = hb(0x1f, 0x20, 0x03, 0xd5)
private val MOV_W0_1 = hb(0x20, 0x00, 0x80, 0x52)
private val RET = hb(0xc0, 0x03, 0x5f, 0xd6)

// Patch definition for offset-based patches
private data class HexPatch(
    val offset: Int,
    val expected: ByteArray,
    val replacement: ByteArray,
    val desc: String,
)

// Offset-based patches — verified against ORIGINAL 67MB lib (v4.0.5):
// These are the ORIGINAL offsets from the first analysis, NOT the 61MB
// APKMirror offsets that were wrong.
private val PATCHES: List<HexPatch> = listOf(
    // 1. AchatWeapon: NOP b.ls at 0x019efba0 (after cmp w8, #0x32 = price 50)
    HexPatch(0x019efba0,
        hb(0x29, 0x03, 0x00, 0x54),
        NOP,
        "AchatWeapon: NOP b.ls (coin check, price=50)"),

    // 2. AchatSkin: NOP b.ls at 0x019effa8 (after cmp w9, w20)
    HexPatch(0x019effa8,
        hb(0xe9, 0x0b, 0x00, 0x54),
        NOP,
        "AchatSkin: NOP b.ls (coin check)"),

    // 3. AchatColor #1: NOP b.hs at 0x019f0c94 (first coin check)
    HexPatch(0x019f0c94,
        hb(0xc2, 0x1b, 0x00, 0x54),
        NOP,
        "AchatColor #1: NOP b.hs (coin check, first)"),

    // 4. AchatColor #2: NOP b.hs at 0x019f0dd0 (second coin check)
    HexPatch(0x019f0dd0,
        hb(0xe2, 0x11, 0x00, 0x54),
        NOP,
        "AchatColor #2: NOP b.hs (coin check, second)"),

    // 5. AchatColor #3: NOP b.hs at 0x019f0e08 (third coin check)
    HexPatch(0x019f0e08,
        hb(0x22, 0x10, 0x00, 0x54),
        NOP,
        "AchatColor #3: NOP b.hs (coin check, third)"),

    // NOTE: buyMiniGames REMOVED — NOPing its b.ls breaks mode
    // initialization (all modes show as "boss fights"). With
    // UnlimitedCoinsPatch active, the player has infinite coins to
    // buy modes normally.

    // 6. RemoveAds.get_AdsRemoved: mov w0,#1; ret at 0x01a0b24c
    //    Original: e0 03 13 aa (mov x0, x19) + f4 4f 42 a9 (ldp x20, x19, [sp, #0x20])
    //    Patched:  20 00 80 52 (mov w0, #1) + c0 03 5f d6 (ret)
    HexPatch(0x01a0b24c,
        hb(0xe0, 0x03, 0x13, 0xaa, 0xf4, 0x4f, 0x42, 0xa9),
        MOV_W0_1 + RET,
        "RemoveAds.get_AdsRemoved: mov w0,#1; ret (no ads)"),
)

// Level check patterns (pattern-based, works on any lib size):
// cmp w9, #0x33 (51) = 3f cd 00 71
// cmp w9, #0x1f3 (499) = 3f cd 07 71
// b.lt = 0x54xxxxxb (condition 0xB = LT)
private val CMP_W9_51 = hb(0x3f, 0xcd, 0x00, 0x71)
private val CMP_W9_499 = hb(0x3f, 0xcd, 0x07, 0x71)

private fun isBlt(word: Int): Boolean =
    (word and 0xFF000000.toInt()) == 0x54000000 && (word and 0xF) == 0xB

private fun ByteArray.toHex(): String =
    joinToString(" ") { "%02x".format(it) }

@Suppress("unused")
val unlockAllPatch = rawResourcePatch(
    name = "Unlock all (weapons, skins, colors, modes, no ads + level bypass)",
    description = "Hex patches libil2cpp.so to: " +
        "(1) NOP level checks in AchatWeapon (pattern-based: " +
        "searches for cmp w9, #51 + b.lt and cmp w9, #499 + b.lt). " +
        "(2) NOP coin checks in AchatWeapon, AchatSkin, AchatColor (3x) " +
        "(offset-based, verified on 67MB lib). " +
        "(3) Patch RemoveAds.get_AdsRemoved to always return true. " +
        "buyMiniGames is NOT patched (breaks mode init — use " +
        "UnlimitedCoinsPatch to buy modes with infinite coins). " +
        "Combined: unlimited coins + all weapons (no level req!) + " +
        "all skins/colors + no ads.",
    default = true,
) {
    compatibleWith(SUPREME_DUELIST)

    execute {
        val logger = Logger.getLogger("UnlockAll")
        val libPath = "lib/arm64-v8a/libil2cpp.so"
        val libFile = get(libPath)
        val libBytes = libFile.readBytes()

        logger.info("UnlockAll: loaded libil2cpp.so (${libBytes.size} bytes)")

        var appliedCount = 0
        var skippedCount = 0

        // === PART 1: Offset-based patches (coin checks + ads) ===
        for (p in PATCHES) {
            if (p.offset + p.expected.size > libBytes.size) {
                logger.warning("  SKIP (out of bounds): ${p.desc}")
                skippedCount++
                continue
            }

            val actual = libBytes.sliceArray(p.offset until p.offset + p.expected.size)
            if (!actual.contentEquals(p.expected)) {
                logger.warning("  SKIP (byte mismatch): ${p.desc}")
                logger.warning("         expected: ${p.expected.toHex()}")
                logger.warning("         actual:   ${actual.toHex()}")
                logger.warning("         at offset: 0x${p.offset.toString(16)}")
                skippedCount++
                continue
            }

            for (i in p.replacement.indices) {
                libBytes[p.offset + i] = p.replacement[i]
            }
            appliedCount++
            logger.info("  APPLIED: ${p.desc}")
            logger.info("         at offset: 0x${p.offset.toString(16)}")
        }

        // === PART 2: Pattern-based level checks ===
        // Search for "cmp w9, #51; b.lt" (level >= 51 check)
        var levelCheckCount = 0
        var i = 0
        val lastStart = libBytes.size - 8
        while (i <= lastStart) {
            // Check for cmp w9, #0x33 (51)
            if (libBytes[i] == CMP_W9_51[0] &&
                libBytes[i + 1] == CMP_W9_51[1] &&
                libBytes[i + 2] == CMP_W9_51[2] &&
                libBytes[i + 3] == CMP_W9_51[3]) {

                // Check next instruction is b.lt
                if (i + 8 <= libBytes.size) {
                    val word = (libBytes[i + 4].toInt() and 0xFF) or
                        ((libBytes[i + 5].toInt() and 0xFF) shl 8) or
                        ((libBytes[i + 6].toInt() and 0xFF) shl 16) or
                        ((libBytes[i + 7].toInt() and 0xFF) shl 24)

                    if (isBlt(word)) {
                        // NOP the b.lt
                        val bltOffset = i + 4
                        libBytes[bltOffset] = NOP[0]
                        libBytes[bltOffset + 1] = NOP[1]
                        libBytes[bltOffset + 2] = NOP[2]
                        libBytes[bltOffset + 3] = NOP[3]
                        levelCheckCount++
                        appliedCount++
                        logger.info("  APPLIED: Level check 1 (level >= 51): NOP b.lt at 0x${bltOffset.toString(16)}")
                    }
                }
            }

            // Check for cmp w9, #0x1f3 (499)
            if (libBytes[i] == CMP_W9_499[0] &&
                libBytes[i + 1] == CMP_W9_499[1] &&
                libBytes[i + 2] == CMP_W9_499[2] &&
                libBytes[i + 3] == CMP_W9_499[3]) {

                // Check next instruction is b.lt
                if (i + 8 <= libBytes.size) {
                    val word = (libBytes[i + 4].toInt() and 0xFF) or
                        ((libBytes[i + 5].toInt() and 0xFF) shl 8) or
                        ((libBytes[i + 6].toInt() and 0xFF) shl 16) or
                        ((libBytes[i + 7].toInt() and 0xFF) shl 24)

                    if (isBlt(word)) {
                        // NOP the b.lt
                        val bltOffset = i + 4
                        libBytes[bltOffset] = NOP[0]
                        libBytes[bltOffset + 1] = NOP[1]
                        libBytes[bltOffset + 2] = NOP[2]
                        libBytes[bltOffset + 3] = NOP[3]
                        levelCheckCount++
                        appliedCount++
                        logger.info("  APPLIED: Level check 2 (counter >= 499): NOP b.lt at 0x${bltOffset.toString(16)}")
                    }
                }
            }

            i += 4
        }

        logger.info("UnlockAll: patched $levelCheckCount level checks (pattern-based)")
        logger.info("UnlockAll: applied $appliedCount total patches, skipped $skippedCount")

        if (appliedCount > 0) {
            libFile.writeBytes(libBytes)
            logger.info("UnlockAll: SUCCESS!")
        } else {
            logger.severe("UnlockAll: NO patches were applied!")
        }
    }
}
