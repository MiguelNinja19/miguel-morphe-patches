/*
 * Unlock full game + license bypass for Lara Croft and the Guardian of
 * Light (com.feralinteractive.laracroftgol_android).
 *
 * RESEARCH SUMMARY (v1.2.6RC1, XAPK analyzed from APKCombo):
 *
 * - NO PairIP. Confirmed by: (1) no PairipApplication/ licence activity
 *   in AndroidManifest.xml, (2) no libpairipcore.so in any split,
 *   (3) no pairip strings anywhere in the DEX.
 *
 * - The game is "try before you buy": levels 1-2 are free, the full
 *   game (levels 3-14 + DLC) unlocks via a single $9.99 in-app
 *   purchase ("Feral.FeralLib" TBYB system).
 *
 * - BUT it embeds the classic Google Play Licensing (LVL) library
 *   (com.google.android.vending.licensing.*) with the
 *   com.android.vending.CHECK_LICENSE permission. The startup
 *   runnable (framework/I.run()) builds a LicenseChecker with a
 *   ServerManagedPolicy-style Policy (framework/B0) and a
 *   LicenseCheckerCallback (R0/B). On failure the callback shows an
 *   error dialog and NEVER calls f0.k() (the "proceed to game"
 *   queue-drainer), so a re-signed APK gets stuck at the splash
 *   screen. That is the "complex protection" Feral games are known
 *   for - not PairIP.
 *
 * - The billing bridge is Java: q0 extends the (non-obfuscated)
 *   FeralGoogleBillingServicesInterface and feeds purchase data to
 *   the native game (libLaraCroftGoL.so) via
 *   FeralGoogleBillingServicesInterface.nativeProcessPurchaseItems
 *   (Z, FeralBillingClientAPI$Purchase[], Z). The native TBYB code
 *   decides "full game owned" by matching the SKUs reported by each
 *   purchase (Purchase.getSkus()).
 *
 * - The SKU strings "Demo.FullGame" and "Demo.FullGamePlusDLC" are
 *   present as plain strings inside libLaraCroftGoL.so (next to the
 *   TBYB/analytics keys), so those are the product ids we inject.
 *
 * PATCH STRATEGY (all bytecode, no Fingerprints.kt needed because the
 * interesting classes are obfuscated - we discover them dynamically,
 * same approach as the universal BillingBypassPatch):
 *
 * HOOK 1 - LVL Policy.allowAccess() -> true
 *   Every class implementing com.google.android.vending.licensing.Policy
 *   (framework/B0 + ServerManagedPolicy + StrictPolicy +
 *   APKExpansionPolicy) gets allowAccess() patched to return true.
 *   With the policy always allowing, the LicenseValidator calls
 *   callback.allow() instead of dontAllow() -> f0.k() runs -> the
 *   game boots.
 *
 * HOOK 2 - LicenseCheckerCallback failure paths -> allow()
 *   The class implementing LicenseCheckerCallback has
 *   dontAllow(I)V and applicationError(I)V rewritten to simply call
 *   this.allow(I). This covers the paths that bypass the policy
 *   (service connection errors, ERROR_NOT_MARKET_MANAGED from the
 *   re-signed APK, etc). allow() drains the startup queue exactly
 *   like a licensed boot.
 *
 * HOOK 3 - Inject fake purchases into nativeProcessPurchaseItems
 *   The static feeder method (q0.a(q0, g, List, Z)) is the single
 *   choke point that converts the billing library's purchase list
 *   into FeralBillingClientAPI$Purchase[] and hands it to the native
 *   game. We prepend instructions that, if the list is null, create
 *   an empty ArrayList, then construct two fake purchases:
 *
 *     productId      = "Demo.FullGame" / "Demo.FullGamePlusDLC"
 *     purchaseState  = 0 (json) -> getPurchaseState() == 1 PURCHASED
 *     acknowledged   = true  (flows straight into the result array
 *                            instead of the acknowledge sub-flow)
 *
 *   The native TBYB system then reports "PurchasedAllDLC"/full game
 *   owned on every startup, without contacting Google Play Billing.
 */

package com.feralinteractive.laracroftgol.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.feralinteractive.laracroftgol.patches.shared.LARA_CROFT_GOL
import java.util.logging.Logger

private const val BILLING_IFACE = "Lcom/feralinteractive/nativeframework/FeralGoogleBillingServicesInterface;"
private const val POLICY_IFACE = "Lcom/google/android/vending/licensing/Policy;"
private const val CALLBACK_IFACE = "Lcom/google/android/vending/licensing/LicenseCheckerCallback;"

// Skus shipped inside libLaraCroftGoL.so (TBYB product ids).
private const val SKU_FULL_GAME = "Demo.FullGame"
private const val SKU_FULL_GAME_PLUS_DLC = "Demo.FullGamePlusDLC"

@Suppress("unused")
val unlockFullGamePatch = bytecodePatch(
    name = "Unlock full game (TBYB bypass + license)",
    description = "Unlocks the full game (all levels + DLC) of the " +
        "try-before-you-buy version and bypasses the Google Play " +
        "Licensing (LVL) startup check that blocks re-signed APKs. " +
        "Injects 'Demo.FullGame' and 'Demo.FullGamePlusDLC' as already " +
        "purchased into the Feral billing bridge, so the native game " +
        "marks the full game as owned without contacting Google Play. " +
        "Also patches every LVL Policy.allowAccess() to return true " +
        "and the LicenseCheckerCallback failure callbacks to behave " +
        "as licensed. Note: this game does NOT use PairIP (verified " +
        "against the manifest and all split APKs).",
    default = true,
) {
    compatibleWith(LARA_CROFT_GOL)

    execute {
        val logger = Logger.getLogger("LaraGoL")

        // ==============================================================
        // HOOK 1: LVL Policy.allowAccess() -> true
        // ==============================================================
        // Patching p0 (this) is register-count safe on any method:
        //   const/4 p0, 0x1   (clobbers `this`, never used again)
        //   return p0         (returns 1 == true)
        // ==============================================================
        var policyCount = 0
        classDefForEach { classDef ->
            val ifaces = classDef.interfaces
            if (ifaces == null || ifaces.none { it == POLICY_IFACE }) return@classDefForEach
            val mutableClass = mutableClassDefBy(classDef)
            mutableClass.methods
                .filter { it.name == "allowAccess" && it.returnType == "Z" && it.parameterTypes.isEmpty() }
                .forEach { method ->
                    if (method.implementation != null) {
                        method.addInstructions(0, """
                            const/4 p0, 0x1
                            return p0
                        """.trimIndent())
                        policyCount++
                        logger.info("  patched Policy.allowAccess: ${mutableClass.type}")
                    }
                }
        }
        logger.info("Hook 1: patched $policyCount LVL Policy.allowAccess() methods")

        // ==============================================================
        // HOOK 2: LicenseCheckerCallback dontAllow/applicationError
        //         -> this.allow(reason)
        // ==============================================================
        // allow(I)V is public in the callback class (it implements the
        // interface), so invoke-virtual works. allow() itself checks
        // isFinishing() and an internal "already allowed" flag before
        // draining the startup queue (f0.k()), so calling it twice is
        // harmless.
        // ==============================================================
        var callbackClassType: String? = null
        classDefForEach { classDef ->
            val ifaces = classDef.interfaces
            if (ifaces != null && ifaces.any { it == CALLBACK_IFACE }) {
                if (callbackClassType == null) callbackClassType = classDef.type
            }
        }

        var callbackCount = 0
        if (callbackClassType != null) {
            val cls = callbackClassType!!
            val mutableClass = mutableClassDefBy(cls)
            mutableClass.methods
                .filter { (it.name == "dontAllow" || it.name == "applicationError") && it.returnType == "V" }
                .filter { it.parameterTypes.size == 1 && it.parameterTypes[0].toString() == "I" }
                .forEach { method ->
                    if (method.implementation != null) {
                        method.addInstructions(0, """
                            invoke-virtual {p0, p1}, $cls->allow(I)V
                            return-void
                        """.trimIndent())
                        callbackCount++
                        logger.info("  patched callback method: $cls->${method.name}(I)")
                    }
                }
        }
        logger.info("Hook 2: patched $callbackCount license failure callbacks")

        // ==============================================================
        // HOOK 3: inject fake purchases into the billing bridge
        // ==============================================================
        // Find the class that extends FeralGoogleBillingServicesInterface
        // (framework/q0 in 1.2.6RC1) and inside it the method that
        // invokes nativeProcessPurchaseItems (the static feeder
        // a(q0, g, List, Z)). We prepend two fake purchases.
        //
        // Smali (p2 = the List parameter, discovered dynamically):
        //   if-nez p2, :lara_ok
        //   new-instance v0, Ljava/util/ArrayList;
        //   invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
        //   move-object p2, v0
        //   :lara_ok
        //   const-string v0, "<json>"
        //   const-string v1, ""
        //   new-instance v2, Lcom/android/billingclient/api/Purchase;
        //   invoke-direct {v2, v0, v1}, .../Purchase;-><init>(...)V
        //   new-instance v0, .../FeralBillingClientAPI$Purchase;
        //   invoke-direct {v0, v2}, ...$Purchase;-><init>(...)V
        //   invoke-interface {p2, v0}, Ljava/util/List;->add(...)Z
        //   (x2, one per SKU)
        //
        // Purchase JSON notes (verified against the shipped
        // com.android.billingclient.api.Purchase):
        //   - "purchaseState": 0 in json -> getPurchaseState()==1
        //     (PURCHASED). Only 4 (PENDING) maps to 2.
        //   - "acknowledged": true -> the feeder takes the direct
        //     path into the nativeProcessPurchaseItems array instead
        //     of the nativeAcknowledgePurchase sub-flow.
        //   - "productId" is read by getSkus()/zza().
        // ==============================================================
        val pkg = "com.feralinteractive.laracroftgol_android"
        val jsonFor = fun(token: String, sku: String): String =
            """{\"orderId\":\"$token\",\"packageName\":\"$pkg\",\"productId\":\"$sku\",\"purchaseTime\":1,\"purchaseState\":0,\"purchaseToken\":\"$token\",\"acknowledged\":true,\"autoRenewing\":false}"""

        var billingClassType: String? = null
        classDefForEach { classDef ->
            if (billingClassType != null) return@classDefForEach
            if (classDef.superclass == BILLING_IFACE) {
                val hasFeeder = classDef.methods.any { m ->
                    m.implementation?.instructions?.any { insn ->
                        if (insn is ReferenceInstruction) {
                            val ref = insn.reference
                            ref is MethodReference && ref.name == "nativeProcessPurchaseItems"
                        } else false
                    } ?: false
                }
                if (hasFeeder) billingClassType = classDef.type
            }
        }

        if (billingClassType == null) {
            logger.severe("Hook 3 FAILED: no class extending FeralGoogleBillingServicesInterface with a nativeProcessPurchaseItems feeder was found")
            return@execute
        }

        val mutableBillingClass = mutableClassDefBy(billingClassType!!)
        val feeder = mutableBillingClass.methods.firstOrNull { m ->
            m.implementation?.instructions?.any { insn ->
                if (insn is ReferenceInstruction) {
                    val ref = insn.reference
                    ref is MethodReference && ref.name == "nativeProcessPurchaseItems"
                } else false
            } ?: false
        }

        if (feeder == null || feeder.implementation == null) {
            logger.severe("Hook 3 FAILED: feeder method not mutable")
            return@execute
        }

        // The List parameter index (p2 in 1.2.6RC1: a(q0, g, List, Z)).
        val listIndex = feeder.parameterTypes.indexOfFirst { it.toString() == "Ljava/util/List;" }
        if (listIndex < 0) {
            logger.severe("Hook 3 FAILED: feeder has no Ljava/util/List; parameter")
            return@execute
        }
        val pList = "p$listIndex"

        // We need 3 local registers (v0, v1, v2). The feeder in
        // 1.2.6RC1 has 7 locals; verify to stay safe on other builds.
        val impl = feeder.implementation!!
        val locals = impl.registerCount - feeder.parameterTypes.size
        if (locals < 3) {
            logger.severe("Hook 3 FAILED: feeder method has only $locals local registers (need 3)")
            return@execute
        }

        val jsonFull = jsonFor("patch.0001", SKU_FULL_GAME)
        val jsonDlc = jsonFor("patch.0002", SKU_FULL_GAME_PLUS_DLC)

        val smali = StringBuilder()
        smali.appendLine("if-nez $pList, :lara_has_list")
        smali.appendLine("new-instance v0, Ljava/util/ArrayList;")
        smali.appendLine("invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V")
        smali.appendLine("move-object $pList, v0")
        smali.appendLine(":lara_has_list")
        for (json in listOf(jsonFull, jsonDlc)) {
            smali.appendLine("const-string v0, \"$json\"")
            smali.appendLine("const-string v1, \"\"")
            smali.appendLine("new-instance v2, Lcom/android/billingclient/api/Purchase;")
            smali.appendLine("invoke-direct {v2, v0, v1}, Lcom/android/billingclient/api/Purchase;-><init>(Ljava/lang/String;Ljava/lang/String;)V")
            smali.appendLine("new-instance v0, Lcom/feralinteractive/framework/FeralBillingClientAPI${'$'}Purchase;")
            smali.appendLine("invoke-direct {v0, v2}, Lcom/feralinteractive/framework/FeralBillingClientAPI${'$'}Purchase;-><init>(Lcom/android/billingclient/api/Purchase;)V")
            smali.appendLine("invoke-interface {$pList, v0}, Ljava/util/List;->add(Ljava/lang/Object;)Z")
        }

        feeder.addInstructions(0, smali.toString().trimEnd())
        logger.info("Hook 3: injected fake purchases ($SKU_FULL_GAME + $SKU_FULL_GAME_PLUS_DLC) into ${mutableBillingClass.type}->${feeder.name}")

        logger.info("Unlock full game patch applied: $policyCount policies, $callbackCount callbacks, 1 feeder")
    }
}
