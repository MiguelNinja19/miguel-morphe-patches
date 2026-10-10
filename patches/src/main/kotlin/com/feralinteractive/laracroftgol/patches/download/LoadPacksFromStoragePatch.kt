/*
 * Load game data from device storage for Lara Croft and the Guardian of
 * Light (com.feralinteractive.laracroftgol_android).
 *
 * RESEARCH SUMMARY (v1.2.6RC1, deep follow-up to issue #4
 * "stuck on loading screen, not downloading data"):
 *
 * - The 1.3 GB of game data is NOT downloaded in runtime on Android: it
 *   ships inside the XAPK/App Bundle as an install-time split
 *   (data_core.apk, verified: contains assets/data_core.pack (STORED,
 *   1,325,089,984 bytes) + assets/data_core.pack.sig + Feral bundletool
 *   signatures). The Play Store installs base + data_core together, so
 *   the store version never downloads anything at first launch either.
 *
 * - FDR (Feral's desktop downloader) cannot replace it on Android:
 *   fdr.feralinteractive.com/data answers 404 "Requested pack not found"
 *   for every format (live-tested, including .zip?buildtype=goldmaster),
 *   and the game's FDR config (RemoteResources.xml) is an empty VFS.
 *   ForceFDRPatch therefore only breaks the working local path.
 *
 * - The ONLY data-presence check in the whole game is Play Core 2.3.0:
 *   framework/f.c() asks AssetPackManager.getPackLocation(name); the
 *   impl (class l) gates on SplitInstallInfoProvider (class
 *   internal/ad, method a()) which builds the set of pack names from
 *   the PackageManager's splitNames (fused.modules metadata is absent,
 *   so it falls back to installed splits). getPackageInfo is always
 *   called WITHOUT GET_SIGNATURES (flags 0/128): nothing in this path
 *   verifies the APK signature or talks to Google.
 *
 * - Morphe installs the patched BASE apk only; the original data_core
 *   split cannot be installed next to it (the Android installer
 *   requires identical signatures across splits). Result: ad.a() set is
 *   empty -> getPackLocation null -> f.d() -> FDR -> 404 -> infinite
 *   loading screen.
 *
 * PATCH STRATEGY (2 hooks, bytecode only, obfuscation-robust discovery,
 * graceful fallback when the user provides no data file):
 *
 * HOOK 1 - SplitInstallInfoProvider.a() (the method returning
 *   Ljava/util/Set; with no parameters that calls
 *   PackageManager.getPackageInfo): prepend a block that, when
 *   <external files dir>/packs/ exists and contains *.apk files,
 *   returns a LinkedHashSet of the file names with the ".apk" suffix
 *   stripped ("data_core.apk" -> "data_core"). The native gate in
 *   l.getPackLocation()/l.getAssetLocation() then believes the pack is
 *   installed (returns the singleton AssetPackLocation with
 *   packStorageMethod()==1, the APK-scan path). When the folder is
 *   missing/empty the original body still runs - zero behavior change.
 *
 * HOOK 2 - the asset-pack APK locator (the method with signature
 *   (Ljava/lang/String;)Ljava/util/List; that calls
 *   PackageManager.getPackageInfo - class bm, method s() in 1.2.6RC1;
 *   it is the list of APKs scanned by bm.d/ce.a for the pack entries):
 *   prepend a block that, when <external files dir>/packs/<name>.apk
 *   exists, returns a single-element list with that path. The Play Core
 *   ZIP scan then finds assets/<name>.pack and .pack.sig inside the
 *   copied file (STORED entries, direct offset+size read by
 *   nativeLoadAssetPackData - no signature check). When the file is
 *   missing the original body still runs.
 *
 * USER INSTRUCTIONS (also in the patch description):
 *   1. Download the full XAPK of the game (1.2.6RC1+, ~1.4 GB) from
 *      APKPure/APKCombo.
 *   2. Open it as a ZIP and extract ONLY data_core.apk (1.32 GB).
 *   3. Copy it to Android/data/com.feralinteractive.laracroftgol_android/
 *      files/packs/data_core.apk (create the "packs" folder; use a file
 *      manager with Android/data access, or USB/adb).
 *   4. Patch + install the base apk with Morphe as usual and play.
 *
 * Both hooks only patch methods that exist in every Play Core 2.3.0
 * build; targets are discovered by structure (return type + parameters
 * + PackageManager.getPackageInfo call + exactly one Context field),
 * never by obfuscated names.
 */

package com.feralinteractive.laracroftgol.patches.download

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.feralinteractive.laracroftgol.patches.shared.LARA_CROFT_GOL
import java.util.logging.Logger

private const val PACKAGE_MANAGER = "Landroid/content/pm/PackageManager;"
private const val CTX_TYPE = "Landroid/content/Context;"
private const val PACKS_DIR = "packs"

// True when insn invokes PackageManager.getPackageInfo(...)
private fun isGetPackageInfoCall(insn: Instruction): Boolean {
    if (insn !is ReferenceInstruction) return false
    val ref = insn.reference as? MethodReference ?: return false
    return ref.definingClass == PACKAGE_MANAGER && ref.name == "getPackageInfo"
}

@Suppress("unused")
val loadPacksFromStoragePatch = bytecodePatch(
    name = "Load game data from device storage",
    description = "Fixes the infinite loading screen after the license " +
        "bypass: the game's 1.3 GB data pack (data_core) normally ships " +
        "as a Play Asset Delivery split that cannot be installed next to " +
        "a re-signed APK, and Feral's FDR servers do not host the " +
        "Android data (404). This patch makes the game load the data " +
        "from a file YOU provide: extract data_core.apk from the full " +
        "XAPK and copy it to Android/data/com.feralinteractive." +
        "laracroftgol_android/files/packs/data_core.apk. With the file " +
        "in place the game boots the full data; without it this patch " +
        "changes nothing. Use together with 'Unlock full game (TBYB " +
        "bypass + license)'. Replaces the old 'Force FDR data " +
        "download' patch (FDR cannot deliver the Android pack).",
    default = true,
) {
    compatibleWith(LARA_CROFT_GOL)

    execute {
        val logger = Logger.getLogger("LaraGoL")

        // ==============================================================
        // Phase 1 (immutable scan): locate the two Play Core classes.
        //  - HOOK 1 target: the SplitInstallInfoProvider - the class
        //    with exactly one Context field and a no-parameter
        //    method returning Ljava/util/Set; that calls
        //    PackageManager.getPackageInfo (it reads splitNames).
        //  - HOOK 2 target: the asset-pack APK locator - the class
        //    with exactly one Context field and a single-String
        //    parameter method returning Ljava/util/List; that calls
        //    PackageManager.getPackageInfo (it lists the APKs to scan).
        // Both are Play Core 2.3.0 internals (internal/ad and bm in
        // 1.2.6RC1) and are found by structure, not by name.
        // ==============================================================
        var hook1Type: String? = null
        var hook1CtxField: String? = null
        var hook1Method: String? = null
        var hook2Type: String? = null
        var hook2CtxField: String? = null
        var hook2Method: String? = null

        classDefForEach { classDef ->
            val ctxFields = classDef.fields.filter { it.type.toString() == CTX_TYPE }
            if (ctxFields.size != 1) return@classDefForEach
            val ctxField = ctxFields[0].name

            if (hook1Type == null) {
                val m = classDef.methods.firstOrNull { m ->
                    m.returnType == "Ljava/util/Set;" &&
                        m.parameterTypes.isEmpty() &&
                        m.implementation?.instructions?.any { isGetPackageInfoCall(it) } == true
                }
                if (m != null) {
                    hook1Type = classDef.type
                    hook1CtxField = ctxField
                    hook1Method = m.name
                }
            }
            if (hook2Type == null) {
                val m = classDef.methods.firstOrNull { m ->
                    m.returnType == "Ljava/util/List;" &&
                        m.parameterTypes.size == 1 &&
                        m.parameterTypes[0].toString() == "Ljava/lang/String;" &&
                        m.implementation?.instructions?.any { isGetPackageInfoCall(it) } == true
                }
                if (m != null) {
                    hook2Type = classDef.type
                    hook2CtxField = ctxField
                    hook2Method = m.name
                }
            }
        }

        // ==============================================================
        // HOOK 1: SplitInstallInfoProvider set -> storage pack names
        // ==============================================================
        // Prepend to the ()Set method:
        //   dir = context.getExternalFilesDir(null)/"packs"
        //   names = dir.list()
        //   if (names == null || names.length == 0) -> original body
        //   set = LinkedHashSet()
        //   for (i = names.length-1; i >= 0; i--)
        //       if (names[i].endsWith(".apk"))
        //           set.add(names[i].replace(".apk", ""))
        //   return set
        // Register budget: v0 names[], v1 set, v2 index, v3 current,
        // v4/v5 scratch - 6 locals. ad.a() has 6 locals in 1.2.6RC1;
        // if a build has fewer, the hook is skipped (logged) and the
        // game behaves as unpatched.
        // ==============================================================
        var hook1Done = false
        val h1Type = hook1Type
        val h1Field = hook1CtxField
        val h1Name = hook1Method
        if (h1Type == null || h1Field == null || h1Name == null) {
            logger.severe(
                "HOOK 1 FAILED: SplitInstallInfoProvider (()Set + getPackageInfo) not found"
            )
        } else {
            val mutableClass = mutableClassDefBy(h1Type)
            val method = mutableClass.methods.firstOrNull { it.name == h1Name } ?: run {
                logger.severe("HOOK 1 FAILED: method $h1Name not found in $h1Type")
                null
            }
            if (method != null && method.implementation != null) {
                val impl = method.implementation!!
                val locals = impl.registerCount - method.parameterTypes.size - 1
                if (locals < 6) {
                    logger.severe(
                        "HOOK 1 FAILED: $h1Type->$h1Name has only $locals locals (need 6)"
                    )
                } else {
                    method.addInstructions(0, """
                        iget-object v4, p0, $h1Type->$h1Field:$CTX_TYPE
                        const/4 v5, 0x0
                        invoke-virtual {v4, v5}, Landroid/content/Context;->getExternalFilesDir(Ljava/lang/String;)Ljava/io/File;
                        move-result-object v4
                        if-eqz v4, :lara_h1_fallback
                        new-instance v5, Ljava/io/File;
                        const-string v0, "$PACKS_DIR"
                        invoke-direct {v5, v4, v0}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V
                        invoke-virtual {v5}, Ljava/io/File;->list()[Ljava/lang/String;
                        move-result-object v0
                        if-eqz v0, :lara_h1_fallback
                        new-instance v1, Ljava/util/LinkedHashSet;
                        invoke-direct {v1}, Ljava/util/LinkedHashSet;-><init>()V
                        array-length v2, v0
                        if-lez v2, :lara_h1_fallback
                        add-int/lit8 v2, v2, -0x1
                        :lara_h1_loop
                        if-gez v2, :lara_h1_done
                        aget-object v3, v0, v2
                        const-string v4, ".apk"
                        invoke-virtual {v3, v4}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z
                        move-result v4
                        if-eqz v4, :lara_h1_next
                        const-string v4, ".apk"
                        const-string v5, ""
                        invoke-virtual {v3, v4, v5}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;
                        move-result-object v4
                        invoke-interface {v1, v4}, Ljava/util/Set;->add(Ljava/lang/Object;)Z
                        :lara_h1_next
                        add-int/lit8 v2, v2, -0x1
                        goto :lara_h1_loop
                        :lara_h1_done
                        return-object v1
                        :lara_h1_fallback
                    """.trimIndent())
                    hook1Done = true
                    logger.info(
                        "  HOOK 1: $h1Type->$h1Name() returns storage pack names " +
                            "from getExternalFilesDir()/$PACKS_DIR (fallback: original body)"
                    )
                }
            }
        }

        // ==============================================================
        // HOOK 2: asset-pack APK list -> user-provided pack file
        // ==============================================================
        // Prepend to the (String)List method:
        //   f = new File(context.getExternalFilesDir(null)/"packs",
        //                name + ".apk")
        //   if (!f.exists()) -> original body
        //   return ArrayList of [f.getPath()]
        // Register budget: v0 ctx/scratch, v1 packs dir, v2 name/bool,
        // v3 pack file, v4 result list - 5 locals (bm.s() has 5 in
        // 1.2.6RC1). p1 (the pack name parameter) is never clobbered.
        // ==============================================================
        var hook2Done = false
        val h2Type = hook2Type
        val h2Field = hook2CtxField
        val h2Name = hook2Method
        if (h2Type == null || h2Field == null || h2Name == null) {
            logger.severe(
                "HOOK 2 FAILED: asset-pack APK locator ((String)List + getPackageInfo) not found"
            )
        } else {
            val mutableClass = mutableClassDefBy(h2Type)
            val method = mutableClass.methods.firstOrNull {
                it.name == h2Name &&
                    it.returnType == "Ljava/util/List;" &&
                    it.parameterTypes.size == 1
            } ?: run {
                logger.severe("HOOK 2 FAILED: method $h2Name(String)List not found in $h2Type")
                null
            }
            if (method != null && method.implementation != null) {
                val impl = method.implementation!!
                val locals = impl.registerCount - method.parameterTypes.size - 1
                if (locals < 5) {
                    logger.severe(
                        "HOOK 2 FAILED: $h2Type->$h2Name has only $locals locals (need 5)"
                    )
                } else {
                    method.addInstructions(0, """
                        iget-object v0, p0, $h2Type->$h2Field:$CTX_TYPE
                        const/4 v1, 0x0
                        invoke-virtual {v0, v1}, Landroid/content/Context;->getExternalFilesDir(Ljava/lang/String;)Ljava/io/File;
                        move-result-object v0
                        if-eqz v0, :lara_h2_fallback
                        new-instance v1, Ljava/io/File;
                        const-string v2, "$PACKS_DIR"
                        invoke-direct {v1, v0, v2}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V
                        new-instance v2, Ljava/lang/StringBuilder;
                        invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V
                        invoke-virtual {v2, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                        const-string v3, ".apk"
                        invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
                        invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                        move-result-object v2
                        new-instance v3, Ljava/io/File;
                        invoke-direct {v3, v1, v2}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V
                        invoke-virtual {v3}, Ljava/io/File;->exists()Z
                        move-result v2
                        if-eqz v2, :lara_h2_fallback
                        new-instance v4, Ljava/util/ArrayList;
                        invoke-direct {v4}, Ljava/util/ArrayList;-><init>()V
                        invoke-virtual {v3}, Ljava/io/File;->getPath()Ljava/lang/String;
                        move-result-object v2
                        invoke-interface {v4, v2}, Ljava/util/List;->add(Ljava/lang/Object;)Z
                        return-object v4
                        :lara_h2_fallback
                    """.trimIndent())
                    hook2Done = true
                    logger.info(
                        "  HOOK 2: $h2Type->$h2Name(String)List serves " +
                            "getExternalFilesDir()/$PACKS_DIR/<name>.apk to the pack scan " +
                            "(fallback: original body)"
                    )
                }
            }
        }

        if (hook1Done && hook2Done) {
            logger.info(
                "Load packs from storage applied: put data_core.apk in " +
                    "Android/data/com.feralinteractive.laracroftgol_android/files/$PACKS_DIR/"
            )
        } else {
            logger.warning("Load packs from storage PARTIALLY applied (see failures above)")
        }
    }
}
