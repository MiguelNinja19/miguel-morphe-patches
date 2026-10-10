/*
 * Unlock full game (IAP entitlements) for Duck Life 4
 * (com.mad.ducklife).
 *
 * RESEARCH SUMMARY (v2026.5.12 / versionCode 300084, XAPK analyzed
 * from APKPure):
 *
 * - PairIP present (see BypassPairIPPatch) but no other DRM. The
 *   manifest also declares com.android.vending.CHECK_LICENSE, which
 *   is the permission PairIP itself uses; there is no separate
 *   Google Play Licensing (LVL) implementation.
 *
 * - Engine: Unity IL2CPP (libil2cpp.so 58.1 MB, metadata v39).
 *   Same MAD.com / Wix Games framework as Duck Life 6: Space: the
 *   game logic (including the WixGames.IAP IAPManager) is compiled
 *   to native code and the app has NO game-specific Java classes.
 *   All bundled Java code is ad/billing SDKs (AdMob/Unity Ads/
 *   LevelPlay + Google Play Billing 8.0.0), and the C# side talks
 *   to com.android.billingclient.api.* through the
 *   bitter.jnibridge.JNIBridge reflection proxies.
 *
 * - The game used to be a PAID app: the C# side still carries the
 *   migration helpers HasBoughtLegacyFullGame / HasBoughtLegacyAdBlock
 *   (identifiers in global-metadata.dat) and the persisted save flag
 *   "has_purchased_full_game". Today the free build sells non-
 *   consumable IAPs whose product ids are plain strings in
 *   global-metadata.dat:
 *     "full_game"        (the full version unlock - the old paywall)
 *     "ad_block"         (remove ads)
 *     "super_ad_block"   (super ad block)
 *     "upgrade_ad_block" (upgrade ad_block -> super_ad_block)
 *
 * - The embedded com.android.billingclient.api.Purchase constructor
 *   (String json, String signature) does NOT validate the signature:
 *   it just stores both strings and parses the JSON with
 *   org.json.JSONObject (verified instruction by instruction in the
 *   shipped classes.dex). No RSA public key is shipped anywhere.
 *   getPurchaseState() returns PURCHASED for purchaseState 0 and
 *   isAcknowledged() defaults to true - fake receipts are accepted.
 *
 * PATCH STRATEGY (single bytecode hook, obfuscation-robust discovery):
 *
 * HOOK - BillingClientImpl.queryPurchasesAsync(params, listener) is
 *   the choke point every purchase-inventory refresh goes through
 *   (9 registers / 6 locals in this build, same as Duck Life 6:
 *   Space). The C# IAPManager calls it (via the JNI reflection
 *   bridge) right after the billing client connects and feeds the
 *   returned list to its entitlement pipeline (RefreshEntitlements /
 *   ApplyOnPurchases / HasBought*), which persists the flags in the
 *   save game ("has_purchased_full_game", adBlock, superAdBlock).
 *
 *   We prepend instructions that build a BillingResult with response
 *   code 0 (OK) and a list with the four product ids above as
 *   already-purchased/acknowledged Purchase objects, hand them to the
 *   listener (the runtime proxy that forwards to C#), and return
 *   without contacting Google Play. The full-game entitlement then
 *   flows through the game's own legitimate pipeline.
 */

package com.mad.ducklife.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.mad.ducklife.patches.shared.DUCK_LIFE_4
import java.util.logging.Logger

private const val BILLING_CLIENT = "Lcom/android/billingclient/api/BillingClient;"
private const val QUERY_PARAMS = "Lcom/android/billingclient/api/QueryPurchasesParams;"
private const val RESPONSE_LISTENER = "Lcom/android/billingclient/api/PurchasesResponseListener;"
private const val PURCHASE = "Lcom/android/billingclient/api/Purchase;"
private const val BILLING_RESULT = "Lcom/android/billingclient/api/BillingResult;"
// NOTE: for inner-class descriptors (e.g. BillingResult$Builder) write the
// FULL literal "...BillingResult${'$'}Builder;" — same style as the Supreme
// and Assassin patches. NEVER concatenate BILLING_RESULT + "$Builder":
// BILLING_RESULT already ends with ';' and the result
// "BillingResult;$Builder" is an invalid descriptor that crashes the
// inline smali compiler with "parser/lexer syntax errors" (bug found in
// the Duck Life Space patch, v1.14.0-dev.11, diagnosed and fixed with the
// morphe-desktop CLI).

private const val PKG = "com.mad.ducklife"

// Product ids shipped inside global-metadata.dat (string literals).
private val SKUS = listOf(
    "full_game",
    "ad_block",
    "super_ad_block",
    "upgrade_ad_block",
)

@Suppress("unused")
val unlockFullGamePatch = bytecodePatch(
    name = "Unlock full game (IAP entitlements)",
    description = "Unlocks everything the paid/legacy version and the " +
        "in-app purchases give in Duck Life 4: injects the four store " +
        "product ids (full_game, ad_block, super_ad_block, " +
        "upgrade_ad_block) as already-purchased into the Google Play " +
        "Billing queryPurchases response, so the game's own entitlement " +
        "pipeline marks the full version as bought and ads as blocked " +
        "without contacting Google Play. Receipts are accepted because " +
        "the game performs no signature validation. Requires the " +
        "'Bypass PairIP license check' patch to be enabled so the " +
        "license screen doesn't block the app when installed outside " +
        "the Play Store.",
    default = true,
) {
    compatibleWith(DUCK_LIFE_4)

    execute {
        val logger = Logger.getLogger("DuckLife4")

        // ==============================================================
        // Find the concrete billing client class:
        //   extends BillingClient (abstract) and implements
        //   queryPurchasesAsync(QueryPurchasesParams,
        //                       PurchasesResponseListener) with code.
        // In 2026.5.12 this is Lcom/android/billingclient/api/
        // BillingClientImpl;. Runtime subclasses inherit the method,
        // so patching the parent covers every instance.
        // ==============================================================
        var targetClassType: String? = null
        classDefForEach { classDef ->
            if (targetClassType != null) return@classDefForEach
            if (classDef.superclass != BILLING_CLIENT) return@classDefForEach
            val hasQuery = classDef.methods.any { m ->
                m.name == "queryPurchasesAsync" &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[0].toString() == QUERY_PARAMS &&
                    m.parameterTypes[1].toString() == RESPONSE_LISTENER &&
                    m.implementation != null
            }
            if (hasQuery) targetClassType = classDef.type
        }

        if (targetClassType == null) {
            logger.severe("Hook FAILED: no concrete BillingClient subclass with queryPurchasesAsync(QueryPurchasesParams, PurchasesResponseListener) was found")
            return@execute
        }

        val mutableClass = mutableClassDefBy(targetClassType!!)
        val queryMethod = mutableClass.methods.firstOrNull { m ->
            m.name == "queryPurchasesAsync" &&
                m.parameterTypes.size == 2 &&
                m.parameterTypes[0].toString() == QUERY_PARAMS &&
                m.parameterTypes[1].toString() == RESPONSE_LISTENER &&
                m.implementation != null
        }

        if (queryMethod == null || queryMethod.implementation == null) {
            logger.severe("Hook FAILED: queryPurchasesAsync is not mutable")
            return@execute
        }

        // The listener parameter is p2 (this + 2 params).
        // Our stub uses v0..v4, so we need at least 5 local registers
        // (the method in 2026.5.12 has 9 registers: 6 locals).
        val impl = queryMethod.implementation!!
        val locals = impl.registerCount - queryMethod.parameterTypes.size - 1
        if (locals < 5) {
            logger.severe("Hook FAILED: queryPurchasesAsync has only $locals local registers (need 5)")
            return@execute
        }

        // Purchase JSON (verified against the shipped billing client):
        //   - "productId" (String) is read by getProducts() fallback
        //   - "purchaseState": 0 -> getPurchaseState() == 1 (PURCHASED)
        //   - "acknowledged": true -> straight into the entitlement flow
        val jsonFor = fun(token: String, sku: String): String =
            """{\"orderId\":\"$token\",\"packageName\":\"$PKG\",\"productId\":\"$sku\",\"purchaseTime\":1,\"purchaseState\":0,\"purchaseToken\":\"$token\",\"acknowledged\":true,\"autoRenewing\":false}"""

        val sb = StringBuilder()
        // Safety: a null listener would crash the original code path.
        sb.appendLine("if-nez p2, :dl4_go")
        sb.appendLine("return-void")
        sb.appendLine(":dl4_go")
        // BillingResult with response code 0 (OK).
        sb.appendLine("invoke-static {}, $BILLING_RESULT->newBuilder()Lcom/android/billingclient/api/BillingResult${'$'}Builder;")
        sb.appendLine("move-result-object v0")
        sb.appendLine("const/4 v1, 0x0")
        sb.appendLine("invoke-virtual {v0, v1}, Lcom/android/billingclient/api/BillingResult${'$'}Builder;->setResponseCode(I)Lcom/android/billingclient/api/BillingResult${'$'}Builder;")
        sb.appendLine("move-result-object v0")
        sb.appendLine("invoke-virtual {v0}, Lcom/android/billingclient/api/BillingResult${'$'}Builder;->build()$BILLING_RESULT")
        sb.appendLine("move-result-object v0")
        // The purchase list.
        sb.appendLine("new-instance v1, Ljava/util/ArrayList;")
        sb.appendLine("invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V")
        for ((i, sku) in SKUS.withIndex()) {
            val json = jsonFor("patch.dl4.000${i + 1}", sku)
            sb.appendLine("const-string v2, \"$json\"")
            sb.appendLine("const-string v3, \"\"")
            sb.appendLine("new-instance v4, $PURCHASE")
            sb.appendLine("invoke-direct {v4, v2, v3}, $PURCHASE-><init>(Ljava/lang/String;Ljava/lang/String;)V")
            sb.appendLine("invoke-virtual {v1, v4}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z")
        }
        // Deliver the fake inventory to the C# proxy and stop: the
        // real (Play Store) query never runs.
        sb.appendLine("invoke-interface {p2, v0, v1}, $RESPONSE_LISTENER->onQueryPurchasesResponse(Lcom/android/billingclient/api/BillingResult;Ljava/util/List;)V")
        sb.appendLine("return-void")

        queryMethod.addInstructions(0, sb.toString().trimEnd())
        logger.info("Hook: injected fake purchases (${SKUS.joinToString(", ")}) into ${mutableClass.type}->queryPurchasesAsync")

        logger.info("Unlock full game patch applied: 1 billing hook (4 fake entitlements)")
    }
}
