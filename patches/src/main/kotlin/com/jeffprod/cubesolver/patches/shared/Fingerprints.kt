package com.jeffprod.cubesolver.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Fingerprint for k93.appReady().
 *
 * Cube Solver is a WebView-based app: the game logic is in a Svelte/Vite
 * JavaScript bundle (assets/www/build/bundle.js), and the Android side
 * provides a JavaScript bridge class `k93` (registered as
 * `window.Android` via `WebView.addJavascriptInterface`).
 *
 * The JS code calls `Android.appReady()` when the WebView finishes
 * loading. This is the perfect hook point because at that moment the
 * WebView is fully initialised and `evaluateJavascript()` calls will
 * succeed.
 *
 * Smali signature (from decompiled APK 5.0.3):
 *   .method public final appReady()V
 *     .locals 5
 *     .annotation runtime Landroid/webkit/JavascriptInterface;
 *     .end annotation
 *     iget-object p0, p0, Lk93;->a:Ljava/lang/ref/WeakReference;
 *     ...
 *
 * The k93 class holds a WeakReference<MainActivity> in field `a`.
 * MainActivity has a public final method `j(String, String)` that
 * calls `webView.evaluateJavascript("window.localStorage.setItem(key, value)", null)`.
 *
 * The JS-side `isPaidUser()` function checks:
 *   window.localStorage.getItem("ulcsall") === "ok"
 *
 * When isPaidUser() returns true, the JS code:
 *   - Skips ALL rewarded ads (showRA returns immediately)
 *   - Skips ALL interstitial ads (showAdInterstitielle returns immediately)
 *   - Skips GDPR consent popup (showRGPD returns immediately)
 *   - Unlocks ALL designs including kilominx (isKilominxUnlocked checks
 *     isPaidUser first, then checks localStorage["ulk"])
 *
 * So calling MainActivity.j("ulcsall", "ok") from appReady() achieves
 * both "no ads" and "unlock all designs" in one shot.
 */
object AppReadyFingerprint : Fingerprint(
    definingClass = "Lk93;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Ljava/lang/ref/Reference;",
            name = "get",
            returnType = "Ljava/lang/Object;",
        ),
    )
)

/**
 * Fingerprint for k93.showRA(String designKey).
 *
 * Called by JS when the user taps "Watch ad to unlock" on a locked design
 * (e.g., kilominx). The parameter is the localStorage key for that design
 * (e.g., "ulk" for kilominx). The original method:
 *   1. Logs "rewarded_ad_prompt" to Firebase Analytics
 *   2. Gets MainActivity from k93.a (WeakReference)
 *   3. Posts a jl1 runnable with (MainActivity, designKey, case=4)
 *   4. The jl1 runnable shows the rewarded ad via AdMob/AppLovin
 *   5. After the ad is watched, y7 callback calls MainActivity.j(designKey, "ok")
 *      which sets localStorage[designKey] = "ok" and unlocks the design
 *
 * We hook this to call MainActivity.j(p1, "ok") directly — skipping the
 * ad entirely but still granting the reward.
 */
object ShowRAFingerprint : Fingerprint(
    definingClass = "Lk93;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(
        methodCall(
            definingClass = "Ljl1;",
            name = "<init>",
        ),
    )
)

/**
 * Fingerprint for k93.showAdInterstitielle().
 *
 * Called by JS to show a full-screen interstitial ad. We hook this to
 * be a no-op (return-void) so interstitial ads never show.
 */
object ShowAdInterstitielleFingerprint : Fingerprint(
    definingClass = "Lk93;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lil1;",
            name = "<init>",
        ),
    )
)

/**
 * Fingerprint for k93.loadRewardedAd().
 *
 * Called by JS to preload a rewarded ad before showing it. We hook this
 * to be a no-op (return-void) so rewarded ads are never preloaded.
 */
object LoadRewardedAdFingerprint : Fingerprint(
    definingClass = "Lk93;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lil1;",
            name = "<init>",
        ),
    )
)

/**
 * Fingerprint for PairIP Application.attachBaseContext(Context).
 *
 * This is the MAIN PairIP entry point. The AndroidManifest declares
 * android:name="com.pairip.application.Application" which extends
 * com.jeffprod.cubesolver.App. The attachBaseContext method is called
 * VERY early in app startup (before onCreate) and runs three PairIP
 * protection layers:
 *
 *   1. VMRunner.setContext(context) — needed for the PairIP VM to work
 *   2. SignatureCheck.verifyIntegrity(context) — CRASHES on patched APK
 *   3. LicenseClient.checkLicense(context) — blocks on sideloaded APK
 *   4. super.attachBaseContext(context) — needed for app to function
 *
 * Smali signature (from decompiled APK 5.0.3):
 *   .method protected attachBaseContext(Landroid/content/Context;)V
 *     .locals 0
 *     invoke-static {p1}, Lcom/pairip/VMRunner;->setContext(Landroid/content/Context;)V
 *     invoke-static {p1}, Lcom/pairip/SignatureCheck;->verifyIntegrity(Landroid/content/Context;)V
 *     invoke-static {p1}, Lcom/pairip/licensecheck/LicenseClient;->checkLicense(Landroid/content/Context;)V
 *     invoke-super {p0, p1}, Lcom/pairip/application/Application;->attachBaseContext(Landroid/content/Context;)V
 *     return-void
 *   .end method
 *
 * We hook this to:
 *   1. KEEP VMRunner.setContext (the VM is needed for onCreate/onDestroy)
 *   2. SKIP SignatureCheck.verifyIntegrity (prevents crash)
 *   3. SKIP LicenseClient.checkLicense (prevents license block)
 *   4. KEEP super.attachBaseContext (needed for app to function)
 *
 * This is the "nuclear option" that bypasses ALL PairIP checks in one
 * hook, while keeping the VM functional.
 *
 * WHY THIS IS BETTER than patching SignatureCheck and LicenseClient
 * individually:
 * - One hook instead of two (simpler, more reliable)
 * - Even if SignatureCheck or LicenseClient are called from elsewhere
 *   (e.g., from the PairIP VM bytecode), they won't be reached because
 *   attachBaseContext is the only caller in the original code
 * - More robust against future updates that might add more checks
 */
object AttachBaseContextFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/application/Application;",
    accessFlags = listOf(AccessFlags.PROTECTED),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/pairip/SignatureCheck;",
            name = "verifyIntegrity",
        ),
        methodCall(
            definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
            name = "checkLicense",
        ),
    )
)
