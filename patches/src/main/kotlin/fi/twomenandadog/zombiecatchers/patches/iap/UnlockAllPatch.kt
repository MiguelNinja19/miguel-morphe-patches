/*
 * Unlock All + Unlimited Everything + Anti-Tamper Bypass for Zombie Catchers.
 *
 * FIX "Get this app from Play":
 *   When APK is re-signed by Morphe, Google Play Billing fails to connect
 *   (responseCode != 0). onBillingSetupFinished returns WITHOUT calling
 *   connectionResult(). C++ has a timeout — if connectionResult never
 *   arrives, C++ assumes the app is pirated and shows "Get this app from
 *   Play" screen.
 *
 *   Fix: Patch onBillingSetupFinished to ALWAYS call
 *   connectionResult(true, "", callback) regardless of responseCode.
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

        // ================================================================
        // HOOK 1: onBillingSetupFinished → ALWAYS call connectionResult(true, ...)
        // ================================================================
        // When billing fails (re-signed APK), responseCode != 0 and the
        // method returns WITHOUT calling connectionResult. C++ timeout
        // triggers "Get this app from Play" screen.
        // Fix: Always call connectionResult(true, "", callback).
        //
        // .locals 4 — v0,v1,v2,v3 available, p0=this, p1=BillingResult
        //
        // NOTE: $ in smali class/field names must be escaped as ${'$'} in
        // Kotlin strings to avoid interpolation.
        // ================================================================
        OnBillingSetupFinishedFingerprint.matchOrNull()?.let {
            val innerClass = "Lfi/twomenandadog/zombiecatchers/InAppServiceImpl" + "${'$'}" + "1;"
            val implClass = "Lfi/twomenandadog/zombiecatchers/InAppServiceImpl;"
            val zcClass = "Lfi/twomenandadog/zombiecatchers/ZCActivity;"
            val sb = StringBuilder()
            // v0 = this.this$0 (InAppServiceImpl)
            sb.append("iget-object v0, p0, ")
            sb.append(innerClass)
            sb.append("->this")
            sb.append("${'$'}")
            sb.append("0:Lfi/twomenandadog/zombiecatchers/InAppServiceImpl;\n")
            // v0 = access$000(v0) = ZCActivity
            sb.append("invoke-static {v0}, ")
            sb.append(implClass)
            sb.append("->access")
            sb.append("${'$'}")
            sb.append("000(Lfi/twomenandadog/zombiecatchers/InAppServiceImpl;)")
            sb.append(zcClass)
            sb.append("\n")
            sb.append("move-result-object v0\n")
            // v1 = 1 (true = success)
            sb.append("const/4 v1, 0x1\n")
            // p1 = "" (empty message)
            sb.append("const-string p1, \"\"\n")
            // v2,v3 = this.val$callback (long)
            sb.append("iget-wide v2, p0, ")
            sb.append(innerClass)
            sb.append("->val")
            sb.append("${'$'}")
            sb.append("callback:J\n")
            // connectionResult(true, "", callback)
            sb.append("invoke-virtual {v0, v1, p1, v2, v3}, ")
            sb.append(zcClass)
            sb.append("->connectionResult(ZLjava/lang/String;J)V\n")
            sb.append("return-void")
            it.method.addInstructions(0, sb.toString())
            count++
            logger.info("  patched: onBillingSetupFinished -> always call connectionResult(true,...)")
        }

        // ================================================================
        // HOOK 2: StartupLauncher.launch → return-void
        // ================================================================
        StartupLauncherFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP StartupLauncher.launch -> return-void")
        }

        // ================================================================
        // HOOK 3: SignatureCheck.verifyIntegrity → return-void
        // ================================================================
        SignatureCheckFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP SignatureCheck.verifyIntegrity -> return-void")
        }

        // ================================================================
        // HOOK 4: LicenseClient.checkLicense → return-void
        // ================================================================
        LicenseCheckFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: PairIP LicenseClient.checkLicense -> return-void")
        }

        // ================================================================
        // HOOK 5: LicenseActivity.onStart → finish()
        // ================================================================
        LicenseActivityOnStartFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, """
                invoke-virtual {p0}, Lcom/pairip/licensecheck/LicenseActivity;->finish()V
                return-void
            """.trimIndent())
            count++
            logger.info("  patched: PairIP LicenseActivity.onStart -> finish()")
        }

        // ================================================================
        // HOOK 6: openPlayStoreZCPage → return-void
        // ================================================================
        OpenPlayStoreFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "return-void")
            count++
            logger.info("  patched: ZCActivity.openPlayStoreZCPage -> return-void")
        }

        // ================================================================
        // HOOK 7: getIntegerForKey → 999999999 for "Balance" keys
        // ================================================================
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

        // ================================================================
        // HOOK 8: getBoolForKey → true for unlock/purchase/ads/bought keys
        // ================================================================
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

        // ================================================================
        // HOOK 9: Security.verifyPurchase → return true
        // ================================================================
        VerifyPurchaseFingerprint.matchOrNull()?.let {
            it.method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
            count++
            logger.info("  patched: Security.verifyPurchase -> return true")
        }

        logger.info("Unlock all COMPLETE: " + count + " methods patched")
    }
}
