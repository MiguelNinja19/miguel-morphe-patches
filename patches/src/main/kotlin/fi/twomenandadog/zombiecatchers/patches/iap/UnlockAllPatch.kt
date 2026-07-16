// Unlock All + Unlimited Everything for Zombie Catchers.
// Uses NATIVE HEX PATCH on libcocos2dcpp.so — doesn't modify DEX,
// so C++ integrity check doesn't trigger.

package fi.twomenandadog.zombiecatchers.patches.iap

import app.morphe.patcher.patch.rawResourcePatch
import fi.twomenandadog.zombiecatchers.patches.shared.ZOMBIE_CATCHERS
import java.util.logging.Logger

@Suppress("unused")
val unlockAllPatch = rawResourcePatch(
    name = "Unlock all",
    description = "Hex patches libcocos2dcpp.so to: bypass Play Store " +
        "redirect (openPlayStore, openUrl, quitApplication), skip billing " +
        "connection, return null from getSHA256, and make getIntegerForKey " +
        "return 999999999 for Balance keys. Pure native patching — no DEX " +
        "modification, no C++ integrity check trigger.",
    default = true,
) {
    compatibleWith(ZOMBIE_CATCHERS)

    execute {
        val logger = Logger.getLogger("UnlockAll")

        val libPath = "lib/arm64-v8a/libcocos2dcpp.so"
        val libFile = get(libPath)
        val libBytes = libFile.readBytes()

        // ARM64 opcodes
        val returnVoid = byteArrayOf(
            0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte(),  // ret
            0x1F, 0x20, 0x03, 0xD5.toByte()             // nop
        )
        val returnNull = byteArrayOf(
            0xE0.toByte(), 0x03, 0x1F, 0xAA.toByte(),  // mov x0, #0 (null)
            0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()   // ret
        )
        val returnMaxInt = byteArrayOf(
            0x80.toByte(), 0x7E, 0x9C, 0x52.toByte(),  // mov w0, #0x3B9AC9FF (999999999)
            0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()   // ret
        )

        // (pattern, replacement, description)
        // Each pattern is 64 bytes (unique in 24MB binary)
        val patches = listOf(
            Triple(
                // JNI::openPlayStoreZCPage -> ret (prevent Play Store redirect)
                byteArrayOf(
                    0xFD.toByte(), 0x7B, 0xBE.toByte(), 0xA9.toByte(),
                    0xF3.toByte(), 0x0B, 0x00, 0xF9.toByte(),
                    0xFD.toByte(), 0x03, 0x00, 0x91.toByte(),
                    0x33, 0x5C, 0x00, 0xB0.toByte(),
                    0x73, 0x96, 0x47, 0xF9.toByte(),
                    0x68, 0x02, 0x40, 0xF9.toByte(),
                    0x48, 0x00, 0x00, 0xB5.toByte(),
                    0x8C.toByte(), 0x3B, 0x29, 0x94.toByte(),
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                ),
                returnVoid,
                "JNI::openPlayStoreZCPage -> ret"
            ),
            Triple(
                // JNI::connectStore -> ret (skip billing, prevent timeout)
                byteArrayOf(
                    0xFD.toByte(), 0x7B, 0xBD.toByte(), 0xA9.toByte(),
                    0xF5.toByte(), 0x0B, 0x00, 0xF9.toByte(),
                    0xF4.toByte(), 0x4F, 0x02, 0xA9.toByte(),
                    0xFD.toByte(), 0x03, 0x00, 0x91.toByte(),
                    0xF4.toByte(), 0x03, 0x00, 0xAA.toByte(),
                    0x21, 0xEB.toByte(), 0xFF.toByte(), 0xB0.toByte(),
                    0x21, 0x8C.toByte(), 0x38, 0x91.toByte(),
                    0x82.toByte(), 0xEC.toByte(), 0xFF.toByte(), 0xD0.toByte(),
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                ),
                returnVoid,
                "JNI::connectStore -> ret (skip billing)"
            ),
            Triple(
                // JNI::getSHA256 -> return null (bypass signature check)
                byteArrayOf(
                    0xFD.toByte(), 0x7B, 0xBD.toByte(), 0xA9.toByte(),
                    0xF6.toByte(), 0x57, 0x01, 0xA9.toByte(),
                    0xF4.toByte(), 0x4F, 0x02, 0xA9.toByte(),
                    0xFD.toByte(), 0x03, 0x00, 0x91.toByte(),
                    0x56, 0x5C, 0x00, 0xF0.toByte(),
                    0xF4.toByte(), 0x03, 0x00, 0xAA.toByte(),
                    0xF3.toByte(), 0x03, 0x08, 0xAA.toByte(),
                    0xD6.toByte(), 0x96, 0x47, 0xF9.toByte(),
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                ),
                returnNull,
                "JNI::getSHA256 -> return null"
            ),
            Triple(
                // JNI::openUrl -> ret (block URL opening as fallback)
                byteArrayOf(
                    0xFD.toByte(), 0x7B, 0xBE.toByte(), 0xA9.toByte(),
                    0xF4.toByte(), 0x4F, 0x01, 0xA9.toByte(),
                    0xFD.toByte(), 0x03, 0x00, 0x91.toByte(),
                    0x54, 0x5C, 0x00, 0xF0.toByte(),
                    0xF3.toByte(), 0x03, 0x00, 0xAA.toByte(),
                    0x94.toByte(), 0x96, 0x47, 0xF9.toByte(),
                    0x88.toByte(), 0x02, 0x40, 0xF9.toByte(),
                    0x48, 0x00, 0x00, 0xB5.toByte(),
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                ),
                returnVoid,
                "JNI::openUrl -> ret (block URLs)"
            ),
            Triple(
                // JNI::quitApplication -> ret (prevent app from quitting)
                byteArrayOf(
                    0xFD.toByte(), 0x7B, 0xBE.toByte(), 0xA9.toByte(),
                    0xF3.toByte(), 0x0B, 0x00, 0xF9.toByte(),
                    0xFD.toByte(), 0x03, 0x00, 0x91.toByte(),
                    0x01, 0xEB.toByte(), 0xFF.toByte(), 0xF0.toByte(),
                    0x21, 0x8C.toByte(), 0x38, 0x91.toByte(),
                    0x42.toByte(), 0xEB.toByte(), 0xFF.toByte(), 0x90.toByte(),
                    0x42.toByte(), 0x34, 0x30, 0x91.toByte(),
                    0x60, 0x00, 0x80.toByte(), 0x52.toByte(),
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                ),
                returnVoid,
                "JNI::quitApplication -> ret (prevent quit)"
            ),
            Triple(
                // Java_..._connectionResult -> ret (skip native callback processing)
                byteArrayOf(
                    0xFD.toByte(), 0x7B, 0xBC.toByte(), 0xA9.toByte(),
                    0xF8.toByte(), 0x5F, 0x01, 0xA9.toByte(),
                    0xF6.toByte(), 0x57, 0x02, 0xA9.toByte(),
                    0xF4.toByte(), 0x4F, 0x03, 0xA9.toByte(),
                    0xFD.toByte(), 0x03, 0x00, 0x91.toByte(),
                    0xF3.toByte(), 0x03, 0x00, 0xAA.toByte(),
                    0xC4.toByte(), 0x03, 0x00, 0xB4.toByte(),
                    0xD6.toByte(), 0x5D, 0x00, 0xB0.toByte(),
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                ),
                returnVoid,
                "connectionResult -> ret (skip callback)"
            ),
            Triple(
                // UserDefault::getIntegerForKey -> return 999999999
                // 64-byte unique pattern
                byteArrayOf(
                    0xFF.toByte(), 0xC3.toByte(), 0x01, 0xD1.toByte(),
                    0xFD.toByte(), 0x7B, 0x03, 0xA9.toByte(),
                    0xF8.toByte(), 0x5F, 0x04, 0xA9.toByte(),
                    0xF6.toByte(), 0x57, 0x05, 0xA9.toByte(),
                    0xF4.toByte(), 0x4F, 0x06, 0xA9.toByte(),
                    0xFD.toByte(), 0xC3.toByte(), 0x00, 0x91.toByte(),
                    0x58, 0xD0.toByte(), 0x3B, 0xD5.toByte(),
                    0xF3.toByte(), 0x03, 0x01, 0xAA.toByte(),
                    0xF5.toByte(), 0x03, 0x00, 0xAA.toByte(),
                    0x08, 0x17, 0x40, 0xF9.toByte(),
                    0xA1.toByte(), 0x43, 0x00, 0xD1.toByte(),
                    0xE0.toByte(), 0x03, 0x13, 0xAA.toByte(),
                    0xF4.toByte(), 0x03, 0x02, 0x2A.toByte(),
                    0xA8.toByte(), 0x83, 0x1F, 0xF8.toByte(),
                    0xBF.toByte(), 0x03, 0x1F, 0xF8.toByte(),
                    0x7E.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0x97.toByte()
                ),
                returnMaxInt,
                "getIntegerForKey -> return 999999999"
            ),
            Triple(
                // UserDefault::getBoolForKey -> return true (1)
                // 64-byte unique pattern (last 4 bytes differ from getIntegerForKey)
                byteArrayOf(
                    0xFF.toByte(), 0xC3.toByte(), 0x01, 0xD1.toByte(),
                    0xFD.toByte(), 0x7B, 0x03, 0xA9.toByte(),
                    0xF8.toByte(), 0x5F, 0x04, 0xA9.toByte(),
                    0xF6.toByte(), 0x57, 0x05, 0xA9.toByte(),
                    0xF4.toByte(), 0x4F, 0x06, 0xA9.toByte(),
                    0xFD.toByte(), 0xC3.toByte(), 0x00, 0x91.toByte(),
                    0x58, 0xD0.toByte(), 0x3B, 0xD5.toByte(),
                    0xF3.toByte(), 0x03, 0x01, 0xAA.toByte(),
                    0xF5.toByte(), 0x03, 0x00, 0xAA.toByte(),
                    0x08, 0x17, 0x40, 0xF9.toByte(),
                    0xA1.toByte(), 0x43, 0x00, 0xD1.toByte(),
                    0xE0.toByte(), 0x03, 0x13, 0xAA.toByte(),
                    0xF4.toByte(), 0x03, 0x02, 0x2A.toByte(),
                    0xA8.toByte(), 0x83, 0x1F, 0xF8.toByte(),
                    0xBF.toByte(), 0x03, 0x1F, 0xF8.toByte(),
                    0x65, 0x00, 0x00, 0x94.toByte()
                ),
                byteArrayOf(
                    0x20, 0x00, 0x80.toByte(), 0x52.toByte(),  // mov w0, #1 (true)
                    0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte(),  // ret
                    0x1F, 0x20, 0x03, 0xD5.toByte(),            // nop
                    0x1F, 0x20, 0x03, 0xD5.toByte()             // nop
                ),
                "getBoolForKey -> return true"
            )
        )

        logger.info("Unlock all: patching libcocos2dcpp.so with " + patches.size + " hex patches")

        var patchedCount = 0
        for ((pattern, replacement, description) in patches) {
            val idx = findPattern(libBytes, pattern)
            if (idx >= 0) {
                for (i in replacement.indices) {
                    libBytes[idx + i] = replacement[i]
                }
                patchedCount++
                logger.info("  patched: " + description + " (offset 0x" + idx.toString(16) + ")")
            } else {
                logger.info("  NOT FOUND: " + description)
            }
        }

        if (patchedCount > 0) {
            libFile.writeBytes(libBytes)
            logger.info("Unlock all COMPLETE: " + patchedCount + "/" + patches.size + " patches applied")
        } else {
            logger.info("Unlock all FAILED: no patterns matched")
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
