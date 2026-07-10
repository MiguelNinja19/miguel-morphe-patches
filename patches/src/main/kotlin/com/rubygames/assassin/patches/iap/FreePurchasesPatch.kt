/*
 * Unlimited Resources patch for Hunter Assassin.
 *
 * Based on the Lucky Patcher custom patch by MD ALI HOSSAIN.
 * Directly writes to the game's SharedPreferences (Cocos2dxPrefsFile)
 * at app startup, setting:
 *   - gems = 9999999
 *   - keys = 9999999
 *   - vipPurchased = true (removes ads)
 *   - assassinOwned2-35 = true (all characters unlocked)
 *
 * The game reads these values from SharedPreferences via
 * Cocos2dxHelper.getIntegerForKey() and getBoolForKey().
 *
 * This approach bypasses the billing system entirely and gives
 * unlimited gems, keys, VIP status, and all characters unlocked.
 */

package com.rubygames.assassin.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.rubygames.assassin.patches.shared.Constants.HUNTER_ASSASSIN
import com.rubygames.assassin.patches.shared.OnCreateFingerprint

@Suppress("unused")
val freePurchasesPatch = bytecodePatch(
    name = "Unlimited gems, keys & unlock all",
    description = "Sets gems to 9999999, keys to 9999999, unlocks VIP " +
        "(removes ads) and all assassin characters (2-35) by writing " +
        "directly to the game's SharedPreferences on startup. Based on " +
        "the Lucky Patcher custom patch approach.",
    default = true,
) {
    compatibleWith(HUNTER_ASSASSIN)

    execute {
        val method = OnCreateFingerprint.method
        method.addInstructions(
            0,
            """
                # Set gems = 9999999
                const-string v0, "gems"
                const v1, 0x98967f
                invoke-static {v0, v1}, Lorg/cocos2dx/lib/Cocos2dxHelper;->setIntegerForKey(Ljava/lang/String;I)V
                
                # Set keys = 9999999
                const-string v0, "keys"
                const v1, 0x98967f
                invoke-static {v0, v1}, Lorg/cocos2dx/lib/Cocos2dxHelper;->setIntegerForKey(Ljava/lang/String;I)V
                
                # Set vipPurchased = true
                const-string v0, "vipPurchased"
                const/4 v1, 0x1
                invoke-static {v0, v1}, Lorg/cocos2dx/lib/Cocos2dxHelper;->setBoolForKey(Ljava/lang/String;Z)V
                
                # Unlock all assassins (2-35)
                const/4 v2, 0x2
                
                :loop_start
                const/16 v3, 0x23
                
                if-le v2, v3, :loop_end
                
                # Build key string "assassinOwned" + number
                const-string v0, "assassinOwned"
                invoke-static {v2}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;
                move-result-object v4
                new-instance v5, Ljava/lang/StringBuilder;
                invoke-direct {v5}, Ljava/lang/StringBuilder;-><init>()V
                invoke-virtual {v5, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v5, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                invoke-virtual {v5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                move-result-object v0
                
                # Set assassinOwnedN = true
                const/4 v1, 0x1
                invoke-static {v0, v1}, Lorg/cocos2dx/lib/Cocos2dxHelper;->setBoolForKey(Ljava/lang/String;Z)V
                
                add-int/lit8 v2, v2, 0x1
                goto :loop_start
                
                :loop_end
            """.trimIndent()
        )
    }
}
