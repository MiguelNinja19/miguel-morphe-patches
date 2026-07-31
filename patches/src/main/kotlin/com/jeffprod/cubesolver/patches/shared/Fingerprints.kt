package com.jeffprod.cubesolver.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Fingerprint for k93.appReady().
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
 * Fingerprint for k93.openPlayStore().
 *
 * Called by JS to open Play Store. We no-op this.
 */
object OpenPlayStoreFingerprint : Fingerprint(
    definingClass = "Lk93;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Ljl1;",
            name = "<init>",
        ),
    )
)

/**
 * Fingerprint for PairIP StartupLauncher.launch().
 *
 * Called from App.<clinit>. Starts the PairIP VM by calling
 * VMRunner.invoke() which loads libpairipcore.so and executes
 * the VM bytecode from asset "PAvdaIa2xHwL2BZt".
 *
 * The native library (libpairipcore.so) contains "android.intent.action.VIEW"
 * and can redirect to Play Store via JNI, bypassing all Java patches.
 * By no-oping launch(), the native library is never loaded and cannot
 * do integrity checks or Play Store redirects.
 *
 * Smali: .method public static declared-synchronized launch()V
 */
object StartupLauncherFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/StartupLauncher;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/pairip/VMRunner;",
            name = "invoke",
        ),
    )
)

/**
 * Fingerprint for MainActivity.onCreate(Bundle).
 *
 * The original onCreate is routed through PairIP VM reflection
 * (aFGUz.pcKC.invoke). Since we disable the VM (StartupLauncher.launch
 * is no-op'd), aFGUz.pcKC would be null and cause a crash.
 *
 * We replace onCreate with a direct WebView setup that doesn't
 * depend on the VM.
 *
 * Smali: .method public final onCreate(Landroid/os/Bundle;)V
 *   sget-object v0, Lcom/unity3d/ads/datastore/Vq/aFGUz;->pcKC:Ljava/lang/reflect/Method;
 *   invoke-virtual {v0, v3, v1}, Ljava/lang/reflect/Method;->invoke(...)
 */
object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/jeffprod/cubesolver/MainActivity;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(
            definingClass = "Ljava/lang/reflect/Method;",
            name = "invoke",
        ),
    )
)
