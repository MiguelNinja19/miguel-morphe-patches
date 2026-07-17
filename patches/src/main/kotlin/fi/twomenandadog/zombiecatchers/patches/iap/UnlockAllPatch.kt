// Unlock All + Unlimited Everything + PairIP Bypass for Zombie Catchers.
// Combines Nai64's 10 PairIP bypass strategies with stealth currency writing.

package fi.twomenandadog.zombiecatchers.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import fi.twomenandadog.zombiecatchers.patches.shared.ZOMBIE_CATCHERS
import fi.twomenandadog.zombiecatchers.patches.shared.GetIntegerForKeyFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.GetBoolForKeyFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.VerifyPurchaseFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.PerformLocalInstallerCheckFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.PairipSignatureCheckVerifyIntegrityFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.PairipSignatureCheckVerifySignatureMatchesFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.PairipLicenseClientStartErrorDialogFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.PairipLicenseClientStartPaywallFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.PairipLicenseActivityShowPaywallFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.PairipApplicationAttachBaseContextFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.PairipApplicationOnCreateFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.StartupLauncherFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.LicenseActivityOnStartFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.GenericBooleanInstallerCheckFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.GenericStringInstallerCheckFingerprint
import java.util.logging.Logger

@Suppress("unused")
val unlockAllPatch = bytecodePatch(
    name = "Unlock all",
    description = "Unlocks everything, sets all currencies to 999999999, " +
        "and bypasses PairIP anti-tamper using 10 strategies from Nai64.",
    default = true,
) {
    compatibleWith(ZOMBIE_CATCHERS)

    execute {
        val logger = Logger.getLogger("UnlockAll")
        var count = 0

        // ================================================================
        // PAIRIP BYPASS (10 strategies from Nai64)
        // ================================================================

        // 1. performLocalInstallerCheck -> return true
        PerformLocalInstallerCheckFingerprint.methodOrNull?.let {
            it.addInstructions(0, listOf(
                BuilderInstruction11n(Opcode.CONST_4, 0, 1),
                BuilderInstruction11x(Opcode.RETURN, 0),
            ))
            count++
            logger.info("  patched: performLocalInstallerCheck -> return true")
        }

        // 2. SignatureCheck.verifyIntegrity -> return-void
        PairipSignatureCheckVerifyIntegrityFingerprint.methodOrNull?.let {
            it.addInstructions(0, "return-void")
            count++
            logger.info("  patched: SignatureCheck.verifyIntegrity -> return-void")
        }

        // 3. SignatureCheck.verifySignatureMatches -> return true
        PairipSignatureCheckVerifySignatureMatchesFingerprint.methodOrNull?.let {
            it.addInstructions(0, listOf(
                BuilderInstruction11n(Opcode.CONST_4, 0, 1),
                BuilderInstruction11x(Opcode.RETURN, 0),
            ))
            count++
            logger.info("  patched: SignatureCheck.verifySignatureMatches -> return true")
        }

        // 4. LicenseClient.startErrorDialogActivity -> return-void
        PairipLicenseClientStartErrorDialogFingerprint.methodOrNull?.let {
            it.addInstructions(0, "return-void")
            count++
            logger.info("  patched: LicenseClient.startErrorDialogActivity -> return-void")
        }

        // 5. LicenseClient.startPaywallActivity -> return-void
        PairipLicenseClientStartPaywallFingerprint.methodOrNull?.let {
            it.addInstructions(0, "return-void")
            count++
            logger.info("  patched: LicenseClient.startPaywallActivity -> return-void")
        }

        // 6. LicenseActivity.showPaywallAndCloseApp -> return-void
        PairipLicenseActivityShowPaywallFingerprint.methodOrNull?.let {
            it.addInstructions(0, "return-void")
            count++
            logger.info("  patched: LicenseActivity.showPaywallAndCloseApp -> return-void")
        }

        // 7a. Application.attachBaseContext -> invoke-super + return-void
        // KEY FIX: Must call invoke-super BEFORE return-void, otherwise
        // the real Application never initializes and the app crashes!
        PairipApplicationAttachBaseContextFingerprint.methodOrNull?.let {
            it.addInstructions(0, """
                invoke-super {p0, p1}, Lcom/pairip/application/Application;->attachBaseContext(Landroid/content/Context;)V
                return-void
            """.trimIndent())
            count++
            logger.info("  patched: Application.attachBaseContext -> invoke-super + return-void")
        }

        // 7b. Application.onCreate -> invoke-super + return-void
        PairipApplicationOnCreateFingerprint.methodOrNull?.let {
            it.addInstructions(0, """
                invoke-super {p0}, Lcom/pairip/application/Application;->onCreate()V
                return-void
            """.trimIndent())
            count++
            logger.info("  patched: Application.onCreate -> invoke-super + return-void")
        }

        // 8. StartupLauncher.launch -> return-void
        StartupLauncherFingerprint.methodOrNull?.let {
            it.addInstructions(0, "return-void")
            count++
            logger.info("  patched: StartupLauncher.launch -> return-void")
        }

        // 9. LicenseActivity.onStart -> finish()
        LicenseActivityOnStartFingerprint.methodOrNull?.let {
            it.addInstructions(0, """
                invoke-virtual {p0}, Lcom/pairip/licensecheck/LicenseActivity;->finish()V
                return-void
            """.trimIndent())
            count++
            logger.info("  patched: LicenseActivity.onStart -> finish()")
        }

        // 10a. Generic boolean installer check -> return true
        GenericBooleanInstallerCheckFingerprint.methodOrNull?.let {
            it.addInstructions(0, listOf(
                BuilderInstruction11n(Opcode.CONST_4, 0, 1),
                BuilderInstruction11x(Opcode.RETURN, 0),
            ))
            count++
            logger.info("  patched: generic boolean installer check -> return true")
        }

        // 10b. Generic String installer check -> return "com.android.vending"
        GenericStringInstallerCheckFingerprint.methodOrNull?.let {
            it.addInstructions(0, """
                const-string v0, "com.android.vending"
                return-object v0
            """.trimIndent())
            count++
            logger.info("  patched: generic String installer check -> return com.android.vending")
        }

        // ================================================================
        // UNLOCK ALL + UNLIMITED CURRENCIES
        // ================================================================

        // 11. getIntegerForKey -> return 999999999 for "Balance" keys
        GetIntegerForKeyFingerprint.methodOrNull?.let {
            it.addInstructions(0, """
                const-string v0, "Balance"
                invoke-virtual {p0, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v0
                if-eqz v0, :original_int
                const v0, 0x3b9ac9ff
                return v0
                :original_int
                nop
            """.trimIndent())
            count++
            logger.info("  patched: getIntegerForKey -> 999999999 for Balance keys")
        }

        // 12. getBoolForKey -> return true for unlock/purchase/ads/bought keys
        GetBoolForKeyFingerprint.methodOrNull?.let {
            it.addInstructions(0, """
                const-string v0, "nlock"
                invoke-virtual {p0, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v0
                if-nez v0, :return_true
                const-string v0, "urchas"
                invoke-virtual {p0, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v0
                if-nez v0, :return_true
                const-string v0, "ads"
                invoke-virtual {p0, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v0
                if-nez v0, :return_true
                const-string v0, "bought"
                invoke-virtual {p0, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
                move-result v0
                if-eqz v0, :return_true
                goto :original_bool
                :return_true
                const/4 v0, 0x1
                return v0
                :original_bool
                nop
            """.trimIndent())
            count++
            logger.info("  patched: getBoolForKey -> true for unlock keys")
        }

        // 13. Security.verifyPurchase -> return true
        VerifyPurchaseFingerprint.methodOrNull?.let {
            it.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
            count++
            logger.info("  patched: Security.verifyPurchase -> return true")
        }

        logger.info("Unlock all COMPLETE: " + count + " methods patched")
    }
}
