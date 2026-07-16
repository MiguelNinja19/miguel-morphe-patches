/*
 * Unlock All + Unlimited Everything for Zombie Catchers.
 *
 * HOW IT WORKS:
 *
 * Zombie Catchers is a Cocos2d-x game. The C++ code stores game state
 * (plutonium, coins, unlocks) using cocos2d::UserDefault, which on
 * Android maps to SharedPreferences in file "Cocos2dxPrefsFile".
 *
 * The Java bridge is Cocos2dxHelper.getIntegerForKey(key, default) and
 * Cocos2dxHelper.getBoolForKey(key, default), which read from those
 * SharedPreferences.
 *
 * Currency keys found in libcocos2dcpp.so:
 *   - PlutoniumBalance (premium currency)
 *   - CoinsBalance (regular currency)
 *   - SqueezerNutsBalance
 *   - SqueezerGearsBalance
 *   - SqueezerScrewsBalance
 *
 * This patch:
 *   HOOK 1: getIntegerForKey → return 999999999 when key ends with "Balance"
 *           This makes ALL currencies (plutonium, coins, squeezer parts)
 *           appear as 999999999 — effectively unlimited.
 *
 *   HOOK 2: getBoolForKey → return true when key contains "unlock" or
 *           "purchased" or "noads". This unlocks all items.
 *
 *   HOOK 3: Security.verifyPurchase → return true (for IAP bypass)
 */

package fi.twomenandadog.zombiecatchers.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import fi.twomenandadog.zombiecatchers.patches.shared.ZOMBIE_CATCHERS
import fi.twomenandadog.zombiecatchers.patches.shared.GetIntegerForKeyFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.GetBoolForKeyFingerprint
import fi.twomenandadog.zombiecatchers.patches.shared.VerifyPurchaseFingerprint
import java.util.logging.Logger

@Suppress("unused")
val unlockAllPatch = bytecodePatch(
    name = "Unlock all",
    description = "Unlocks everything and sets all currencies (plutonium, " +
        "coins, squeezer parts) to 999999999. Patches Cocos2dxHelper " +
        "to return max values for Balance keys and true for unlock keys.",
    default = true,
) {
    compatibleWith(ZOMBIE_CATCHERS)

    execute {
        val logger = Logger.getLogger("UnlockAll")
        var count = 0

        // HOOK 1: getIntegerForKey → return 999999999 for "Balance" keys
        // .locals 3, p0 = String key, p1 = int default
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

        // HOOK 2: getBoolForKey → return true for unlock/purchase/ads keys
        // .locals 3, p0 = String key, p1 = boolean default
        // Keys found in libcocos2dcpp.so: drones_unlocked, removeads, n_squeezers_bought
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

        logger.info("Unlock all COMPLETE: " + count + " methods patched")
        logger.info("  Currencies: PlutoniumBalance, CoinsBalance, SqueezerNuts/Gears/ScrewsBalance -> 999999999")
        logger.info("  Unlocks: any key containing unlock/purchas/ads/bought -> true")
    }
}
