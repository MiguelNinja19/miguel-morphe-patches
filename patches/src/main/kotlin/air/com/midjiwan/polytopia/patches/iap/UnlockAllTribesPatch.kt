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
 * activity) and writes user.cfg BEFORE the Unity engine starts.
 * When Config.Load() runs later in C#, it sees debugUnlock=true
 * and activates EverythingUnlockedPlatformPurchaseManager.
 *
 * Evidence from reverse-engineering global-metadata.dat:
 *   - EverythingUnlockedPlatformPurchaseManager.cs (debug god mode)
 *   - Config class with debugUnlock field loaded from user.cfg
 *   - Paths class with configFileName = "user.cfg"
 *   - String literals in metadata: "user.cfg", "user.json", "user_config.txt"
 *
 * Pure smali, no extension DEX.
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

    execute {
        // Hook MessagingUnityPlayerActivity.onCreate to write user.cfg
        // BEFORE the Unity engine starts and BEFORE Config.Load() runs.
        //
        // Smali flow:
        //   p0 = this (Activity, which is a Context)
        //   p1 = Bundle (preserved for super.onCreate)
        //
        //   v0 = getFilesDir()                    -> File
        //   v1 = new File(v0, "user.cfg")         -> File
        //   v0 = new FileWriter(v1, false)        -> FileWriter (overwrite)
        //   v0.write("debugUnlock = true\npurchaseDebug = true\n")
        //   v0.close()
        MainActivityOnCreateFingerprint.method.addInstructions(0, """
            # Get filesDir (= Application.persistentDataPath on Android)
            invoke-virtual {p0}, Lcom/google/firebase/MessagingUnityPlayerActivity;->getFilesDir()Ljava/io/File;
            move-result-object v0

            # If filesDir is null, skip
            if-eqz v0, :write_done

            # Create File object for "user.cfg"
            new-instance v1, Ljava/io/File;
            const-string v2, "user.cfg"
            invoke-direct {v1, v0, v2}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

            # Create FileWriter(file, false) - false = overwrite
            new-instance v0, Ljava/io/FileWriter;
            const/4 v2, 0x0
            invoke-direct {v0, v1, v2}, Ljava/io/FileWriter;-><init>(Ljava/io/File;Z)V

            # Write config content (key = value format, parsed by Config.ParseBool)
            const-string v1, "debugUnlock = true\\npurchaseDebug = true\\n"
            invoke-virtual {v0, v1}, Ljava/io/Writer;->write(Ljava/lang/String;)V

            # Close the writer (flush + close)
            invoke-virtual {v0}, Ljava/io/Writer;->close()V

            :write_done
        """.trimIndent())
    }
}
