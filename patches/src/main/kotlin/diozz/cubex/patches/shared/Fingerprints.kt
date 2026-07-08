/*
 * Copyright 2025 diozz-cubex-patches
 * Fingerprints for the PremiumHelper SDK shipped inside CubeX Solver.
 */

package diozz.cubex.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * zc.g.i() -> Z  (PremiumHelper.hasActivePurchase)
 *
 * Returns true if a premium subscription is active. Patching to always
 * return true unlocks all premium features.
 *
 * Anchor: the literal string "has_active_purchase" (the SharedPreferences
 * key — very stable across ZipoApps SDK versions).
 */
object PremiumHelperHasActivePurchaseFingerprint : Fingerprint(
    definingClass = "Lzc/g;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        string("has_active_purchase"),
    )
)

/**
 * zc.k.h() -> Z  (shouldShowRelaunch)
 *
 * Main "should we show relaunch / premium upsell" gate.
 * Anchored on the unique call to Lrc/v;->b()Z
 * (PhConsentManager.isConsentRequired).
 */
object ShouldShowRelaunchFingerprint : Fingerprint(
    definingClass = "Lzc/k;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lrc/v;",
            name = "b",
            returnType = "Z",
        ),
    )
)
