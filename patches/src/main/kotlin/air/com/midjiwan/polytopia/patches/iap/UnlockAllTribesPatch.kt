/*
 * Unlock All Tribes patch for The Battle of Polytopia.
 *
 * HOW IT WORKS (native hex patch on libil2cpp.so):
 *
 * Instead of trying to activate debug mode or bypass billing, this patch
 * directly patches the NATIVE ARM64 code in libil2cpp.so to make the
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
 */

package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patcher.patch.hexPatch
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

    dependsOn(hexPatch(block = {
        val libPath = "lib/arm64-v8a/libil2cpp.so"

        // PurchaseManager.IsTribeUnlocked(TribeType)Z → return true
        // File offset: 0x04551B80 (virtual: 0x04555B80)
        // Pattern: 24 bytes (unique — 1 occurrence in 95MB binary)
        // Replace: mov w0, #1 + ret + 16 bytes original
        "FE 57 BE A9 F4 4F 01 A9 75 B1 00 B0 F3 03 01 2A F4 03 00 AA A8 66 57 39" asPatternTo
        "20 00 80 52 C0 03 5F D6 75 B1 00 B0 F3 03 01 2A F4 03 00 AA A8 66 57 39" inFile libPath

        // PurchaseManager.IsSkinUnlocked(SkinType)Z → return true
        // File offset: 0x04551BF8 (virtual: 0x04555BF8)
        // Pattern: 24 bytes (unique — 1 occurrence)
        // Replace: mov w0, #1 + ret + 16 bytes original
        "FE 57 BE A9 F4 4F 01 A9 75 B1 00 B0 F3 03 01 2A F4 03 00 AA A8 6A 57 39" asPatternTo
        "20 00 80 52 C0 03 5F D6 75 B1 00 B0 F3 03 01 2A F4 03 00 AA A8 6A 57 39" inFile libPath
    }))
}
