package app.morphe.patches.all.misc.ads

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import java.util.logging.Logger

@Suppress("unused")
val universalDisableAdComponentsPatch = resourcePatch(
    name = "Universal disable ad components",
    description = "Disables ad-related activities, receivers, and services in AndroidManifest.xml.",
    default = false,
) {
    execute {
        val logger = Logger.getLogger("UniversalDisableAdComponents")

        val adKeywords = listOf(
            "adactivity", "adsactivity", "admob", "adcolony",
            "applovin", "chartboost", "flurry", "ironsource",
            "mopub", "tapjoy", "unityads", "vungle",
            "interstitial", "bannerad", "rewardedad",
            "adreceiver", "adsservice", "adsprovider",
        )

        var disabledCount = 0

        document("AndroidManifest.xml").use { doc ->
            val componentTags = listOf("activity", "receiver", "service", "provider")

            for (tagName in componentTags) {
                val nodes = doc.getElementsByTagName(tagName)
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as Element
                    val name = element.getAttribute("android:name") ?: ""
                    val nameLower = name.lowercase()

                    val isAdComponent = adKeywords.any { keyword ->
                        nameLower.contains(keyword)
                    }

                    if (isAdComponent) {
                        element.setAttribute("android:enabled", "false")
                        disabledCount++
                        logger.info("Disabled: $tagName $name")
                    }
                }
            }
        }

        logger.info("Universal disable ad components: Disabled $disabledCount components")
    }
}
