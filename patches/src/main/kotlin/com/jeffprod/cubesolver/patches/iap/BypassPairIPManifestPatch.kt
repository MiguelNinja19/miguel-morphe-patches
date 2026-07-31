package com.jeffprod.cubesolver.patches.iap

import app.morphe.patcher.patch.resourcePatch
import com.jeffprod.cubesolver.patches.shared.CUBE_SOLVER
import org.w3c.dom.Element

/**
 * Resource patch that modifies AndroidManifest.xml to bypass PairIP:
 *
 * 1. Changes android:name from "com.pairip.application.Application"
 *    to "com.jeffprod.cubesolver.App" — skips attachBaseContext which
 *    calls SignatureCheck.verifyIntegrity and LicenseClient.checkLicense.
 *
 * 2. Removes LicenseActivity — can't redirect to Play Store.
 *
 * 3. Removes CHECK_LICENSE permission — no longer needed.
 *
 * This is a separate resourcePatch because bytecodePatch doesn't have
 * access to the document() function for XML manipulation.
 */
val bypassPairIPManifestPatch = resourcePatch(
    name = "Bypass PairIP manifest",
    description = "Modifies AndroidManifest.xml to skip the PairIP " +
        "Application class, remove LicenseActivity, and remove " +
        "CHECK_LICENSE permission. Part of the PairIP bypass.",
    default = true,
) {
    compatibleWith(CUBE_SOLVER)

    execute {
        document("AndroidManifest.xml").use { document ->
            // Change app class from PairIP Application to App
            val applicationElement =
                document.getElementsByTagName("application").item(0) as Element
            applicationElement.setAttribute(
                "android:name",
                "com.jeffprod.cubesolver.App",
            )

            // Remove LicenseActivity
            val activities = document.getElementsByTagName("activity")
            for (i in activities.length - 1 downTo 0) {
                val activity = activities.item(i) as Element
                if (activity.getAttribute("android:name").contains("LicenseActivity")) {
                    activity.parentNode.removeChild(activity)
                }
            }

            // Remove CHECK_LICENSE permission
            val permissions = document.getElementsByTagName("uses-permission")
            for (i in permissions.length - 1 downTo 0) {
                val permission = permissions.item(i) as Element
                if (permission.getAttribute("android:name").contains("CHECK_LICENSE")) {
                    permission.parentNode.removeChild(permission)
                }
            }
        }
    }
}
