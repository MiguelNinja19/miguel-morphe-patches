/*
 * Bypass PairIP patch for Cube Solver.
 *
 * ROOT CAUSE (found after deep research using RKPairip, Solaree/pairipcore,
 * MatrixEditor/pairipcore-vm, PlatinMods, and SystemWeakness articles):
 *
 * PairIP's native library (libpairipcore.so) contains the string
 * "android.intent.action.VIEW" and can create Intents via JNI to
 * redirect to the Play Store DIRECTLY FROM NATIVE CODE, bypassing
 * all Java-level patches.
 *
 * Previous approaches that didn't work:
 * 1. No-op SignatureCheck.verifyIntegrity() — native code also checks
 * 2. No-op LicenseClient.checkLicense() — native code also checks
 * 3. Change manifest Application class — native code still runs via VM
 * 4. No-op k93.openPlayStore() — native code bypasses Java entirely
 *
 * THE FIX (VM bypass + manifest modification + onCreate replacement):
 *
 *   PART 1: No-op StartupLauncher.launch() to prevent the PairIP VM
 *   (libpairipcore.so) from starting. This prevents ALL native integrity
 *   checks and Play Store redirects from native code.
 *
 *   PART 2: Replace MainActivity.onCreate with a direct WebView setup
 *   that doesn't use the PairIP VM. The original onCreate was routed
 *   through PairIP VM reflection (aFGUz). Our replacement creates the
 *   WebView, configures it, registers the k93 JS bridge as "Android",
 *   and loads the game URL directly — no VM needed.
 *
 *   PART 3: Change AndroidManifest.xml to use com.jeffprod.cubesolver.App
 *   instead of com.pairip.application.Application.
 *
 *   PART 4: Remove LicenseActivity and CHECK_LICENSE from manifest.
 *
 *   PART 5: No-op k93.openPlayStore() to prevent JS-triggered redirects.
 *
 * This approach is inspired by RKPairip (github.com/TechnoIndian/RKPairip)
 * which removes ALL pairip code and restores method bodies. We can't do
 * full string restoration in a Morphe patch, but we CAN replace the one
 * critical method (onCreate) that the VM provides, making the VM unnecessary.
 *
 * This patch is REQUIRED for all other Cube Solver patches to work.
 */

package com.jeffprod.cubesolver.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.jeffprod.cubesolver.patches.shared.CUBE_SOLVER
import com.jeffprod.cubesolver.patches.shared.OpenPlayStoreFingerprint
import com.jeffprod.cubesolver.patches.shared.StartupLauncherFingerprint
import com.jeffprod.cubesolver.patches.shared.MainActivityOnCreateFingerprint
import org.w3c.dom.Element

@Suppress("unused")
val bypassPairIPPatch = bytecodePatch(
    name = "Bypass PairIP integrity check",
    description = "Completely bypasses Google Play's PairIP by (1) " +
        "disabling the PairIP VM (prevents native integrity checks and " +
        "Play Store redirects from libpairipcore.so), (2) replacing " +
        "MainActivity.onCreate with direct WebView setup (no VM needed), " +
        "(3) changing the app class in manifest to skip PairIP " +
        "Application, (4) removing LicenseActivity and CHECK_LICENSE, " +
        "and (5) no-oping openPlayStore. The native library " +
        "libpairipcore.so is never loaded, so it can't do JNI-based " +
        "redirects. REQUIRED for all other patches.",
    default = true,
) {
    compatibleWith(CUBE_SOLVER)

    execute {
        // ============================================================
        // PART 1: Manifest modifications
        // ============================================================
        document("AndroidManifest.xml").use { document ->
            val applicationElement =
                document.getElementsByTagName("application").item(0) as Element
            applicationElement.setAttribute(
                "android:name",
                "com.jeffprod.cubesolver.App",
            )

            val activities = document.getElementsByTagName("activity")
            for (i in activities.length - 1 downTo 0) {
                val activity = activities.item(i) as Element
                if (activity.getAttribute("android:name").contains("LicenseActivity")) {
                    activity.parentNode.removeChild(activity)
                }
            }

            val permissions = document.getElementsByTagName("uses-permission")
            for (i in permissions.length - 1 downTo 0) {
                val permission = permissions.item(i) as Element
                if (permission.getAttribute("android:name").contains("CHECK_LICENSE")) {
                    permission.parentNode.removeChild(permission)
                }
            }
        }

        // ============================================================
        // PART 2: No-op StartupLauncher.launch() — prevent VM from starting
        // ============================================================
        // This prevents libpairipcore.so from loading. The native library
        // has "android.intent.action.VIEW" and can redirect to Play Store
        // via JNI, bypassing all Java patches. By not starting the VM,
        // the native code never runs.
        // ============================================================
        StartupLauncherFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())

        // ============================================================
        // PART 3: Replace MainActivity.onCreate with direct WebView setup
        // ============================================================
        // The original onCreate uses PairIP VM reflection (aFGUz.pcKC).
        // Since we disabled the VM (PART 2), aFGUz.pcKC would be null.
        // We replace onCreate with a direct implementation that creates
        // the WebView, enables JS, registers the k93 JS bridge, and
        // loads the game URL — no VM needed.
        //
        // Register usage:
        //   p0 = this (MainActivity)
        //   p1 = savedInstanceState (Bundle)
        //   v0 = WebView
        //   v1 = WebSettings
        //   v2 = 1 (true)
        //   v3 = k93 JS bridge
        //   v4 = "Android" string
        //   v5 = URL string
        // ============================================================
        MainActivityOnCreateFingerprint.method.addInstructions(0, """
            # Call super.onCreate(bundle)
            invoke-super {p0, p1}, Landroidx/activity/ComponentActivity;->onCreate(Landroid/os/Bundle;)V

            # Create WebView and store in field b
            new-instance v0, Landroid/webkit/WebView;
            invoke-direct {v0, p0}, Landroid/webkit/WebView;-><init>(Landroid/content/Context;)V
            iput-object v0, p0, Lcom/jeffprod/cubesolver/MainActivity;->b:Landroid/webkit/WebView;

            # Enable JavaScript and DOM storage
            invoke-virtual {v0}, Landroid/webkit/WebView;->getSettings()Landroid/webkit/WebSettings;
            move-result-object v1
            const/4 v2, 0x1
            invoke-virtual {v1, v2}, Landroid/webkit/WebSettings;->setJavaScriptEnabled(Z)V
            invoke-virtual {v1, v2}, Landroid/webkit/WebSettings;->setDomStorageEnabled(Z)V

            # Create k93 JS bridge and register as "Android"
            new-instance v3, Lk93;
            invoke-direct {v3, p0}, Lk93;-><init>(Lcom/jeffprod/cubesolver/MainActivity;)V
            const-string v4, "Android"
            invoke-virtual {v0, v3, v4}, Landroid/webkit/WebView;->addJavascriptInterface(Ljava/lang/Object;Ljava/lang/String;)V

            # Load the game URL
            const-string v5, "file:///android_asset/www/index.html"
            invoke-virtual {v0, v5}, Landroid/webkit/WebView;->loadUrl(Ljava/lang/String;)V

            # Set WebView as content view
            invoke-virtual {p0, v0}, Lcom/jeffprod/cubesolver/MainActivity;->setContentView(Landroid/view/View;)V

            return-void
        """.trimIndent())

        // ============================================================
        // PART 4: No-op k93.openPlayStore()
        // ============================================================
        OpenPlayStoreFingerprint.method.addInstructions(0, """
            return-void
        """.trimIndent())
    }
}
