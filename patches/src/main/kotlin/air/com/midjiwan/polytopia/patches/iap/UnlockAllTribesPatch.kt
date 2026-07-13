/*
 * Unlock All Tribes patch for The Battle of Polytopia.
 *
 * HOW IT WORKS (debug mode approach):
 *
 * Polytopia has a hidden debug class called
 * EverythingUnlockedPlatformPurchaseManager that unlocks ALL tribes
 * and skins for free. It's activated when Config.debugUnlock == true.
 *
 * Config.Load() reads from a FILE (user.cfg) in the app's filesDir
 * (= Application.persistentDataPath on Android). The format is:
 *
 *     debugUnlock = true
 *     purchaseDebug = true
 *
 * This patch hooks MessagingUnityPlayerActivity.onCreate (the main
 * activity) and calls a Java extension to write user.cfg BEFORE the
 * Unity engine starts. When Config.Load() runs later in C#, it sees
 * debugUnlock=true and activates EverythingUnlockedPlatformPurchaseManager.
 *
 * WHY EXTENSION:
 *   The onCreate method has .locals 1 (only v0 available), which is
 *   insufficient for file I/O in pure smali (need 3+ registers for
 *   File, FileWriter, String). A minimal Java extension handles the
 *   file writing cleanly.
 */

package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA
import air.com.midjiwan.polytopia.patches.shared.MainActivityOnCreateFingerprint

@Suppress("unused")
val unlockAllTribesPatch = bytecodePatch(
    name = "Unlock all tribes",
    description = "Unlocks all 20 tribes (Xinxi, Imperius, Bardur, Oumaji, " +
        "Kickoo, Hoodrick, Luxidoor, Vengir, Zebasi, Aimo, Aquarion, " +
        "Elyrion, Polaris, Magma, Yadakk, Quetzali, Cymanti, Swamp, " +
        "Ikarus, Urkaz) by activating the game's built-in DEBUG MODE. " +
        "Writes a user.cfg file with debugUnlock=true before the game " +
        "loads, which makes Polytopia use its internal " +
        "EverythingUnlockedPlatformPurchaseManager (a debug class that " +
        "unlocks everything for free).",
    default = true,
) {
    compatibleWith(POLYTOPIA)

    // Load the extension DEX that contains the file-writing helper
    extendWith("extensions/extension.mpe")

    execute {
        // Hook MessagingUnityPlayerActivity.onCreate to call our extension
        // BEFORE the Unity engine starts and BEFORE Config.Load() runs.
        //
        // Smali flow:
        //   p0 = this (MessagingUnityPlayerActivity, which is a Context)
        //   p1 = Bundle (preserved for super.onCreate)
        //
        //   invoke-static {p0}, DebugUnlockPatch->writeDebugConfig(Context)V
        //
        // This fits in .locals 0 because we only use p0 (no local registers).
        MainActivityOnCreateFingerprint.method.addInstructions(0, """
            invoke-static {p0}, Lair/com/midjiwan/polytopia/extension/DebugUnlockPatch;->writeDebugConfig(Landroid/content/Context;)V
        """.trimIndent())
    }
}
