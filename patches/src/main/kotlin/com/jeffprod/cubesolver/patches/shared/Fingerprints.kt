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
 *
 * NOTE on PairIP:
 *   The app uses Google Play's PairIP (Play Automatic Integrity Protection).
 *   The com.pairip.* package and libpairipcore.so are present, and
 *   MainActivity.onCreate / App.onCreate are routed through PairIP VM
 *   reflection (com.unity3d.ads.datastore.Vq.aFGUz). HOWEVER, the
 *   VmDecryptor.decrypt() method is a no-op stub that returns the input
 *   bytes unchanged, so all method bodies are in plain smali. The k93
 *   class (which is the JS bridge) is NOT behind PairIP VM — its methods
 *   are direct Java/smali and can be patched freely.
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
 * Smali signature (from decompiled APK 5.0.3):
 *   .method public final showRA(Ljava/lang/String;)V
 *     .locals 7
 *     .annotation runtime Landroid/webkit/JavascriptInterface;
 *     .end annotation
 *     ...
 *     iget-object p0, p0, Lk93;->a:Ljava/lang/ref/WeakReference;
 *     invoke-virtual {p0}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
 *     move-result-object p0
 *     check-cast p0, Lcom/jeffprod/cubesolver/MainActivity;
 *     ...
 *     new-instance v1, Ljl1;
 *     const/4 v2, 0x4
 *     invoke-direct {v1, p0, p1, v2}, Ljl1;-><init>(Lcom/jeffprod/cubesolver/MainActivity;Ljava/lang/String;I)V
 *
 * We hook this to call MainActivity.j(p1, "ok") directly — skipping the
 * ad entirely but still granting the reward. The design is unlocked
 * instantly without any ad being shown.
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
 * Called by JS to show a full-screen interstitial ad (e.g., between
 * screen transitions). The original method gets MainActivity from
 * k93.a and posts an il1 runnable with case=9, which calls the
 * AdMob/AppLovin interstitial ad loader.
 *
 * Smali signature (from decompiled APK 5.0.3):
 *   .method public final showAdInterstitielle()V
 *     .locals 3
 *     .annotation runtime Landroid/webkit/JavascriptInterface;
 *     .end annotation
 *     iget-object p0, p0, Lk93;->a:Ljava/lang/ref/WeakReference;
 *     ...
 *     new-instance v1, Lil1;
 *     const/16 v2, 0x9
 *     invoke-direct {v1, p0, v2}, Lil1;-><init>(Lcom/jeffprod/cubesolver/MainActivity;I)V
 *
 * We hook this to be a no-op (return-void) so interstitial ads never show.
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
 * Called by JS to preload a rewarded ad before showing it. The original
 * method gets MainActivity from k93.a and posts an il1 runnable with
 * case=12 (0xc), which calls the AdMob/AppLovin rewarded ad loader.
 *
 * Smali signature (from decompiled APK 5.0.3):
 *   .method public final loadRewardedAd()V
 *     .locals 3
 *     .annotation runtime Landroid/webkit/JavascriptInterface;
 *     .end annotation
 *     iget-object p0, p0, Lk93;->a:Ljava/lang/ref/WeakReference;
 *     ...
 *     new-instance v1, Lil1;
 *     const/16 v2, 0xc
 *     invoke-direct {v1, p0, v2}, Lil1;-><init>(Lcom/jeffprod/cubesolver/MainActivity;I)V
 *
 * We hook this to be a no-op (return-void) so rewarded ads are never
 * preloaded, saving bandwidth and preventing the ad SDK from initialising.
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
