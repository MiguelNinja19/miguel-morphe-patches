/*
 * Unlock All + Unlimited Everything for Zombie Catchers.
 *
 * Also bypasses PairIP anti-tamper protection (signature verification
 * and license check) that redirects to Play Store when the APK is
 * re-signed by Morphe.
 *
 * HOOKS:
 *   1. Cocos2dxHelper.getIntegerForKey → 999999999 for "Balance" keys
 *   2. Cocos2dxHelper.getBoolForKey → true for unlock/purchase/ads keys
 *   3. Security.verifyPurchase → return true
 *   4. PairIP SignatureCheck.verifyIntegrity → return-void (bypass)
 *   5. PairIP LicenseClient.checkLicense → return-void (bypass)
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
import java.util.logging.Logger

@Suppress("unused")
val unlockAllPatch = bytecodePatch(
    name = "Unlock all",
    description = "Unlocks everything, sets all currencies (plutonium, " +
        "coins, squeezer parts) to 999999999, and bypasses PairIP " +
        "anti-tamper protection (signature verification + license check) " +
        "that redirects to Play Store.",
    default = true,
) {
    compatibleWith(ZOMBIE_CATCHERS)

    execute {
        val logger = Logger.getLogger("UnlockAll")
        var count = 0

        // HOOK 1: getIntegerForKey → 999999999 for "Balance" keys
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

        // HOOK 2: getBoolForKey → true for unlock/purchase/ads/bought keys
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
            logger.info("  patched: Cocos2dxHelper.getBoolForKey -> true for unlock/purchase/ads/bought keys")
        }

        // HOOK 3: Security.verifyPurchase → return true
        VerifyPurchaseFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
            count++
            logger.info("  patched: Security.verifyPurchase -> return true")
        }

        // HOOK 4: PairIP SignatureCheck.verifyIntegrity → return-void
        // Bypasses APK signature verification that detects re-signed APKs
        // and redirects to Play Store.
        SignatureCheckFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP SignatureCheck.verifyIntegrity -> return-void (bypass)")
        }

        // HOOK 5: PairIP LicenseClient.checkLicense → return-void
        // Bypasses Google Play license check that verifies the app was
        // installed from Play Store.
        LicenseCheckFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP LicenseClient.checkLicense -> return-void (bypass)")
        }

        logger.info("Unlock all COMPLETE: " + count + " methods patched")
    }
}
