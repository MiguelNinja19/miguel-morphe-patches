// Unlock All + Unlimited Everything for Zombie Catchers.
//
// Stealth approach: write currencies to SharedPreferences at END of
// onCreate (after super.onCreate and C++ initialization), don't modify
// any game methods.

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
    description = "Writes unlimited currencies to SharedPreferences at " +
        "the END of onCreate (after C++ init) and bypasses PairIP.",
    default = true,
) {
    compatibleWith(ZOMBIE_CATCHERS)

    execute {
        val logger = Logger.getLogger("UnlockAll")
        var count = 0

        // HOOK 1: ZCGoogleAcitivty.onCreate -> write values to SharedPreferences
        // Inject at the END (before return-void) so super.onCreate() has
        // already been called and Context is fully initialized.
        val mainActivityClass = classDefByOrNull("Lfi/twomenandadog/zombiecatchers/ZCGoogleAcitivty;")
        if (mainActivityClass != null) {
            val mutableClass = mutableClassDefBy(mainActivityClass)
            mutableClass.methods.find { it.name == "onCreate" && it.parameterTypes.size == 1 }?.let { method ->
                if (method.implementation != null) {
                    val editorClass = "Landroid/content/SharedPreferences" + "${'$'}" + "Editor;"
                    // Find the last return-void and inject BEFORE it
                    val instructions = method.implementation!!.instructions
                    var returnIndex = -1
                    for (i in instructions.indices.reversed()) {
                        val insn = instructions[i]
                        if (insn.opcode == com.android.tools.smali.dexlib2.Opcode.RETURN_VOID) {
                            returnIndex = i
                            break
                        }
                    }
                    if (returnIndex >= 0) {
                        val sb = StringBuilder()
                        sb.append("const-string v0, \"Cocos2dxPrefsFile\"\n")
                        sb.append("const/4 v1, 0x0\n")
                        sb.append("invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;\n")
                        sb.append("move-result-object v0\n")
                        sb.append("invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences" + "${'$'}" + "Editor;\n")
                        sb.append("move-result-object v0\n")
                        sb.append("const-string v1, \"PlutoniumBalance\"\n")
                        sb.append("const v2, 0x3b9ac9ff\n")
                        sb.append("invoke-interface {v0, v1, v2}, " + editorClass + "->putInt(Ljava/lang/String;I)" + editorClass + "\n")
                        sb.append("const-string v1, \"CoinsBalance\"\n")
                        sb.append("const v2, 0x3b9ac9ff\n")
                        sb.append("invoke-interface {v0, v1, v2}, " + editorClass + "->putInt(Ljava/lang/String;I)" + editorClass + "\n")
                        sb.append("const-string v1, \"SqueezerNutsBalance\"\n")
                        sb.append("const v2, 0x3b9ac9ff\n")
                        sb.append("invoke-interface {v0, v1, v2}, " + editorClass + "->putInt(Ljava/lang/String;I)" + editorClass + "\n")
                        sb.append("const-string v1, \"SqueezerGearsBalance\"\n")
                        sb.append("const v2, 0x3b9ac9ff\n")
                        sb.append("invoke-interface {v0, v1, v2}, " + editorClass + "->putInt(Ljava/lang/String;I)" + editorClass + "\n")
                        sb.append("const-string v1, \"SqueezerScrewsBalance\"\n")
                        sb.append("const v2, 0x3b9ac9ff\n")
                        sb.append("invoke-interface {v0, v1, v2}, " + editorClass + "->putInt(Ljava/lang/String;I)" + editorClass + "\n")
                        sb.append("const-string v1, \"n_squeezers_bought\"\n")
                        sb.append("const/16 v2, 0x3e7\n")
                        sb.append("invoke-interface {v0, v1, v2}, " + editorClass + "->putInt(Ljava/lang/String;I)" + editorClass + "\n")
                        sb.append("const-string v1, \"removeads\"\n")
                        sb.append("const/4 v2, 0x1\n")
                        sb.append("invoke-interface {v0, v1, v2}, " + editorClass + "->putBoolean(Ljava/lang/String;Z)" + editorClass + "\n")
                        sb.append("const-string v1, \"drones_unlocked\"\n")
                        sb.append("const/4 v2, 0x1\n")
                        sb.append("invoke-interface {v0, v1, v2}, " + editorClass + "->putBoolean(Ljava/lang/String;Z)" + editorClass + "\n")
                        sb.append("invoke-interface {v0}, " + editorClass + "->apply()V")
                        method.addInstructions(returnIndex, sb.toString())
                        count++
                        logger.info("  patched: ZCGoogleAcitivty.onCreate -> write currencies (at END, before return)")
                    }
                }
            }
        }

        // HOOK 2: StartupLauncher.launch -> return-void
        StartupLauncherFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP StartupLauncher.launch -> return-void")
        }

        // HOOK 3: SignatureCheck.verifyIntegrity -> return-void
        SignatureCheckFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP SignatureCheck.verifyIntegrity -> return-void")
        }

        // HOOK 4: LicenseClient.checkLicense -> return-void
        LicenseCheckFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP LicenseClient.checkLicense -> return-void")
        }

        // HOOK 5: LicenseActivity.onStart -> finish()
        LicenseActivityOnStartFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, """
                invoke-virtual {p0}, Lcom/pairip/licensecheck/LicenseActivity;->finish()V
                return-void
            """.trimIndent())
            count++
            logger.info("  patched: PairIP LicenseActivity.onStart -> finish()")
        }

        logger.info("Unlock all COMPLETE: " + count + " methods patched")
    }
}
