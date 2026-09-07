/*
 * Unlock full game (IAP entitlements) for Duck Life 6: Space
 * (com.mad.DuckLifeSpace).
 *
 * RESEARCH SUMMARY (v2026.729.2 / versionCode 400079, XAPK analyzed
 * from APKPure):
 *
 * - NO PairIP. Confirmed by: (1) no PairipApplication / license
 *   activity in AndroidManifest.xml, (2) no libpairipcore.so in any
 *   split, (3) no "pairip" string anywhere in the 3 classes.dex.
 *   No Google Play Licensing (LVL) either (no CHECK_LICENSE
 *   permission): the only protection is Google Play Billing.
 *
 * - Engine: Unity IL2CPP (libil2cpp.so 62.8 MB, metadata v39).
 *   The game logic (including the IAP manager) is compiled to
 *   native code, and the app has NO game-specific Java classes.
 *   All bundled Java code is ad/billing SDKs (ironSource LevelPlay,
 *   Unity Ads, Vungle, BidMachine, Google Play Billing 8.0.0).
 *
 * - The C# "IAPManager" (from global-metadata.dat identifiers:
 *   HasBoughtAdBlock / HasBoughtSuperAdBlock / HasBoughtLegacyFullGame,
 *   ApplyEntitlement, RefreshEntitlements, ProcessPurchase) talks to
 *   Google Play Billing directly through the "bitter.jnibridge.JNIBridge"
 *   helper (java.lang.reflect proxies created at runtime), referencing
 *   com.android.billingclient.api.* classes by name via JNI.
 *
 * - The game used to be a PAID app ("HasBoughtLegacyFullGame" migrates
 *   old buyers). The current free build sells three non-consumable
 *   IAPs, whose product ids are plain strings in global-metadata.dat:
 *     "ad_block_dl6"        (remove ads)
 *     "super_ad_block_dl6"  (super ad block)
 *     "upgrade_ad_block_dl6" (upgrade ad block)
 *
 * - The embedded com.android.billingclient.api.Purchase constructor
 *   (String json, String signature) does NOT validate the signature:
 *   it just stores both strings and parses the JSON with
 *   org.json.JSONObject. Verified in the DEX:
 *     getProducts()   -> falls back to "productId" (String) when
 *                        "productIds" (JSONArray) is absent
 *     getPurchaseState() -> optInt("purchaseState", 1); anything
 *                        other than 4 (PENDING) returns 1 PURCHASED
 *     isAcknowledged()   -> optBoolean("acknowledged", true)
 *   No RSA public key is shipped anywhere (DEX or metadata), so fake
 *   receipts are accepted by the game.
 *
 * PATCH STRATEGY (single bytecode hook, obfuscation-robust discovery):
 *
 * HOOK - BillingClientImpl.queryPurchasesAsync(params, listener) is
 *   the choke point every purchase-inventory refresh goes through.
 *   The C# side calls it (via the JNI reflection bridge) right after
 *   the billing client connects, and feeds the returned list to
 *   RefreshEntitlements -> ApplyEntitlement, which persists
 *   adBlock/superAdBlock in the save game.
 *
 *   We prepend instructions that build a BillingResult with response
 *   code 0 (OK) and a list with the three product ids above as
 *   already-purchased/acknowledged Purchase objects, hand them to the
 *   listener (the runtime proxy that forwards to C#), and return
 *   without contacting Google Play. The entitlement then flows through
 *   the game's own legitimate pipeline.
 *
 *   Register budget: the hooked method has 6 locals (9 registers,
 *   2 params + this); our stub needs v0..v4 only. If a future build
 *   has fewer locals the patch logs a severe message and skips
 *   instead of breaking the build.
 */

package com.mad.DuckLifeSpace.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.mad.DuckLifeSpace.patches.shared.DUCK_LIFE_SPACE
import java.util.logging.Logger

private const val BILLING_CLIENT = "Lcom/android/billingclient/api/BillingClient;"
private const val QUERY_PARAMS = "Lcom/android/billingclient/api/QueryPurchasesParams;"
private const val RESPONSE_LISTENER = "Lcom/android/billingclient/api/PurchasesResponseListener;"
private const val PURCHASE = "Lcom/android/billingclient/api/Purchase;"
private const val BILLING_RESULT = "Lcom/android/billingclient/api/BillingResult;"

private const val PKG = "com.mad.DuckLifeSpace"

// Product ids shipped inside global-metadata.dat (string literals).
private val SKUS = listOf(
    "ad_block_dl6",
    "super_ad_block_dl6",
    "upgrade_ad_block_dl6",
)

@Suppress("unused")
val unlockFullGamePatch = bytecodePatch(
    name = "Unlock full game (IAP entitlements)",
    description = "Unlocks everything the paid/legacy version and the " +
        "in-app purchases give in Duck Life 6: Space: injects the three " +
        "store product ids (ad_block_dl6, super_ad_block_dl6, " +
        "upgrade_ad_block_dl6) as already-purchased into the Google Play " +
        "Billing queryPurchases response, so the game's own entitlement " +
        "pipeline marks ads as blocked (adBlock + superAdBlock) without " +
        "contacting Google Play. Receipts are accepted because the game " +
        "performs no signature validation and ships no license check. " +
        "Note: this game does NOT use PairIP (verified against the " +
        "manifest and all split APKs).",
    default = true,
) {
    compatibleWith(DUCK_LIFE_SPACE)

    execute {
        val logger = Logger.getLogger("DuckLifeSpace")

        // ==============================================================
        // Find the concrete billing client class:
        //   extends BillingClient (abstract) and implements
        //   queryPurchasesAsync(QueryPurchasesParams,
        //                       PurchasesResponseListener) with code.
        // In 2026.729.2 this is Lcom/android/billingclient/api/
        // BillingClientImpl;. Runtime subclasses (e.g. zzce) inherit
        // the method, so patching the parent covers every instance.
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

        // The listener parameter is p2 (this + params + params).
        // Our stub uses v0..v4, so we need at least 5 local registers
        // (the method in 2026.729.2 has 9 registers: 6 locals).
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
        sb.appendLine("if-nez p2, :dls_go")
        sb.appendLine("return-void")
        sb.appendLine(":dls_go")
        // BillingResult with response code 0 (OK).
        sb.appendLine("invoke-static {}, $BILLING_RESULT->newBuilder()Lcom/android/billingclient/api/BillingResult${'$'}Builder;")
        sb.appendLine("move-result-object v0")
        sb.appendLine("const/4 v1, 0x0")
        sb.appendLine("invoke-virtual {v0, v1}, $BILLING_RESULT${'$'}Builder->setResponseCode(I)Lcom/android/billingclient/api/BillingResult${'$'}Builder;")
        sb.appendLine("move-result-object v0")
        sb.appendLine("invoke-virtual {v0}, $BILLING_RESULT${'$'}Builder->build()Lcom/android/billingclient/api/BillingResult;")
        sb.appendLine("move-result-object v0")
        // The purchase list.
        sb.appendLine("new-instance v1, Ljava/util/ArrayList;")
        sb.appendLine("invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V")
        for ((i, sku) in SKUS.withIndex()) {
            val json = jsonFor("patch.dl6.000${i + 1}", sku)
            sb.appendLine("const-string v2, \"$json\"")
            sb.appendLine("const-string v3, \"\"")
            sb.appendLine("new-instance v4, $PURCHASE")
            sb.appendLine("invoke-direct {v4, v2, v3}, $PURCHASE-><init>(Ljava/lang/String; Ljava/lang/String;)V")
            sb.appendLine("invoke-virtual {v1, v4}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z")
        }
        // Deliver the fake inventory to the C# proxy and stop: the
        // real (Play Store) query never runs.
        sb.appendLine("invoke-interface {p2, v0, v1}, $RESPONSE_LISTENER->onQueryPurchasesResponse(Lcom/android/billingclient/api/BillingResult; Ljava/util/List;)V")
        sb.appendLine("return-void")

        queryMethod.addInstructions(0, sb.toString().trimEnd())
        logger.info("Hook: injected fake purchases (${SKUS.joinToString(", ")}) into ${mutableClass.type}->queryPurchasesAsync")

        logger.info("Unlock full game patch applied: 1 billing hook (3 fake entitlements)")
    }
}
