/*
 * Unlock All Tribes patch for The Battle of Polytopia.
 *
 * HOW IT WORKS (native hex patch on libil2cpp.so):
 *
 * Directly patches the NATIVE ARM64 code in libil2cpp.so to make the
 * methods IsTribeUnlocked() and IsSkinUnlocked() always return true.
 *
 * This is the "Hex Patch on Native Library" pattern from the morphe-ai
 * hoodles-patch-catalog.md. It's the cleanest approach because:
 * - No billing bypass needed
 * - No debug mode needed
 * - No Purchase fake creation
 * - No Java extension
 * - Works at the C# (IL2CPP) level directly
 *
 * The patch replaces the first 8 bytes of each function with:
 *   mov w0, #1   (20 00 80 52)  — set return value to true
 *   ret          (C0 03 5F D6)  — return immediately
 *
 * Functions patched (found via Il2CppInspectorRedux dump):
 *   PurchaseManager.IsTribeUnlocked(TribeType) → return true
 *   PurchaseManager.IsSkinUnlocked(SkinType) → return true
 *
 * This makes ALL tribes and skins appear as unlocked in the tribe picker.
 * The user can select and play with any tribe without purchasing.
 *
 * API: Morphe rawResourcePatch + get() to read the .so file, then
 * replace the byte pattern and writeBytes() back. The .so file is
 * available as a raw resource in the APK split.
 */

package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.patch.rawResourcePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA

@Suppress("unused")
val unlockAllTribesPatch = rawResourcePatch(
    name = "Unlock all tribes",
    description = "Unlocks all 20 tribes (Xinxi, Imperius, Bardur, Oumaji, " +
        "Kickoo, Hoodrick, Luxidoor, Vengir, Zebasi, Aimo, Aquarion, " +
        "Elyrion, Polaris, Magma, Yadakk, Quetzali, Cymanti, Swamp, " +
        "Ikarus, Urkaz) and all skins by patching the native IL2CPP " +
        "library (libil2cpp.so). Makes IsTribeUnlocked() and " +
        "IsSkinUnlocked() always return true via ARM64 hex patching.",
    default = true,
) {
    compatibleWith(POLYTOPIA)

    execute {
        // ============================================================
        // Patch libil2cpp.so: IsTribeUnlocked + IsSkinUnlocked → true
        // ============================================================
        // ARM64 opcodes:
        //   mov w0, #1 = 20 00 80 52  (set return value to 1/true)
        //   ret         = C0 03 5F D6  (return immediately)
        //
        // Each function starts with the same 8-byte prologue:
        //   FE 57 BE A9 F4 4F 01 A9
        // but the next 16 bytes are unique per function.
        //
        // We search for the 24-byte pattern (prologue + 16 unique bytes)
        // and replace the first 8 bytes with mov+ret.
        // ============================================================

        val libPath = "lib/arm64-v8a/libil2cpp.so"
        val libFile = get(libPath)
        val libBytes = libFile.readBytes()

        // ARM64 patch: mov w0, #1 + ret
        val patch = byteArrayOf(
            0x20, 0x00, 0x80.toByte(), 0x52,  // mov w0, #1
            0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()   // ret
        )

        // PurchaseManager.IsTribeUnlocked(TribeType)Z
        // Pattern (24 bytes, unique - 1 occurrence in 95MB):
        //   FE 57 BE A9 F4 4F 01 A9 75 B1 00 B0 F3 03 01 2A F4 03 00 AA A8 66 57 39
        val tribePattern = byteArrayOf(
            0xFE.toByte(), 0x57, 0xBE.toByte(), 0xA9.toByte(),
            0xF4.toByte(), 0x4F, 0x01, 0xA9.toByte(),
            0x75, 0xB1, 0x00, 0xB0.toByte(),
            0xF3.toByte(), 0x03, 0x01, 0x2A.toByte(),
            0xF4.toByte(), 0x03, 0x00, 0xAA.toByte(),
            0xA8.toByte(), 0x66, 0x57, 0x39
        )

        // PurchaseManager.IsSkinUnlocked(SkinType)Z
        // Pattern (24 bytes, unique - 1 occurrence):
        //   FE 57 BE A9 F4 4F 01 A9 75 B1 00 B0 F3 03 01 2A F4 03 00 AA A8 6A 57 39
        val skinPattern = byteArrayOf(
            0xFE.toByte(), 0x57, 0xBE.toByte(), 0xA9.toByte(),
            0xF4.toByte(), 0x4F, 0x01, 0xA9.toByte(),
            0x75, 0xB1, 0x00, 0xB0.toByte(),
            0xF3.toByte(), 0x03, 0x01, 0x2A.toByte(),
            0xF4.toByte(), 0x03, 0x00, 0xAA.toByte(),
            0xA8.toByte(), 0x6A, 0x57, 0x39
        )

        // Patch IsTribeUnlocked
        val tribeIdx = libBytes.indexOfSlice(tribePattern)
        if (tribeIdx >= 0) {
            for (i in patch.indices) {
                libBytes[tribeIdx + i] = patch[i]
            }
        }

        // Patch IsSkinUnlocked
        val skinIdx = libBytes.indexOfSlice(skinPattern)
        if (skinIdx >= 0) {
            for (i in patch.indices) {
                libBytes[skinIdx + i] = patch[i]
            }
        }

        // Write patched bytes back
        libFile.writeBytes(libBytes)
    }
}
