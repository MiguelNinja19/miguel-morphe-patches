/*
 * Unlock all patch for Supreme Duelist Stickman v4.0.5.
 *
 * STRATEGY: Offset-based hex patches with byte verification.
 *
 * This is the FULL VERSION that unlocks EVERYTHING:
 * - Weapons (AchatWeapon) - free purchase, no level requirement, no coins needed
 * - Skins (AchatSkin) - free purchase (handled via UnlimitedCoinsPatch)
 * - Colors (AchatColor) - free purchase (handled via UnlimitedCoinsPatch)
 * - Mini-games/modes (buyMiniGames) - free purchase
 * - Ads removed (get_AdsRemoved -> true)
 *
 * CRITICAL: This patch now includes LEVEL CHECKS for AchatWeapon.
 * AchatWeapon has 2 level checks BEFORE the coin check:
 *   - Level >= 51 (cmp w9, #0x33; b.lt)
 *   - Counter >= 499 (cmp w9, #0x1f3; b.lt)
 * These were identified in the original 4.0.5 lib and verified.
 *
 * Combined with UnlimitedCoinsPatch, the player has:
 * - Unlimited coins (never decrease)
 * - All weapons unlocked for free (no level requirement!)
 * - All skins unlocked for free (via UnlimitedCoinsPatch)
 * - All colors unlocked for free (via UnlimitedCoinsPatch)
 * - All modes unlocked for free
 * - No ads
 *
 * All offsets VERIFIED in the ORIGINAL 4.0.5 libil2cpp.so (61MB,
 * il2cpp v31, sha256: 1a7400eefe42d7e0cb8a80fa5d9cb61f99ca6bd95df2c929c6268e1c5452fa57)
 *
 * If verification fails (e.g., game updated), patch is skipped
 * with a warning — doesn't break other patches.
 */

package com.Neurononfire.SupremeDuelist.patches.iap

import app.morphe.patcher.patch.rawResourcePatch
import com.Neurononfire.SupremeDuelist.patches.shared.SUPREME_DUELIST
import java.util.logging.Logger

// Patch definition: offset, expected original bytes, replacement bytes, description
private data class HexPatch(
    val offset: Int,
    val expected: ByteArray,
    val replacement: ByteArray,
    val desc: String,
)

// Helper to create byte arrays from hex ints (avoids .toByte() on each)
private fun hb(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

// ARM64 instruction encodings (little-endian byte order)
private val NOP = hb(0x1f, 0x20, 0x03, 0xd5)
private val MOV_W0_1 = hb(0x20, 0x00, 0x80, 0x52)
private val RET = hb(0xc0, 0x03, 0x5f, 0xd6)

// All patches for ORIGINAL v4.0.5 lib (61,143,840 bytes, il2cpp v31):
private val PATCHES: List<HexPatch> = listOf(
    // === AchatWeapon LEVEL CHECKS (NEW!) ===
    // These check the player's level/progress before allowing purchase.
    // NOP-ing them removes the level requirement entirely.

    // 1. AchatWeapon level check 1: NOP b.lt at 0x017b25f8
    //    After: cmp w9, #0x33 (51) - "is level >= 51?"
    //    b.lt #0x17b2604 - "if level < 51, skip unlock"
    //    NOP this to remove the level requirement.
    HexPatch(0x017b25f8,
        hb(0x6b, 0x00, 0x00, 0x54),
        NOP,
        "AchatWeapon level check 1: NOP b.lt (level >= 51 required)"),

    // 2. AchatWeapon level check 2: NOP b.lt at 0x017b2624
    //    After: cmp w9, #0x1f3 (499) - "is counter >= 499?"
    //    b.lt #0x17b2648 - "if counter < 499, skip unlock"
    //    This is likely an XP-based or progression-based requirement.
    //    NOP this to remove the progression requirement.
    HexPatch(0x017b2624,
        hb(0x2b, 0x01, 0x00, 0x54),
        NOP,
        "AchatWeapon level check 2: NOP b.lt (counter >= 499 required)"),

    // === AchatWeapon COIN CHECK ===
    // 3. AchatWeapon coin check: NOP b.ls at 0x017b26b8
    //    After: cmp w8, #0x32 (50) - "do you have 50 coins?"
    //    b.ls #0x17b271c - "if coins < 50, fail purchase"
    //    NOP this to make the purchase succeed regardless of coin count.
    HexPatch(0x017b26b8,
        hb(0x29, 0x03, 0x00, 0x54),
        NOP,
        "AchatWeapon coin check: NOP b.ls (price=50)"),

    // === buyMiniGames COIN CHECK ===
    // 4. buyMiniGames coin check: NOP b.ls at 0x016e0e80
    //    After: cmp w9, #3 - "do you have 3 mini-games?"
    //    b.ls #0x16e0ed8 - "if count < 3, fail purchase"
    //    NOP this to unlock all mini-games for free.
    HexPatch(0x016e0e80,
        hb(0xc9, 0x02, 0x00, 0x54),
        NOP,
        "buyMiniGames coin check: NOP b.ls (price=3)"),

    // === RemoveAds.get_AdsRemoved ===
    // 5. RemoveAds.get_AdsRemoved: replace first 8 bytes with mov w0,#1; ret
    //    Original: e0 03 13 aa (mov x0, x19) + f4 4f 42 a9 (ldp x20, x19, [sp, #0x20])
    //    Patched:  20 00 80 52 (mov w0, #1) + c0 03 5f d6 (ret)
    //    This makes get_AdsRemoved() always return true, activating No Ads state.
    HexPatch(0x01373f70,
        hb(0xe0, 0x03, 0x13, 0xaa, 0xf4, 0x4f, 0x42, 0xa9),
        MOV_W0_1 + RET,
        "RemoveAds.get_AdsRemoved: mov w0,#1; ret (no ads)"),

    // NOTE on AchatSkin and AchatColor:
    // These methods are called in a LOOP for each skin/color in a category.
    // The cmp w9, w20; b.ls pattern appears hundreds of times (one per item).
    // NOPing all of them would be unsafe (could affect unrelated code).
    // With UnlimitedCoinsPatch active, the player has unlimited coins to
    // buy any skin/color they want. So we don't need to NOP these checks.
    //
    // The level checks we DID NOP are unique to AchatWeapon and verified
    // by their unique cmp values (cmp w9, #51 and cmp w9, #499).
)

private fun ByteArray.toHex(): String =
    joinToString(" ") { "%02x".format(it) }

@Suppress("unused")
val unlockAllPatch = rawResourcePatch(
    name = "Unlock all (weapons, modes, no ads + level bypass)",
    description = "Hex patches libil2cpp.so at VERIFIED offsets to: " +
        "(1) NOP the level checks in AchatWeapon (2 level checks: " +
        "level >= 51 and counter >= 499) — REMOVES LEVEL REQUIREMENT! " +
        "(2) NOP the coin check in AchatWeapon (free weapons, price=50). " +
        "(3) NOP the coin check in buyMiniGames (free mini-games). " +
        "(4) Patch RemoveAds.get_AdsRemoved to always return true (no ads). " +
        "Combined with UnlimitedCoinsPatch (which prevents coins from " +
        "decreasing), this gives the player: unlimited coins + all " +
        "weapons unlocked (no level requirement!) + all modes + no ads. " +
        "Skins and colors are NOT patched here because their coin check " +
        "pattern appears hundreds of times in a loop and would be unsafe " +
        "to NOP — with unlimited coins, the player can buy them anyway. " +
        "All offsets verified against the ORIGINAL 4.0.5 lib (61MB, " +
        "il2cpp v31, sha256 starts with 1a7400ee).",
    default = true,
) {
    compatibleWith(SUPREME_DUELIST)

    execute {
        val logger = Logger.getLogger("UnlockAll")
        val libPath = "lib/arm64-v8a/libil2cpp.so"
        val libFile = get(libPath)
        val libBytes = libFile.readBytes()

        logger.info("UnlockAll: loaded libil2cpp.so (${libBytes.size} bytes)")
        logger.info("UnlockAll: expected lib size = 61,143,840 bytes (original 4.0.5)")

        var appliedCount = 0
        var skippedCount = 0

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

        if (appliedCount > 0) {
            libFile.writeBytes(libBytes)
            logger.info("UnlockAll: applied $appliedCount of ${PATCHES.size} patches, skipped $skippedCount")
        } else {
            logger.severe("UnlockAll: NO patches were applied! All $skippedCount skipped due to byte mismatches.")
        }
    }
}
