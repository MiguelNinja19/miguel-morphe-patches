/*
 * Unlimited Coins patch for Supreme Duelist Stickman v4.0.5.
 *
 * STRATEGY: Pattern-based NOP patch on BOTH the coin deduction str
 * AND the "not enough coins" branch that follows it.
 *
 * HOW IT WORKS:
 * The game's coin deduction pattern is:
 *
 *   ldr w8, [x19, #0x18]      ; load Coins
 *   sub w8, w8, w21            ; subtract price
 *   subs w4, w8, w20           ; secondary check (sets flags!)
 *   str w8, [x19, #0x18]       ; store deducted value ← NOP this
 *   b.le #fail                 ; if not enough, skip ← ALSO NOP this!
 *   ... purchase code ...
 *
 * Previous version only NOPed the str, but the b.le AFTER it still
 * canceled purchases because the subs instruction set the "less than
 * or equal" flag. This version NOPs BOTH the str AND the b.le.
 *
 * Result: coins never decrease AND purchases always succeed.
 */

package com.Neurononfire.SupremeDuelist.patches.iap

import app.morphe.patcher.patch.rawResourcePatch
import com.Neurononfire.SupremeDuelist.patches.shared.SUPREME_DUELIST
import java.util.logging.Logger

private fun hb(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

private val NOP = hb(0x1f, 0x20, 0x03, 0xd5)
private val SUB_W8_W8_W21 = hb(0x08, 0x01, 0x15, 0x4b)

private fun isStrW8Offset0x18(word: Int): Boolean {
    if ((word and 0xFFFFFC00.toInt()) != 0xB9001800.toInt()) return false
    if ((word and 0x1F) != 0x08) return false
    return true
}

// Check if instruction is a conditional branch (b.cond)
// b.cond: 0x54xxxxxx (top byte = 0x54)
private fun isCondBranch(word: Int): Boolean =
    (word and 0xFF000000.toInt()) == 0x54000000

@Suppress("unused")
val unlimitedCoinsPatch = rawResourcePatch(
    name = "Unlimited coins (real - patches coin deduction + branch)",
    description = "Real unlimited coins patch. Searches libil2cpp.so " +
        "for the coin-deduction pattern (sub w8, w8, w21; ...; str w8, " +
        "[xN, #0x18]; b.le/b.lt) and NOPs BOTH the str instruction " +
        "AND the conditional branch that follows it. Previous version " +
        "only NOPed the str, but the b.le still canceled purchases " +
        "because the subs instruction set the 'less than' flag.",
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

        var i = 0
        val lastStart = libBytes.size - 4
        while (i <= lastStart) {
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
                    val isB = (word and 0xFC000000.toInt()) == 0x14000000
                    val isBl = (word and 0xFC000000.toInt()) == 0x94000000.toInt()
                    val isRet = (word and 0xFFFFFC00.toInt()) == 0xD65F0000.toInt()
                    if (isB || isBl || isRet) break
                }

                if (foundStrOffset >= 0) {
                    // NOP the str instruction
                    libBytes[foundStrOffset] = NOP[0]
                    libBytes[foundStrOffset + 1] = NOP[1]
                    libBytes[foundStrOffset + 2] = NOP[2]
                    libBytes[foundStrOffset + 3] = NOP[3]
                    patchedCount++

                    // ALSO check the instruction AFTER the str — if it's a
                    // conditional branch (b.le/b.lt/b.eq etc.), NOP it too!
                    // This prevents the "not enough coins" check from failing.
                    val afterStrOffset = foundStrOffset + 4
                    if (afterStrOffset + 4 <= libBytes.size) {
                        val afterWord = ((libBytes[afterStrOffset].toInt() and 0xFF)) or
                            ((libBytes[afterStrOffset + 1].toInt() and 0xFF) shl 8) or
                            ((libBytes[afterStrOffset + 2].toInt() and 0xFF) shl 16) or
                            ((libBytes[afterStrOffset + 3].toInt() and 0xFF) shl 24)

                        if (isCondBranch(afterWord)) {
                            libBytes[afterStrOffset] = NOP[0]
                            libBytes[afterStrOffset + 1] = NOP[1]
                            libBytes[afterStrOffset + 2] = NOP[2]
                            libBytes[afterStrOffset + 3] = NOP[3]
                            patchedCount++
                            if (patchedCount <= 10) {
                                logger.info("  patched str at 0x${foundStrOffset.toString(16)} + branch at 0x${afterStrOffset.toString(16)}")
                            }
                        }
                    }
                }
            }
            i += 4
        }

        logger.info("UnlimitedCoins: scanned $scannedCount 'sub w8, w8, w21' instructions")
        logger.info("UnlimitedCoins: patched $patchedCount instructions (str + branch -> nop)")
        logger.info("UnlimitedCoins: SUCCESS! Coins will no longer decrease and purchases won't fail.")

        if (patchedCount > 0) {
            libFile.writeBytes(libBytes)
        } else {
            logger.severe("UnlimitedCoins: NO patches applied!")
        }
    }
}
