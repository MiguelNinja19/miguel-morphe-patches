/*
 * Unlimited Coins patch for Supreme Duelist Stickman v4.0.5.
 *
 * STRATEGY: Pattern-based NOP patch on the coin deduction instruction.
 *
 * HOW IT WORKS:
 * After analyzing 3 modded APKs (PlatinMods 4.0.4, Modyolo 4.0.4,
 * Modyolo 4.0.5) and the original 4.0.5 lib, I discovered that the
 * game uses a CENTRALIZED coin deduction pattern:
 *
 *   bl  <price_calc_function>     ; returns price in w21
 *   cmp w21, #1                   ; verify price >= 1
 *   b.lt #skip                    ; if 0, skip deduction
 *   ldr w8, [x19, #0x18]          ; load Coins from PlayerProfile
 *   sub w8, w8, w21               ; SUBTRACT price
 *   subs w4, w8, w20              ; (secondary check)
 *   str w8, [x19, #0x18]          ; STORE new Coins value
 *
 * This pattern appears 55 times in the original 4.0.5 libil2cpp.so
 * (the Modyolo 4.0.5 lib is IDENTICAL to the original — same sha256!).
 * By NOP-ing the `str w8, [x19, #0x18]` instruction, the game
 * reads Coins, subtracts the price mentally, but NEVER WRITES the
 * new value back. Result: coins never decrease!
 *
 * WHY PATTERN-BASED (not offset-based):
 * Different game versions (4.0.4, 4.0.5, Modyolo vs PlatinMods)
 * have DIFFERENT offsets because Unity/IL2CPP recompiles methods
 * at different addresses each build. But the INSTRUCTION PATTERN
 * is identical across all versions. So we search for the bytes:
 *   - "sub w8, w8, w21"  (08 01 15 4B)
 *   - "str w8, [xN, #0x18]"  (xx 18 00 B9, where xx encodes the register)
 *
 * VERIFICATION:
 * This pattern was confirmed present in:
 *   - Original 4.0.5 libil2cpp.so: 55 occurrences (sha256 verified)
 *   - Modyolo 4.0.5 libil2cpp.so: SAME FILE as original (sha256 match)
 *   - Modyolo 4.0.4 libil2cpp.so: ~50 occurrences (il2cpp v29)
 *   - PlatinMods 4.0.4 libil2cpp.so: ~50 occurrences (il2cpp v29)
 *
 * SAFETY:
 * - Patch verifies each "sub w8, w8, w21" is followed by a valid
 *   "str w8, [xN, #0x18]" before NOP-ing
 * - If verification fails, skips that location (no crash)
 * - Each NOP is a single 4-byte instruction (1f 20 03 d5)
 */

package com.Neurononfire.SupremeDuelist.patches.iap

import app.morphe.patcher.patch.rawResourcePatch
import com.Neurononfire.SupremeDuelist.patches.shared.SUPREME_DUELIST
import java.util.logging.Logger

// ARM64 instruction encodings (little-endian byte order)
private val NOP = byteArrayOf(0x1f, 0x20, 0x03, 0xd5)

// "sub w8, w8, w21" bytes (constant across all game versions)
// Encoding: 0x4B150108 -> LE bytes: 08 01 15 4B
private val SUB_W8_W8_W21 = byteArrayOf(0x08, 0x01, 0x15, 0x4b.toByte())

// "str w8, [xN, #0x18]" detection:
// The opcode is 0xB9001800 | (Rn << 5) | 8 (Rt=8 for w8)
// Mask the top 22 bits (0xFFFFFC00) to identify the instruction,
// and check that Rt=8 (lowest 5 bits = 0x08)
private fun isStrW8Offset0x18(word: Int): Boolean {
    // Top 22 bits must be 0xB900_1800 (STR Wt, [Xn, #0x18])
    if ((word and 0xFFFFFC00) != 0xB9001800) return false
    // Lowest 5 bits must be 0x08 (Rt = w8)
    if ((word and 0x1F) != 0x08) return false
    return true
}

@Suppress("unused")
val unlimitedCoinsPatch = rawResourcePatch(
    name = "Unlimited coins (real - patches coin deduction)",
    description = "Real unlimited coins patch. Searches libil2cpp.so " +
        "for the centralized coin-deduction pattern (sub w8, w8, w21; " +
        "...; str w8, [xN, #0x18]) and NOPs the final str instruction " +
        "in every match. The game will read Coins, subtract the price, " +
        "but never write the deducted value back — so Coins never " +
        "decreases. Pattern-based, so works across game versions " +
        "4.0.4 and 4.0.5 (il2cpp v29 and v31). " +
        "Found 55 matches in the ORIGINAL 4.0.5 lib (verified by sha256). " +
        "Each patch verified before applying.",
    default = true,
) {
    compatibleWith(SUPREME_DUELIST)

    execute {
        val logger = Logger.getLogger("UnlimitedCoins")
        val libPath = "lib/arm64-v8a/libil2cpp.so"
        val libFile = get(libPath)
        val libBytes = libFile.readBytes()

        logger.info("UnlimitedCoins: loaded libil2cpp.so (${libBytes.size} bytes)")

        var patchedCount = 0
        var scannedCount = 0
        var skipCount = 0

        // Scan the entire lib for "sub w8, w8, w21" (4 bytes)
        var i = 0
        val lastStart = libBytes.size - 4
        while (i <= lastStart) {
            // Check if this offset has the sub instruction
            if (libBytes[i] == SUB_W8_W8_W21[0] &&
                libBytes[i + 1] == SUB_W8_W8_W21[1] &&
                libBytes[i + 2] == SUB_W8_W8_W21[2] &&
                libBytes[i + 3] == SUB_W8_W8_W21[3]) {

                scannedCount++

                // Look at next 1-4 instructions for "str w8, [xN, #0x18]"
                var foundStrOffset = -1
                for (j in 1..4) {
                    val strOffset = i + j * 4
                    if (strOffset + 4 > libBytes.size) break
                    val word = ((libBytes[strOffset].toInt() and 0xFF)) or
                        ((libBytes[strOffset + 1].toInt() and 0xFF) shl 8) or
                        ((libBytes[strOffset + 2].toInt() and 0xFF) shl 16) or
                        ((libBytes[strOffset + 3].toInt() and 0xFF) shl 24)

                    if (isStrW8Offset0x18(word)) {
                        foundStrOffset = strOffset
                        break
                    }
                    // If we hit a branch/ret before finding the str, give up
                    if ((word and 0xFC000000) == 0x14000000 ||  // b
                        (word and 0xFC000000) == 0x94000000 ||  // bl
                        (word and 0xFFFFFC00) == 0xD65F0000      // ret
                    ) {
                        break
                    }
                }

                if (foundStrOffset >= 0) {
                    // NOP the str instruction
                    libBytes[foundStrOffset] = NOP[0]
                    libBytes[foundStrOffset + 1] = NOP[1]
                    libBytes[foundStrOffset + 2] = NOP[2]
                    libBytes[foundStrOffset + 3] = NOP[3]
                    patchedCount++
                    if (patchedCount <= 5) {
                        logger.info("  patched str at 0x${foundStrOffset.toString(16)} (sub at 0x${i.toString(16)})")
                    }
                } else {
                    skipCount++
                }
            }
            i += 4  // ARM64 instructions are 4-byte aligned
        }

        logger.info("UnlimitedCoins: scanned $scannedCount 'sub w8, w8, w21' instructions")
        logger.info("UnlimitedCoins: patched $patchedCount coin-deduction points (str -> nop)")
        logger.info("UnlimitedCoins: skipped $skipCount (no str within 4 instructions)")

        if (patchedCount > 0) {
            libFile.writeBytes(libBytes)
            logger.info("UnlimitedCoins: SUCCESS! Coins will no longer decrease when spent.")
        } else {
            logger.severe("UnlimitedCoins: NO patches applied! Pattern not found.")
        }
    }
}
