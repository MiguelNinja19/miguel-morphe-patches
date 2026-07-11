package app.morphe.patches.all.misc.ads

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch
import java.util.logging.Logger

@Suppress("unused")
val universalRemoveAdsPatch = bytecodePatch(
    name = "Universal remove ads",
    description = "Scans the target APK for common ad SDK classes and patches their ad-loading methods to do nothing.",
    default = false,
) {
    execute {
        val logger = Logger.getLogger("UniversalRemoveAds")

        val adSdkPrefixes = setOf(
            "Lcom/google/ads/",
            "Lcom/google/android/gms/ads/",
            "Lcom/google/android/gms/internal/ads/",
            "Lcom/adcolony/",
            "Lcom/applovin/",
            "Lcom/chartboost/",
            "Lcom/flurry/",
            "Lcom/ironsource/",
            "Lcom/mopub/",
            "Lcom/tapjoy/",
            "Lcom/unity3d/ads/",
            "Lcom/vungle/",
            "Lcom/millennialmedia/",
            "Lcom/inmobi/",
            "Lcom/startapp/android/ads/",
            "Lcom/fyber/",
            "Lcom/mintegral/",
            "Lcom/bytedance/sdk/",
            "Lcom/applovin/mediation/",
        )

        val adMethodNames = setOf(
            "loadAd", "loadAds", "showAd", "showAds",
            "loadInterstitial", "showInterstitial",
            "displayInterstitial", "loadVideo", "showVideo",
            "loadBanner", "showBanner",
            "loadNativeAd", "showNativeAd",
            "loadRewardedVideo", "showRewardedVideo",
            "loadRewardedAd", "showRewardedAd",
        )

        var patchedCount = 0

        classDefForEach { classDef ->
            val className = classDef.type

            val isAdClass = adSdkPrefixes.any { prefix ->
                className.startsWith(prefix)
            }

            if (!isAdClass) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)

            mutableClass.methods.forEach { method ->
                if (method.implementation == null) return@forEach

                val methodName = method.name
                val returnType = method.returnType

                if (methodName !in adMethodNames) return@forEach

                when (returnType) {
                    "V" -> {
                        method.replaceInstructions(0, """
                            return-void
                        """.trimIndent())
                        patchedCount++
                        logger.info("Patched: $className->$methodName() = void")
                    }
                    "Z" -> {
                        method.replaceInstructions(0, """
                            const/4 v0, 0x0
                            return v0
                        """.trimIndent())
                        patchedCount++
                        logger.info("Patched: $className->$methodName() = false")
                    }
                }
            }
        }

        logger.info("Universal remove ads: Patched $patchedCount ad-related methods")
    }
}
