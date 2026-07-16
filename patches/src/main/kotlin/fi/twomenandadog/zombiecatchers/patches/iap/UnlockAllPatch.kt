/*
 * Unlock All + Unlimited Everything + Anti-Tamper Bypass for Zombie Catchers.
 *
 * ROOT CAUSE of "Get this app from Play":
 *   When APK is re-signed by Morphe, Google Play Billing fails to connect
 *   (responseCode != 0). onBillingSetupFinished returns WITHOUT calling
 *   connectionResult(). C++ has a timeout — if connectionResult never
 *   arrives, C++ assumes the app is pirated and shows "Get this app from
 *   Play" screen.
 *
 * FIX: Patch onBillingSetupFinished to ALWAYS call
 *   connectionResult(true, "", callback) regardless of responseCode.
 *   C++ thinks billing connected successfully → no "Get this app from Play".
 *
 * HOOKS:
 *   1. onBillingSetupFinished → always call connectionResult(true, ...)
 *   2. StartupLauncher.launch → return-void (skip VM bytecode)
 *   3. SignatureCheck.verifyIntegrity → return-void
 *   4. LicenseClient.checkLicense → return-void
 *   5. LicenseActivity.onStart → finish()
 *   6. openPlayStoreZCPage → return-void
 *   7. getIntegerForKey → 999999999 for Balance keys
 *   8. getBoolForKey → true for unlock keys
 *   9. Security.verifyPurchase → return true
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
import fi.twomenandadog.zombiecatchers.patches.shared.OnBillingSetupFinishedFingerprint
import java.util.logging.Logger

@Suppress("unused")
val unlockAllPatch = bytecodePatch(
    name = "Unlock all",
    description = "Unlocks everything, sets all currencies to 999999999, " +
        "bypasses PairIP anti-tamper, and fixes 'Get this app from Play' " +
        "by forcing billing connection to report success.",
    default = true,
) {
    compatibleWith(ZOMBIE_CATCHERS)

    execute {
        val logger = Logger.getLogger("UnlockAll")
        var count = 0

        // === FIX "GET THIS APP FROM PLAY" (KEY FIX) ===

        // HOOK 1: onBillingSetupFinished → ALWAYS call connectionResult(true, ...)
        // .locals 4 — v0,v1,v2,v3 available
        // p0 = this (InAppServiceImpl$1)
        // p1 = BillingResult
        //
        // this$0 = InAppServiceImpl (field on p0)
        // access$000(this$0) = ZCActivity
        // val$callback = long (field on p0)
        //
        // Smali:
        //   v0 = p0.this$0 (InAppServiceImpl)
        //   v0 = access$000(v0) (ZCActivity)
        //   v1 = 1 (true = success)
        //   v2 = "" (message)
        //   v3 = p0.val$callback (low 32 bits of long)
        //   -- need v4 for high 32 bits, but .locals 4 only gives v0-v3
        //   -- Actually, val$callback is iget-wide which uses 2 regs
        //   -- With .locals 4: v0,v1,v2,v3 + p0(=v4), p1(=v5)
        //   -- iget-wide v2, p0, ->val$callback:J uses v2 AND v3
        //   -- Then invoke-virtual {v0, v1, v2, v3} would need v0=ZCActivity, v1=true, v2/v3=callback
        //   -- But we also need a String for the message...
        //   -- Use const-string v1, "" for message, but then v1 can't be 1 (true)
        //   -- Let me think...
        //   -- invoke-virtual {v0, v1, v2, v3, v4} — 5 registers
        //   -- v0 = ZCActivity
        //   -- v1 = 1 (Z = true)
        //   -- v2 = "" (String message)
        //   -- v3,v4 = callback (J = long, 2 regs)
        //   -- But .locals 4 gives v0-v3, and we need v4...
        //   -- p0 = v4 (this), p1 = v5 (BillingResult)
        //   -- So v4 is p0, not available as local
        //   -- BUT: we can use p0 directly since we don't need 'this' after getting callback
        //
        // Actually, let me look at how the ORIGINAL code does it:
        //   v0 = access$000(this$0) = ZCActivity
        //   v1 = 1 (true)
        //   p1 = p1.getDebugMessage() (reuses p1 register)
        //   v2,v3 = val$callback (iget-wide)
        //   invoke-virtual {v0, v1, p1, v2, v3}, connectionResult(ZLjava/lang/String;J)V
        //
        // So the registers are: v0=ZCActivity, v1=true, p1=message, v2/v3=callback
        // invoke-virtual {v0, v1, p1, v2, v3} — that's 5 registers: v0,v1,p1,v2,v3
        // With .locals 4: v0,v1,v2,v3 + p0(=v4), p1(=v5)
        // So the original uses p1(=v5) as the message register.
        //
        // We can do the same but force v1=1 and p1="" regardless of responseCode.
        OnBillingSetupFinishedFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, """
                # Get ZCActivity via access$000(this$0)
                iget-object v0, p0, Lfi/twomenandadog/zombiecatchers/InAppServiceImpl$1;->this$0:Lfi/twomenandadog/zombiecatchers/InAppServiceImpl;
                invoke-static {v0}, Lfi/twomenandadog/zombiecatchers/InAppServiceImpl;->access$000(Lfi/twomenandadog/zombiecatchers/InAppServiceImpl;)Lfi/twomenandadog/zombiecatchers/ZCActivity;
                move-result-object v0
                # v0 = ZCActivity
                # v1 = 1 (true = success)
                const/4 v1, 0x1
                # p1 = "" (empty message — reuses p1 register)
                const-string p1, ""
                # v2,v3 = val$callback (long)
                iget-wide v2, p0, Lfi/twomenandadog/zombiecatchers/InAppServiceImpl$1;->val$callback:J
                # Call connectionResult(true, "", callback)
                invoke-virtual {v0, v1, p1, v2, v3}, Lfi/twomenandadog/zombiecatchers/ZCActivity;->connectionResult(ZLjava/lang/String;J)V
                return-void
            """.trimIndent())
            count++
            logger.info("  patched: onBillingSetupFinished -> always call connectionResult(true,...)")
        }

        // === PAIRIP BYPASS ===

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

        // HOOK 6: openPlayStoreZCPage → return-void
        OpenPlayStoreFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: ZCActivity.openPlayStoreZCPage -> return-void")
        }

        // === UNLOCK ALL + UNLIMITED ===

        // HOOK 7: getIntegerForKey → 999999999 for "Balance" keys
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

        // HOOK 8: getBoolForKey → true for unlock/purchase/ads/bought keys
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

        // HOOK 9: Security.verifyPurchase → return true
        VerifyPurchaseFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
            count++
            logger.info("  patched: Security.verifyPurchase -> return true")
        }

        logger.info("Unlock all COMPLETE: " + count + " methods patched")
    }
}
