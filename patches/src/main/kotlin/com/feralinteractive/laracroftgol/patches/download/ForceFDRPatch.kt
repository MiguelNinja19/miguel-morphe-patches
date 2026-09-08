/*
 * Force FDR data download for Lara Croft and the Guardian of Light
 * (com.feralinteractive.laracroftgol_android).
 *
 * RESEARCH SUMMARY (v1.2.6RC1, follow-up to issue #4 feedback):
 *
 * - The "complex Feral protection" reported by users is NOT PairIP and
 *   NOT only LVL: after the license patch lets the game boot, a
 *   re-signed APK dies at the Feral splash with the dialog
 *   "Download Failed - Failed to download data from Google Play Store."
 *
 * - Root cause (fully traced in smali + native disasm): the game ships
 *   its 1.3 GB of game data as a Play Asset Delivery pack
 *   ("data_core", plus optional data_support1/2/3 and data_bundle_*
 *   DLC packs). At startup, activity f0 reads the pack list from
 *   resources, reports it to the native game via
 *   FeralAssetPacksHandlerInterface.nativeAvailableAssets(String[])
 *   and immediately calls handler.loadBundle("data_core").
 *
 * - loadBundle() calls AssetPackManager.getPackLocation(name) (Play
 *   Core). On an app that was not installed by the Play Store (or was
 *   re-signed) this returns null, so loadBundle returns false and f0
 *   shows the fatal "Download Failed" dialog. There is no retry path
 *   that ever succeeds and no fallback: the game is stuck. That is the
 *   modding "detection".
 *
 * - The native game (libLaraCroftGoL.so) contains a COMPLETE
 *   alternative downloader, "FDR" (Feral Download Resource,
 *   cferalremoteresourcemanager.cpp): the decision function prints
 *   "Download Type Determined as FDR/IAP/ODR/TEST" and defaults to
 *   FDR when Play asset packs are unavailable. FDR downloads from
 *   Feral's own servers (config key "Feral.FeralFDRDataURL" =
 *   https://fdr.feralinteractive.com/data - endpoint is live) using
 *   the "FERAL MANIFEST v1" format. Console commands
 *   "RemoteResourcesForceFDR" / "RemoteResourcesForceFDRforIAP"
 *   exist in every Feral port and force this path on desktop; on
 *   Android the ODR path is chosen because the pack list is always
 *   reported as available.
 *
 * PATCH STRATEGY (all bytecode, obfuscation-robust discovery):
 *
 * HOOK A - Report an EMPTY pack list to the native game.
 *   In the asset-pack handler (the class whose method invokes
 *   nativeAvailableAssets(String[]) - the handler's <init> in
 *   1.2.6RC1), we replace the array that is about to be passed with
 *   a zero-length String[]:
 *
 *     const/4 v0, 0x0
 *     new-array vN, v0, [Ljava/lang/String;   (argument register is
 *                                              discovered dynamically
 *                                              from the invoke format)
 *
 *   The native RemoteResourceManager then sees no Play asset packs,
 *   its ODR availability gate fails, and DetermineDownloadType()
 *   falls back to FDR - the downloader that works on re-signed APKs.
 *
 * HOOK B - loadBundle() returns true without touching Play Core.
 *   The method that invokes AssetPackManager.fetch() gets
 *   "const/4 v0, 0x1; return v0" prepended. This keeps the startup
 *   flow on the "download started" path (no fatal dialog) while never
 *   calling the Play Core fetch that cannot work on a re-signed APK
 *   anyway.
 *
 * IMPLEMENTATION NOTE (v2.1): classDefForEach() yields read-only
 * ClassDef/Method objects - addInstructions() only exists on the
 * MUTABLE types. Both hooks therefore run in two phases: a scan
 * phase over the immutable classes to locate the target class by
 * signature (works on any obfuscation), then a patch phase through
 * mutableClassDefBy(type) whose methods are MutableMethod. The
 * invoke argument register is read directly from the dexlib2
 * instruction format (Instruction35c.registerC / Instruction3rc
 * .startRegister) instead of reflection.
 *
 * TESTING NOTES (for whoever applies this first):
 *   - Watch logcat for "DetermineDownloadType" / "Downloading via
 *     FDR" / "FDR Error" lines from cferalremoteresourcemanager.cpp.
 *   - If the splash shows an endless download spinner instead of the
 *     old dialog, FDR was selected but the request failed: capture
 *     the FDR log lines (they contain the pack name and error code)
 *     so the request format can be reverse-engineered next.
 *   - If "Download Type Determined as ODR" still appears, the empty
 *     list hook missed (pack list read from a different source) -
 *     report the logcat too.
 */

package com.feralinteractive.laracroftgol.patches.download

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.feralinteractive.laracroftgol.patches.shared.LARA_CROFT_GOL
import java.util.logging.Logger

private const val ASSET_PACKS_HANDLER_IFACE =
    "Lcom/feralinteractive/nativeframework/FeralAssetPacksHandlerInterface;"
private const val ASSET_PACK_MANAGER_CLASS =
    "Lcom/google/android/play/core/assetpacks/AssetPackManager;"
private const val FETCH_METHOD = "fetch"

// True when insn invokes
// FeralAssetPacksHandlerInterface.nativeAvailableAssets([Ljava/lang/String;)
private fun isAvailableAssetsCall(insn: Instruction): Boolean {
    if (insn !is ReferenceInstruction) return false
    val ref = insn.reference as? MethodReference ?: return false
    return ref.definingClass == ASSET_PACKS_HANDLER_IFACE &&
        ref.name == "nativeAvailableAssets" &&
        ref.parameterTypes.firstOrNull()?.toString() == "[Ljava/lang/String;"
}

// True when insn invokes AssetPackManager.fetch(...)
private fun isAssetPackFetchCall(insn: Instruction): Boolean {
    if (insn !is ReferenceInstruction) return false
    val ref = insn.reference as? MethodReference ?: return false
    return ref.name == FETCH_METHOD && ref.definingClass == ASSET_PACK_MANAGER_CLASS
}

@Suppress("unused")
val forceFdrDownloadPatch = bytecodePatch(
    name = "Force FDR data download (bypass Play asset delivery)",
    description = "Fixes the 'Download Failed - Failed to download data " +
        "from Google Play Store' dialog that blocks re-signed APKs at " +
        "the splash screen. The game's 1.3 GB data pack is normally " +
        "fetched through Play Asset Delivery, which only works for " +
        "Play-Store-installed apps. This patch makes the Java layer " +
        "report no available Play asset packs and skip the Play Core " +
        "fetch, so the native game uses its built-in FDR downloader " +
        "(Feral's own servers, fdr.feralinteractive.com) instead - " +
        "the same fallback every Feral port has. Use together with " +
        "'Unlock full game (TBYB bypass + license)'. EXPERIMENTAL: " +
        "if the FDR servers refuse the Android pack, the game will " +
        "stay on the download screen (check logcat for " +
        "'DetermineDownloadType' / 'FDR' lines and report them).",
    default = false,
) {
    compatibleWith(LARA_CROFT_GOL)

    execute {
        val logger = Logger.getLogger("LaraGoL")

        // ==============================================================
        // HOOK A: nativeAvailableAssets() receives an empty array
        // ==============================================================
        // Phase 1 (immutable scan): locate the class that has a
        // method invoking
        // FeralAssetPacksHandlerInterface.nativeAvailableAssets(String[]).
        // Phase 2 (mutable patch): in that class, find the method +
        // the exact invoke index, read the argument register from the
        // invoke instruction format and inject the two instructions
        // right before the invoke.
        // ==============================================================
        var handlerClassType: String? = null
        classDefForEach { classDef ->
            if (handlerClassType != null) return@classDefForEach
            val hasCall = classDef.methods.any { m ->
                m.implementation?.instructions?.any { insn ->
                    isAvailableAssetsCall(insn)
                } == true
            }
            if (hasCall) handlerClassType = classDef.type
        }

        var hookADone = false
        if (handlerClassType == null) {
            logger.severe(
                "HOOK A FAILED: no caller of " +
                    "FeralAssetPacksHandlerInterface.nativeAvailableAssets found"
            )
        } else {
            val mutableHandler = mutableClassDefBy(handlerClassType!!)
            for (method in mutableHandler.methods) {
                val impl = method.implementation ?: continue
                val insns = impl.instructions.toList()
                for ((idx, insn) in insns.withIndex()) {
                    if (!isAvailableAssetsCall(insn)) continue

                    // Argument register of the invoke:
                    //   35c (invoke-static {vC}): first argument is C.
                    //   3rc (invoke-static/range {vCCCC..}): range start.
                    val reg = when (insn) {
                        is Instruction35c -> insn.registerC
                        is Instruction3rc -> insn.startRegister
                        else -> null
                    }
                    if (reg == null) {
                        logger.warning(
                            "  nativeAvailableAssets call found but argument " +
                                "register not readable in ${mutableHandler.type}"
                        )
                        continue
                    }

                    // scratch register: any local (non-parameter) register
                    // below the argument register. In 1.2.6RC1 <init> has
                    // 4 locals (v0-v3) and the argument is v6.
                    val localCount = impl.registerCount - method.parameterTypes.size - 1
                    val scratchCandidates = (0 until minOf(reg, localCount))
                    if (scratchCandidates.isEmpty()) {
                        logger.warning(
                            "  no scratch register available in " +
                                "${mutableHandler.type}->${method.name}"
                        )
                        continue
                    }
                    val scratch = "v${scratchCandidates.last()}"
                    val argReg = "v$reg"

                    method.addInstructions(idx, """
                        const/4 $scratch, 0x0
                        new-array $argReg, $scratch, [Ljava/lang/String;
                    """.trimIndent())
                    hookADone = true
                    logger.info(
                        "  HOOK A: empty asset-pack list injected into " +
                            "${mutableHandler.type}->${method.name} " +
                            "(arg reg $argReg, scratch $scratch)"
                    )
                    break
                }
                if (hookADone) break
            }
            if (!hookADone) {
                logger.severe("HOOK A FAILED: method not patchable in $handlerClassType")
            }
        }

        // ==============================================================
        // HOOK B: loadBundle() -> true without Play Core fetch
        // ==============================================================
        // Phase 1 (immutable scan): locate the class that has a
        // loadBundle(String)Z method (exactly one String parameter,
        // boolean return) calling AssetPackManager.fetch.
        // Phase 2 (mutable patch): rewrite that method to return true
        // immediately. The fetch path (getPackLocation/fetch/Task
        // listeners) is never reached, so the startup flow takes the
        // "download started" branch instead of the fatal dialog.
        //
        // NOTE: the generic framework message dispatcher
        // (framework/a.a(k, I, I, [Object)Z in 1.2.6RC1) ALSO calls
        // fetch and returns Z - the single-String-parameter filter is
        // what keeps us from patching it by mistake.
        // ==============================================================
        var loaderClassType: String? = null
        classDefForEach { classDef ->
            if (loaderClassType != null) return@classDefForEach
            val candidate = classDef.methods.any { m ->
                m.returnType == "Z" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0].toString() == "Ljava/lang/String;" &&
                    m.implementation?.instructions?.any { insn ->
                        isAssetPackFetchCall(insn)
                    } == true
            }
            if (candidate) loaderClassType = classDef.type
        }

        var hookBDone = false
        if (loaderClassType == null) {
            logger.severe(
                "HOOK B FAILED: no Z-returning caller of " +
                    "AssetPackManager.fetch found"
            )
        } else {
            val mutableLoader = mutableClassDefBy(loaderClassType!!)
            for (method in mutableLoader.methods) {
                if (method.returnType != "Z") continue
                val params = method.parameterTypes.map { it.toString() }
                if (params.size != 1 || params[0] != "Ljava/lang/String;") continue
                val impl = method.implementation ?: continue
                val callsFetch = impl.instructions.any { insn ->
                    isAssetPackFetchCall(insn)
                }
                if (!callsFetch) continue

                // v0 always exists on a method with an implementation
                // (it is either a local or the first parameter); we
                // return immediately after clobbering it, so no live
                // value is destroyed.
                if (impl.registerCount < 1) continue

                method.addInstructions(0, """
                    const/4 v0, 0x1
                    return v0
                """.trimIndent())
                hookBDone = true
                logger.info(
                    "  HOOK B: ${mutableLoader.type}->" +
                        "${method.name}(Ljava/lang/String;)Z returns true " +
                        "(Play Core fetch skipped)"
                )
                break
            }
            if (!hookBDone) {
                logger.severe("HOOK B FAILED: method not patchable in $loaderClassType")
            }
        }

        if (hookADone && hookBDone) {
            logger.info("Force FDR patch applied: empty pack list + fetch bypass OK")
        } else {
            logger.warning("Force FDR patch PARTIALLY applied (see failures above)")
        }
    }
}
