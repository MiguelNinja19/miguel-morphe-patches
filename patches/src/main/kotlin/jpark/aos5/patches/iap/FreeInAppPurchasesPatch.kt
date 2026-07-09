/*
 * Free In-App Purchases patch for Anger of Stick 5 : Zombie.
 *
 * This patch does THREE things in one:
 *
 * 1. JAVA: Patches launchPurchaseFlow to call nativeOnSuccess directly,
 *    skipping Google Play Billing. Credits items without payment.
 *
 * 2. NATIVE: Modifies libMyGame.so to change the boolean parameter
 *    passed to the C++ onSuccess from false (0) to true (1).
 *
 * 3. Disables setRestore/restorePurchases to prevent startup issues.
 */

package jpark.aos5.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import jpark.aos5.patches.shared.Constants.ANGER_OF_STICK_5
import jpark.aos5.patches.shared.LaunchPurchaseFlowFingerprint
import jpark.aos5.patches.shared.RestorePurchasesFingerprint
import jpark.aos5.patches.shared.SetRestoreFingerprint

@Suppress("unused")
val freeInAppPurchasesPatch = bytecodePatch(
    name = "Free in-app purchases",
    description = "Skips Google Play Billing and credits IAP items (gem packs, " +
        "coin packs, starter packs) directly. Also patches libMyGame.so to " +
        "fix the native purchase callback. Disables the startup purchase-restore " +
        "flow to prevent loading screen issues.",
    default = true,
) {
    compatibleWith(ANGER_OF_STICK_5)

    execute {
        // 1) JAVA: Patch launchPurchaseFlow
        val method = LaunchPurchaseFlowFingerprint.method
        method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                invoke-static {p1, v0}, Lorg/cocos2dx/cpp/AppActivity;->nativeOnSuccess(Ljava/lang/String;Z)V
                return-void
            """.trimIndent()
        )

        // 2) NATIVE: Patch libMyGame.so ARM64
        try {
            val arm64File = get("lib/arm64-v8a/libMyGame.so")
            val arm64Bytes = arm64File.readBytes()

            val arm64Search = byteArrayOf(
                0xE0.toByte(), 0x03, 0x13, 0xAA.toByte(),
                0xE2.toByte(), 0x03, 0x1F, 0x2A.toByte(),
                0x00, 0x01, 0x3F, 0xD6.toByte()
            )
            val arm64Replace = byteArrayOf(
                0xE0.toByte(), 0x03, 0x13, 0xAA.toByte(),
                0x22, 0x00, 0x80, 0x52,
                0x00, 0x01, 0x3F, 0xD6.toByte()
            )

            val idx = arm64Bytes.indexOfSlice(arm64Search)
            if (idx >= 0) {
                for (i in arm64Replace.indices) {
                    arm64Bytes[idx + i] = arm64Replace[i]
                }
                arm64File.writeBytes(arm64Bytes)
            }
        } catch (e: Exception) {
        }

        // 2b) NATIVE: Patch libMyGame.so ARM32
        try {
            val arm32File = get("lib/armeabi-v7a/libMyGame.so")
            val arm32Bytes = arm32File.readBytes()

            val search1 = byteArrayOf(0x20, 0x46, 0x00, 0x22, 0x98.toByte(), 0x47)
            val replace1 = byteArrayOf(0x20, 0x46, 0x01, 0x22, 0x98.toByte(), 0x47)
            val idx1 = arm32Bytes.indexOfSlice(search1)
            if (idx1 >= 0) {
                for (i in replace1.indices) {
                    arm32Bytes[idx1 + i] = replace1[i]
                }
            }

            val search2 = byteArrayOf(
                0xD2.toByte(), 0xF8.toByte(), 0xA8.toByte(), 0x32,
                0x00, 0x22, 0x18, 0x47
            )
            val replace2 = byteArrayOf(
                0xD2.toByte(), 0xF8.toByte(), 0xA8.toByte(), 0x32,
                0x01, 0x22, 0x18, 0x47
            )
            val idx2 = arm32Bytes.indexOfSlice(search2)
            if (idx2 >= 0) {
                for (i in replace2.indices) {
                    arm32Bytes[idx2 + i] = replace2[i]
                }
            }

            if (idx1 >= 0 || idx2 >= 0) {
                arm32File.writeBytes(arm32Bytes)
            }
        } catch (e: Exception) {
        }

        // 3) Disable restore-purchases on startup
        SetRestoreFingerprint.method.addInstructions(0, "return-void")
        RestorePurchasesFingerprint.method.addInstructions(0, "return-void")
    }
}
