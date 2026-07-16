/*
 * Unlock All + Unlimited Everything + PairIP Bypass for Zombie Catchers.
 *
 * HOOKS:
 *   1. StartupLauncher.launch → return-void (skip VM bytecode)
 *   2. SignatureCheck.verifyIntegrity → return-void
 *   3. LicenseClient.checkLicense → return-void
 *   4. LicenseActivity.onStart → finish() (close immediately)
 *   5. ZCActivity.openPlayStoreZCPage → return-void (NEW: prevent redirect)
 *   6. getIntegerForKey → 999999999 for Balance keys
 *   7. getBoolForKey → true for unlock keys
 *   8. Security.verifyPurchase → return true
 */

package fi.twomenandadog.zombiecatchers.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import fi.twomenandadog.zombiecatchers.patches.shared.ZOMBIE_CATCHERS
import fi.twomenandadog.zombiecatchers.patches.shared.GetIntegerForKeyFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.GetBoolForKeyFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.VerifyPurchaseFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.SignatureCheckFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.LicenseCheckFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.StartupLauncherFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.LicenseActivityOnStartFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.OpenPlayStoreFingerprint
import java.util.logging.Logger

@Suppress("unused")
val unlockAllPatch = bytecodePatch(
    name = "Unlock all",
    description = "Unlocks everything, sets all currencies to 999999999, " +
        "and bypasses PairIP anti-tamper protection (VM bytecode, " +
        "signature verification, license check, LicenseActivity, and " +
        "Play Store redirect) that redirects to Play Store.",
    default = true,
) {
    compatibleWith(ZOMBIE_CATCHERS)

    execute {
        val logger = Logger.getLogger("UnlockAll")
        var count = 0

        // === PAIRIP BYPASS (5 hooks) ===

        // HOOK 1: StartupLauncher.launch → return-void
        StartupLauncherFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP StartupLauncher.launch -> return-void")
        }

        // HOOK 2: SignatureCheck.verifyIntegrity → return-void
        SignatureCheckFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP SignatureCheck.verifyIntegrity -> return-void")
        }

        // HOOK 3: LicenseClient.checkLicense → return-void
        LicenseCheckFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP LicenseClient.checkLicense -> return-void")
        }

        // HOOK 4: LicenseActivity.onStart → finish() + return-void
        LicenseActivityOnStartFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, """
                invoke-virtual {p0}, Lcom/pairip/licensecheck/LicenseActivity;->finish()V
                return-void
            """.trimIndent())
            count++
            logger.info("  patched: PairIP LicenseActivity.onStart -> finish()")
        }

        // HOOK 5: ZCActivity.openPlayStoreZCPage → return-void
        // This is the KEY hook — C++ calls this via JNI to redirect to Play Store
        OpenPlayStoreFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: ZCActivity.openPlayStoreZCPage -> return-void (prevent redirect)")
        }

        // === UNLOCK ALL + UNLIMITED (3 hooks) ===

        // HOOK 6: getIntegerForKey → 999999999 for "Balance" keys
        GetIntegerForKeyFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, """
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
            logger.info("  patched: Cocos2dxHelper.getIntegerForKey -> 999999999 for Balance keys")
        }

        // HOOK 7: getBoolForKey → true for unlock/purchase/ads/bought keys
        GetBoolForKeyFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, """
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
            logger.info("  patched: Cocos2dxHelper.getBoolForKey -> true for unlock keys")
        }

        // HOOK 8: Security.verifyPurchase → return true
        VerifyPurchaseFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
            count++
            logger.info("  patched: Security.verifyPurchase -> return true")
        }

        logger.info("Unlock all COMPLETE: " + count + " methods patched")
    }
}
