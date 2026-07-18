// Unlock All + Unlimited Everything for Zombie Catchers.
//
// ROOT CAUSE: Even when we change the Application class in the manifest,
// CoreComponentFactory.<clinit>() still calls StartupLauncher.launch()
// which runs PairIP's encrypted VM bytecode BEFORE the app starts.
//
// The mod removes this call from BOTH:
// - androidx.core.app.CoreComponentFactory.<clinit>
// - android.app.AppComponentFactory.<clinit>
//
// This patch does the same + manifest change + hex patch .so.

package fi.twomenandadog.zombiecatchers.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import fi.twomenandadog.zombiecatchers.patches.shared.ZOMBIE_CATCHERS
import fi.twomenandadog.zombiecatchers.patches.shared.CoreComponentFactoryClinitFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.AppComponentFactoryClinitFingerprint
import java.util.logging.Logger

@Suppress("unused")
val unlockAllPatch = bytecodePatch(
    name = "Unlock all",
    description = "Removes PairIP from manifest, removes StartupLauncher " +
        "calls from ComponentFactory, and hex patches libcocos2dcpp.so " +
        "for unlimited currencies.",
    default = true,
) {
    compatibleWith(ZOMBIE_CATCHERS)

    dependsOn(resourcePatch(
        name = "Remove PairIP manifest",
        description = "Changes Application class and appComponentFactory"
    ) {
        execute {
            val logger = Logger.getLogger("ManifestPatch")
            document("AndroidManifest.xml").use { doc ->
                val app = doc.getElementsByTagName("application").item(0) as org.w3c.dom.Element
                // Change Application class from PairIP to real app
                app.setAttribute("android:name", "fi.twomenandadog.zombiecatchers.ZombieCatchersApp")
                // Change appComponentFactory to default (no PairIP)
                app.setAttribute("android:appComponentFactory", "android.app.AppComponentFactory")
                logger.info("Changed Application to ZombieCatchersApp and appComponentFactory to default")
            }
        }
    })

    execute {
        val logger = Logger.getLogger("UnlockAll")
        var count = 0

        // HOOK 1: Remove StartupLauncher.launch() from CoreComponentFactory.<clinit>
        // This is the KEY fix — PairIP VM runs here BEFORE the app starts
        CoreComponentFactoryClinitFingerprint.methodOrNull?.let {
            it.addInstructions(0, "return-void")
            count++
            logger.info("  patched: CoreComponentFactory.<clinit> -> return-void (remove PairIP startup)")
        }

        // HOOK 2: Remove StartupLauncher.launch() from AppComponentFactory.<clinit>
        AppComponentFactoryClinitFingerprint.methodOrNull?.let {
            it.addInstructions(0, "return-void")
            count++
            logger.info("  patched: AppComponentFactory.<clinit> -> return-void (remove PairIP startup)")
        }

        // HOOK 3: Hex patch libcocos2dcpp.so
        val libPath = "lib/arm64-v8a/libcocos2dcpp.so"
        val libFile = get(libPath)
        val libBytes = libFile.readBytes()

        fun hb(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

        val returnVoid = hb(0xC0, 0x03, 0x5F, 0xD6, 0x1F, 0x20, 0x03, 0xD5)
        val returnNull = hb(0xE0, 0x03, 0x1F, 0xAA, 0xC0, 0x03, 0x5F, 0xD6)
        val returnMaxInt = hb(0x80, 0x7E, 0x9C, 0x52, 0xC0, 0x03, 0x5F, 0xD6)
        val returnTrue = hb(0x20, 0x00, 0x80, 0x52, 0xC0, 0x03, 0x5F, 0xD6, 0x1F, 0x20, 0x03, 0xD5, 0x1F, 0x20, 0x03, 0xD5)

        val patches = listOf(
            Triple(
                hb(0xFD, 0x7B, 0xBE, 0xA9, 0xF3, 0x0B, 0x00, 0xF9, 0xFD, 0x03, 0x00, 0x91, 0x33, 0x5C, 0x00, 0xB0, 0x73, 0x96, 0x47, 0xF9, 0x68, 0x02, 0x40, 0xF9, 0x48, 0x00, 0x00, 0xB5, 0x8C, 0x3B, 0x29, 0x94),
                returnVoid,
                "openPlayStoreZCPage -> ret"
            ),
            Triple(
                hb(0xFD, 0x7B, 0xBD, 0xA9, 0xF5, 0x0B, 0x00, 0xF9, 0xF4, 0x4F, 0x02, 0xA9, 0xFD, 0x03, 0x00, 0x91, 0xF4, 0x03, 0x00, 0xAA, 0x21, 0xEB, 0xFF, 0xB0, 0x21, 0x8C, 0x38, 0x91, 0x82, 0xEC, 0xFF, 0xD0),
                returnVoid,
                "connectStore -> ret"
            ),
            Triple(
                hb(0xFD, 0x7B, 0xBD, 0xA9, 0xF6, 0x57, 0x01, 0xA9, 0xF4, 0x4F, 0x02, 0xA9, 0xFD, 0x03, 0x00, 0x91, 0x56, 0x5C, 0x00, 0xF0, 0xF4, 0x03, 0x00, 0xAA, 0xF3, 0x03, 0x08, 0xAA, 0xD6, 0x96, 0x47, 0xF9),
                returnNull,
                "getSHA256 -> return null"
            ),
            Triple(
                hb(0xFD, 0x7B, 0xBE, 0xA9, 0xF4, 0x4F, 0x01, 0xA9, 0xFD, 0x03, 0x00, 0x91, 0x54, 0x5C, 0x00, 0xF0, 0xF3, 0x03, 0x00, 0xAA, 0x94, 0x96, 0x47, 0xF9, 0x88, 0x02, 0x40, 0xF9, 0x48, 0x00, 0x00, 0xB5),
                returnVoid,
                "openUrl -> ret"
            ),
            Triple(
                hb(0xFD, 0x7B, 0xBE, 0xA9, 0xF3, 0x0B, 0x00, 0xF9, 0xFD, 0x03, 0x00, 0x91, 0x01, 0xEB, 0xFF, 0xF0, 0x21, 0x8C, 0x38, 0x91, 0x42, 0xEB, 0xFF, 0x90, 0x42, 0x34, 0x30, 0x91, 0x60, 0x00, 0x80, 0x52),
                returnVoid,
                "quitApplication -> ret"
            ),
            Triple(
                hb(0xFD, 0x7B, 0xBC, 0xA9, 0xF8, 0x5F, 0x01, 0xA9, 0xF6, 0x57, 0x02, 0xA9, 0xF4, 0x4F, 0x03, 0xA9, 0xFD, 0x03, 0x00, 0x91, 0xF3, 0x03, 0x00, 0xAA, 0xC4, 0x03, 0x00, 0xB4, 0xD6, 0x5D, 0x00, 0xB0),
                returnVoid,
                "connectionResult -> ret"
            ),
            Triple(
                hb(0xFF, 0xC3, 0x01, 0xD1, 0xFD, 0x7B, 0x03, 0xA9, 0xF8, 0x5F, 0x04, 0xA9, 0xF6, 0x57, 0x05, 0xA9, 0xF4, 0x4F, 0x06, 0xA9, 0xFD, 0xC3, 0x00, 0x91, 0x58, 0xD0, 0x3B, 0xD5, 0xF3, 0x03, 0x01, 0xAA, 0xF5, 0x03, 0x00, 0xAA, 0x08, 0x17, 0x40, 0xF9, 0xA1, 0x43, 0x00, 0xD1, 0xE0, 0x03, 0x13, 0xAA, 0xF4, 0x03, 0x02, 0x2A, 0xA8, 0x83, 0x1F, 0xF8, 0xBF, 0x03, 0x1F, 0xF8, 0x7E, 0xFE, 0xFF, 0x97),
                returnMaxInt,
                "getIntegerForKey -> return 999999999"
            ),
            Triple(
                hb(0xFF, 0xC3, 0x01, 0xD1, 0xFD, 0x7B, 0x03, 0xA9, 0xF8, 0x5F, 0x04, 0xA9, 0xF6, 0x57, 0x05, 0xA9, 0xF4, 0x4F, 0x06, 0xA9, 0xFD, 0xC3, 0x00, 0x91, 0x58, 0xD0, 0x3B, 0xD5, 0xF3, 0x03, 0x01, 0xAA, 0xF5, 0x03, 0x00, 0xAA, 0x08, 0x17, 0x40, 0xF9, 0xA1, 0x43, 0x00, 0xD1, 0xE0, 0x03, 0x13, 0xAA, 0xF4, 0x03, 0x02, 0x2A, 0xA8, 0x83, 0x1F, 0xF8, 0xBF, 0x03, 0x1F, 0xF8, 0x65, 0x00, 0x00, 0x94),
                returnTrue,
                "getBoolForKey -> return true"
            )
        )

        for ((pattern, replacement, description) in patches) {
            val idx = findPattern(libBytes, pattern)
            if (idx >= 0) {
                for (i in replacement.indices) {
                    libBytes[idx + i] = replacement[i]
                }
                count++
                logger.info("  patched: " + description)
            }
        }

        libFile.writeBytes(libBytes)
        logger.info("Unlock all COMPLETE: " + count + " patches applied")
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
