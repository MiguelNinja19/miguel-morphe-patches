/*
 * Free In-App Purchases patch for The Battle of Polytopia.
 *
 * HOW IT WORKS (native hex patch on libil2cpp.so):
 *
 * Previous smali-only patches didn't work because Polytopia's C# code
 * (compiled to IL2CPP) uses its OWN purchase validation, not Google
 * Play Billing's Purchase.isAcknowledged(). The game flow is:
 *
 *   User taps Buy
 *     → PurchaseManager.PurchaseProduct()
 *     → UnityPlatformPurchaseManager.OnPurchaseProduct()
 *     → Google Play Billing (launchBillingFlow)
 *     → Purchase fails/cancelled
 *     → OnProductPurchasedCallback(success=false)
 *     → ShowPurchaseErrorPopup()  ← THIS is the "Purchase error" dialog
 *
 * This patch uses NATIVE HEX PATCHING on libil2cpp.so (ARM64) to:
 *
 * HOOK 1: ShowPurchaseErrorPopup → return-void (ret immediately)
 *   Prevents the "Purchase error" dialog from appearing.
 *
 * HOOK 2: OnProductPurchasedCallback → return immediately
 *   Skips the failure handling that would show the error popup.
 *
 * HOOK 3: UnityPlatformPurchaseManager.IsProductUnlocked → return true
 *   Makes the game think the product is already unlocked.
 *   This is the KEY hook — it bypasses the game's own validation.
 *
 * HOOK 4: UnityPlatformPurchaseManager.OnPurchaseProduct → return immediately
 *   Skips the purchase flow entirely. Combined with HOOK 3, the game
 *   sees the product as unlocked without attempting purchase.
 *
 * Pattern from morphe-ai hoodles-patch-catalog.md:
 *   "Hex Patch on Native Library" — patch ARM64 code directly.
 *
 * Each function's first 8 bytes are replaced with:
 *   return-void:    ret (C0 03 5F D6)
 *   return true:    mov w0, #1 (20 00 80 52) + ret (C0 03 5F D6)
 *
 * Found via Il2CppInspectorRedux dump (metadata v39 supported).
 * Each 32-byte pattern is unique (1 occurrence in 95MB binary).
 */

package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.patch.rawResourcePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA
import java.util.logging.Logger

@Suppress("unused")
val freeInAppPurchasesPatch = rawResourcePatch(
    name = "Free in-app purchases",
    description = "Bypasses Polytopia's purchase validation by patching " +
        "the native IL2CPP library (libil2cpp.so). Makes IsProductUnlocked " +
        "return true, skips the purchase flow, and suppresses the " +
        "'Purchase error' dialog. Pure hex patching, no smali, no extension.",
    default = false,
) {
    compatibleWith(POLYTOPIA)

    execute {
        val logger = Logger.getLogger("FreeIAP")

        val libPath = "lib/arm64-v8a/libil2cpp.so"
        val libFile = get(libPath)
        val libBytes = libFile.readBytes()

        // ARM64 opcodes
        // return-void = ret = C0 03 5F D6
        // return true = mov w0, #1 (20 00 80 52) + ret (C0 03 5F D6)
        val returnVoid = byteArrayOf(
            0xC0.toByte(), 0x03.toByte(), 0x5F.toByte(), 0xD6.toByte(),
            0x1F.toByte(), 0x20.toByte(), 0x03.toByte(), 0xD5.toByte() // nop (pad)
        )
        val returnTrue = byteArrayOf(
            0x20.toByte(), 0x00.toByte(), 0x80.toByte(), 0x52.toByte(), // mov w0, #1
            0xC0.toByte(), 0x03.toByte(), 0x5F.toByte(), 0xD6.toByte()  // ret
        )

        // (pattern, replacement, description)
        val patches = listOf(
            Triple(
                // HOOK 1: ShowPurchaseErrorPopup → return-void
                // Prevents "Purchase error" dialog
                byteArrayOf(
                    0xFE.toByte(), 0x5F.toByte(), 0xBD.toByte(), 0xA9.toByte(),
                    0xF6.toByte(), 0x57.toByte(), 0x01.toByte(), 0xA9.toByte(),
                    0xF4.toByte(), 0x4F.toByte(), 0x02.toByte(), 0xA9.toByte(),
                    0x74.toByte(), 0xB1.toByte(), 0x00.toByte(), 0x90.toByte(),
                    0xF3.toByte(), 0x03.toByte(), 0x00.toByte(), 0x2A.toByte(),
                    0x88.toByte(), 0x96.toByte(), 0x57.toByte(), 0x39.toByte(),
                    0xA8.toByte(), 0x05.toByte(), 0x00.toByte(), 0x37.toByte(),
                    0x80.toByte(), 0x94.toByte(), 0x00.toByte(), 0x90.toByte()
                ),
                returnVoid,
                "ShowPurchaseErrorPopup -> return-void (suppress error dialog)"
            ),
            Triple(
                // HOOK 2: OnProductPurchasedCallback → return-void
                // Skips failure handling
                byteArrayOf(
                    0xFF.toByte(), 0x83.toByte(), 0x01.toByte(), 0xD1.toByte(),
                    0xFE.toByte(), 0x6F.toByte(), 0x01.toByte(), 0xA9.toByte(),
                    0xFA.toByte(), 0x67.toByte(), 0x02.toByte(), 0xA9.toByte(),
                    0xF8.toByte(), 0x5F.toByte(), 0x03.toByte(), 0xA9.toByte(),
                    0xF6.toByte(), 0x57.toByte(), 0x04.toByte(), 0xA9.toByte(),
                    0xF4.toByte(), 0x4F.toByte(), 0x05.toByte(), 0xA9.toByte(),
                    0x77.toByte(), 0xB1.toByte(), 0x00.toByte(), 0x90.toByte(),
                    0xF3.toByte(), 0x03.toByte(), 0x03.toByte(), 0x2A.toByte()
                ),
                returnVoid,
                "OnProductPurchasedCallback -> return-void (skip failure handling)"
            ),
            Triple(
                // HOOK 3: UnityPlatformPurchaseManager.IsProductUnlocked → return true
                // KEY HOOK: makes game think product is unlocked
                byteArrayOf(
                    0xFF.toByte(), 0x03.toByte(), 0x02.toByte(), 0xD1.toByte(),
                    0xFD.toByte(), 0x7B.toByte(), 0x02.toByte(), 0xA9.toByte(),
                    0xFC.toByte(), 0x6F.toByte(), 0x03.toByte(), 0xA9.toByte(),
                    0xFA.toByte(), 0x67.toByte(), 0x04.toByte(), 0xA9.toByte(),
                    0xF8.toByte(), 0x5F.toByte(), 0x05.toByte(), 0xA9.toByte(),
                    0xF6.toByte(), 0x57.toByte(), 0x06.toByte(), 0xA9.toByte(),
                    0xF4.toByte(), 0x4F.toByte(), 0x07.toByte(), 0xA9.toByte(),
                    0x54.toByte(), 0xB1.toByte(), 0x00.toByte(), 0x90.toByte()
                ),
                returnTrue,
                "UnityPlatformPurchaseManager.IsProductUnlocked -> return true"
            ),
            Triple(
                // HOOK 4: UnityPlatformPurchaseManager.OnPurchaseProduct → return-void
                // Skips purchase flow entirely
                byteArrayOf(
                    0xFE.toByte(), 0x5F.toByte(), 0xBD.toByte(), 0xA9.toByte(),
                    0xF6.toByte(), 0x57.toByte(), 0x01.toByte(), 0xA9.toByte(),
                    0xF4.toByte(), 0x4F.toByte(), 0x02.toByte(), 0xA9.toByte(),
                    0x57.toByte(), 0xB1.toByte(), 0x00.toByte(), 0x90.toByte(),
                    0x76.toByte(), 0x94.toByte(), 0x00.toByte(), 0x90.toByte(),
                    0xF5.toByte(), 0x03.toByte(), 0x02.toByte(), 0xAA.toByte(),
                    0xE8.toByte(), 0xEE.toByte(), 0x57.toByte(), 0x39.toByte(),
                    0xD6.toByte(), 0x3A.toByte(), 0x45.toByte(), 0xF9.toByte()
                ),
                returnVoid,
                "UnityPlatformPurchaseManager.OnPurchaseProduct -> return-void (skip purchase)"
            )
        )

        logger.info("Free IAP: patching libil2cpp.so with ${patches.size} hex patches")

        var patchedCount = 0
        for ((pattern, replacement, description) in patches) {
            val idx = findPattern(libBytes, pattern)
            if (idx >= 0) {
                for (i in replacement.indices) {
                    libBytes[idx + i] = replacement[i]
                }
                patchedCount++
                logger.info("  patched: $description (offset 0x${idx.toString(16)})")
            } else {
                logger.info("  NOT FOUND: $description")
            }
        }

        if (patchedCount > 0) {
            libFile.writeBytes(libBytes)
            logger.info("Free IAP COMPLETE: $patchedCount/${patches.size} patches applied")
        } else {
            logger.info("Free IAP FAILED: no patterns matched")
        }
    }
}

private fun findPattern(haystack: ByteArray, needle: ByteArray): Int {
    if (needle.isEmpty() || haystack.size < needle.size) return -1
    val lastStart = haystack.size - needle.size
    for (i in 0..lastStart) {
        var found = true
        for (j in needle.indices) {
            if (haystack[i + j] != needle[j]) {
                found = false
                break
            }
        }
        if (found) return i
    }
    return -1
}
