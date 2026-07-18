/*
 * Unlock Full Game patch for Oddmar.
 *
 * Oddmar is a Unity IL2CPP game (no PairIP, no anti-tamper).
 * The game uses com.mobge.oddmarbilling for IAP.
 *
 * Unlock mechanism:
 *   C# calls HasProductBeenPurchasedCall with productId "unlock_all_levels"
 *   Java queries Google Play Billing
 *   OnQuerySuccess(boolean purchased) is called
 *   If purchased == true → game unlocks all levels
 *   If purchased == false → game stays locked (trial)
 *
 * Patch: Hook OnQuerySuccess to always call the callback with true,
 * making the game think "unlock_all_levels" was purchased.
 *
 * No server loading detected. No PairIP. No anti-tamper.
 */

package com.mobge.oddmar.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.mobge.oddmar.patches.shared.ODDMAR
import com.mobge.oddmar.patches.shared.HasProductBeenPurchasedCallbackFingerprint
import java.util.logging.Logger

@Suppress("unused")
val unlockFullGamePatch = bytecodePatch(
    name = "Unlock full game",
    description = "Unlocks all levels by making the game think the " +
        "'unlock_all_levels' IAP product was purchased. Patches " +
        "HasProductBeenPurchasedCall to always report success.",
    default = true,
) {
    compatibleWith(ODDMAR)

    execute {
        val logger = Logger.getLogger("UnlockFullGame")

        // HasProductBeenPurchasedCall$1.OnQuerySuccess(Z)V
        // .locals 5 — p0=this, p1=boolean purchased
        //
        // The method calls access$200(this$0, p1) which calls the callback
        // with the purchased boolean. We override p1 to 1 (true) before
        // the callback is called.
        //
        // Smali:
        //   const/4 p1, 0x1   # force purchased = true
        //   # original code continues and calls access$200(this, true)
        HasProductBeenPurchasedCallbackFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "const/4 p1, 0x1")
            logger.info("Unlock full game COMPLETE: HasProductBeenPurchased -> always true")
        }

        if (HasProductBeenPurchasedCallbackFingerprint.matchOrNull() == null) {
            logger.info("Unlock full game FAILED: fingerprint not found")
        }
    }
}
