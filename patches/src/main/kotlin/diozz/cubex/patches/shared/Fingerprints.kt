/*
 * Copyright 2025 Miguel's Patches
 * https://github.com/MiguelNinja19/miguel-morphe-patches
 *
 * Fingerprints for the PremiumHelper SDK shipped inside CubeX Solver.
 *
 * NOTE: We don't pin definingClass because the obfuscation (zc.g, rc.a,
 * etc.) can change between APK versions. Instead, we anchor on stable
 * string literals that are part of the SDK's on-disk SharedPreferences
 * keys and assertion strings.
 */

package diozz.cubex.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * PremiumHelper.hasActivePurchase() -> Z
 *
 * This is the boolean getter that returns SharedPreferences.getBoolean
 * ("has_active_purchase", false). The string "has_active_purchase" is
 * the on-disk key the PremiumHelper SDK has been using since at least
 * version 4.x, and is extremely stable.
 *
 * We deliberately do NOT pin definingClass because the obfuscated class
 * name (zc.g) can change between APK versions / build configurations.
 * The string alone is unique enough to identify this method.
 *
 * The method has signature ()Z (no parameters, returns boolean).
 */
object PremiumHelperHasActivePurchaseFingerprint : Fingerprint(
    // No definingClass - search the entire APK
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(
        string("has_active_purchase"),
    )
)
