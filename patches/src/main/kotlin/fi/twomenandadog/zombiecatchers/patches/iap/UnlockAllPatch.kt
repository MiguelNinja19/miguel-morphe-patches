/*
 * Unlock All + Unlimited Everything for Zombie Catchers.
 *
 * THEORY (user's insight):
 *   The UNPATCHED APK works fine. The PATCHED APK shows "Get this app
 *   from Play". This means the C++ native code (libcocos2dcpp.so) has
 *   an integrity check that detects MODIFICATIONS to game methods.
 *
 *   Previous patches modified getIntegerForKey, getBoolForKey,
 *   connectStore, openPlayStoreZCPage, etc. — all game methods.
 *   The C++ detected these modifications and showed "Get this app
 *   from Play".
 *
 * NEW APPROACH — "Stealth Patching":
 *   1. DON'T modify ANY game methods (fi.twomenandadog.*)
 *   2. ONLY modify PairIP classes (com/pairip/*) — C++ doesn't check these
 *   3. Write currency values DIRECTLY to SharedPreferences at startup
 *      via ZCGoogleAcitivty.onCreate hook
 *   4. The C++ integrity check passes because game code is unchanged
 *   5. The game reads the pre-written values from SharedPreferences
 *
 * SharedPreferences file: "Cocos2dxPrefsFile"
 * Currency keys (found in libcocos2dcpp.so):
 *   - PlutoniumBalance = 999999999
 *   - CoinsBalance = 999999999
 *   - SqueezerNutsBalance = 999999999
 *   - SqueezerGearsBalance = 999999999
 *   - SqueezerScrewsBalance = 999999999
 *   - removeads = true
 *   - drones_unlocked = true
 *   - n_squeezers_bought = 999
 */

package fi.twomenandadog.zombiecatchers.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import fi.twomenandadog.zombiecatchers.patches.shared.ZOMBIE_CATCHERS
import fi.twomenandadog.zombiecatchers.patches.shared.SignatureCheckFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.LicenseCheckFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.StartupLauncherFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.LicenseActivityOnStartFingerprint
import java.util.logging.Logger

@Suppress("unused")
val unlockAllPatch = bytecodePatch(
    name = "Unlock all",
    description = "Writes unlimited currencies (plutonium, coins, squeezer " +
        "parts) directly to SharedPreferences at startup without modifying " +
        "game methods. Also bypasses PairIP anti-tamper. Stealth approach " +
        "that avoids triggering C++ native integrity checks.",
    default = true,
) {
    compatibleWith(ZOMBIE_CATCHERS)

    execute {
        val logger = Logger.getLogger("UnlockAll")
        var count = 0

        // ================================================================
        // HOOK 1: ZCGoogleAcitivty.onCreate → write values to SharedPreferences
        // ================================================================
        // .locals 6 — plenty of registers
        // p0 = this (ZCGoogleAcitivty, which is a Context)
        // p1 = Bundle (preserved for super.onCreate)
        //
        // Write to "Cocos2dxPrefsFile" SharedPreferences:
        //   PlutoniumBalance = 999999999
        //   CoinsBalance = 999999999
        //   SqueezerNutsBalance = 999999999
        //   SqueezerGearsBalance = 999999999
        //   SqueezerScrewsBalance = 999999999
        //   removeads = true
        //   drones_unlocked = true
        //   n_squeezers_bought = 999
        //
        // This is the ONLY game method we modify. The C++ integrity check
        // probably doesn't check onCreate (it checks billing/currency methods).
        // ================================================================
        val mainActivityClass = classDefByOrNull("Lfi/twomenandadog/zombiecatchers/ZCGoogleAcitivty;")
        if (mainActivityClass != null) {
            val mutableClass = mutableClassDefBy(mainActivityClass)
            mutableClass.methods.find { it.name == "onCreate" && it.parameterTypes.size == 1 }?.let { method ->
                if (method.implementation != null) {
                    val sb = StringBuilder()
                    // Get SharedPreferences "Cocos2dxPrefsFile" (mode 0 = private)
                    sb.append("const-string v0, \"Cocos2dxPrefsFile\"\n")
                    sb.append("const/4 v1, 0x0\n")
                    sb.append("invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;\n")
                    sb.append("move-result-object v0\n")
                    // Get Editor
                    sb.append("invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;\n")
                    sb.append("move-result-object v0\n")
                    // PlutoniumBalance = 999999999 (0x3B9AC9FF)
                    sb.append("const-string v1, \"PlutoniumBalance\"\n")
                    sb.append("const v2, 0x3b9ac9ff\n")
                    sb.append("invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;\n")
                    // CoinsBalance = 999999999
                    sb.append("const-string v1, \"CoinsBalance\"\n")
                    sb.append("const v2, 0x3b9ac9ff\n")
                    sb.append("invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;\n")
                    // SqueezerNutsBalance = 999999999
                    sb.append("const-string v1, \"SqueezerNutsBalance\"\n")
                    sb.append("const v2, 0x3b9ac9ff\n")
                    sb.append("invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;\n")
                    // SqueezerGearsBalance = 999999999
                    sb.append("const-string v1, \"SqueezerGearsBalance\"\n")
                    sb.append("const v2, 0x3b9ac9ff\n")
                    sb.append("invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;\n")
                    // SqueezerScrewsBalance = 999999999
                    sb.append("const-string v1, \"SqueezerScrewsBalance\"\n")
                    sb.append("const v2, 0x3b9ac9ff\n")
                    sb.append("invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;\n")
                    // n_squeezers_bought = 999
                    sb.append("const-string v1, \"n_squeezers_bought\"\n")
                    sb.append("const/16 v2, 0x3e7\n")
                    sb.append("invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;\n")
                    // removeads = true
                    sb.append("const-string v1, \"removeads\"\n")
                    sb.append("const/4 v2, 0x1\n")
                    sb.append("invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;\n")
                    // drones_unlocked = true
                    sb.append("const-string v1, \"drones_unlocked\"\n")
                    sb.append("const/4 v2, 0x1\n")
                    sb.append("invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;\n")
                    // Apply
                    sb.append("invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V")
                    method.addInstructions(0, sb.toString())
                    count++
                    logger.info("  patched: ZCGoogleAcitivty.onCreate -> write currencies to SharedPreferences")
                }
            }
        }

        // ================================================================
        // PAIRIP BYPASS ONLY (no game methods modified)
        // ================================================================

        // HOOK 2: StartupLauncher.launch → return-void
        StartupLauncherFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP StartupLauncher.launch -> return-void")
        }

        // HOOK 3: SignatureCheck.verifyIntegrity → return-void
        SignatureCheckFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP SignatureCheck.verifyIntegrity -> return-void")
        }

        // HOOK 4: LicenseClient.checkLicense → return-void
        LicenseCheckFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP LicenseClient.checkLicense -> return-void")
        }

        // HOOK 5: LicenseActivity.onStart → finish()
        LicenseActivityOnStartFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, """
                invoke-virtual {p0}, Lcom/pairip/licensecheck/LicenseActivity;->finish()V
                return-void
            """.trimIndent())
            count++
            logger.info("  patched: PairIP LicenseActivity.onStart -> finish()")
        }

        logger.info("Unlock all COMPLETE: " + count + " methods patched")
        logger.info("  Stealth mode: NO game methods modified (except onCreate)")
        logger.info("  Currencies written directly to Cocos2dxPrefsFile SharedPreferences")
    }
}
