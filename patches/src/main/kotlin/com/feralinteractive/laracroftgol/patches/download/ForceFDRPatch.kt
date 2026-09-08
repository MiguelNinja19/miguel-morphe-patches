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
 *   shows the fatal "Download Failed" dialog (resources 2131690461 /
 *   2131690462, class framework/K case 2 on the UI thread). There is
 *   no retry path that ever succeeds and no fallback: the game is
 *   stuck. That is the modding "detection".
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
 *   In the asset-pack handler's <init> (the method that invokes
 *   nativeAvailableAssets(String[])), we replace the array that is
 *   about to be passed with a zero-length String[]:
 *
 *     const/4 v0, 0x0
 *     new-array vN, v0, [Ljava/lang/String;   (register holding
 *                                              the argument is
 *                                              discovered dynamically)
 *
 *   The native RemoteResourceManager then sees no Play asset packs,
 *   its ODR availability gate (is pack "data_bundle_*" present?)
 *   fails, and DetermineDownloadType() falls back to FDR - the
 *   downloader that works on re-signed APKs.
 *
 * HOOK B - loadBundle() returns true without touching Play Core.
 *   The method that invokes AssetPackManager.fetch() gets
 *   "const/4 v0, 0x1; return v0" prepended. This keeps the startup
 *   flow on the "download started" path (no fatal dialog from
 *   framework/K case 2) while never calling the Play Core fetch that
 *   cannot work on a re-signed APK anyway.
 *
 * The pair of hooks gives the native FDR downloader full control of
 * the data download. If Feral's FDR servers also serve the Android
 * packs (they do for the desktop ports), the game downloads ~1.3 GB
 * from fdr.feralinteractive.com and boots normally.
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
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.feralinteractive.laracroftgol.patches.shared.LARA_CROFT_GOL
import java.util.logging.Logger

private const val ASSET_PACKS_HANDLER_IFACE = "Lcom/feralinteractive/nativeframework/FeralAssetPacksHandlerInterface;"
private const val FETCH_METHOD = "fetch"

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
        // Find the method that invokes
        // FeralAssetPacksHandlerInterface.nativeAvailableAssets([Ljava/lang/String;)
        // (the handler's <init> in 1.2.6RC1). Immediately before the
        // invoke-static, replace the argument register with a
        // zero-length String[].
        // ==============================================================
        var hookADone = false
        classDefForEach { classDef ->
            if (hookADone) return@classDefForEach
            for (method in classDef.methods) {
                val impl = method.implementation ?: continue
                val insns = impl.instructions.toList()
                for ((idx, insn) in insns.withIndex()) {
                    if (insn !is ReferenceInstruction) continue
                    val ref = insn.reference
                    if (ref !is MethodReference) continue
                    if (ref.definingClass != ASSET_PACKS_HANDLER_IFACE || ref.name != "nativeAvailableAssets") continue
                    if (ref.parameterTypes.firstOrNull()?.toString() != "[Ljava/lang/String;") continue

                    // read the first argument register from the invoke
                    // (35c format: register C). dexlib2 exposes it via the
                    // instruction's registers array where available.
                    val invokeRegs = try {
                        val f = insn.javaClass.getMethod("getRegisters")
                        @Suppress("UNCHECKED_CAST")
                        (f.invoke(insn) as? Array<Any>)?.mapNotNull { r ->
                            try {
                                val g = r.javaClass.getMethod("getValue")
                                (g.invoke(r) as? Int)
                            } catch (e: Exception) { null }
                        }
                    } catch (e: Exception) { null }
                    val reg = invokeRegs?.firstOrNull()
                    if (reg == null) {
                        logger.warning("  nativeAvailableAssets call found but register not readable in ${classDef.type}")
                        continue
                    }

                    // scratch register: any local (non-parameter) register
                    // below the argument register. In 1.2.6RC1 <init> has
                    // 4 locals (v0-v3) and the argument is v6.
                    val localCount = impl.registerCount - method.parameterTypes.size - 1
                    val scratchCandidates = (0 until minOf(reg, localCount)).toList()
                    if (scratchCandidates.isEmpty()) {
                        logger.warning("  no scratch register available in ${classDef.type}->${method.name}")
                        continue
                    }
                    val scratch = "v${scratchCandidates.last()}"

                    val argReg = "v$reg"
                    method.addInstructions(idx, """
                        const/4 $scratch, 0x0
                        new-array $argReg, $scratch, [Ljava/lang/String;
                    """.trimIndent())
                    hookADone = true
                    logger.info("  HOOK A: empty asset-pack list injected into ${classDef.type}->${method.name} (arg reg $argReg, scratch $scratch)")
                    break
                }
                if (hookADone) break
            }
        }
        if (!hookADone) {
            logger.severe("HOOK A FAILED: no caller of FeralAssetPacksHandlerInterface.nativeAvailableAssets found")
        }

        // ==============================================================
        // HOOK B: loadBundle() -> true without Play Core fetch
        // ==============================================================
        // Find the method that matches loadBundle(String)Z (exactly one
        // String parameter, boolean return, calls AssetPackManager.fetch)
        // and rewrite it to return true immediately. The fetch path
        // (getPackLocation/fetch/Task listeners) is never reached, so the
        // startup flow takes the "download started" branch instead of
        // the fatal "Download Failed" dialog.
        //
        // NOTE: the generic framework message dispatcher
        // (framework/a.a(k, I, I, [Object)Z in 1.2.6RC1) ALSO calls fetch
        // and returns Z - the single-String-parameter filter is what
        // keeps us from patching it by mistake.
        // ==============================================================
        var hookBDone = false
        classDefForEach { classDef ->
            if (hookBDone) return@classDefForEach
            for (method in classDef.methods) {
                if (method.returnType != "Z") continue
                val params = method.parameterTypes.map { it.toString() }
                if (params.size != 1 || params[0] != "Ljava/lang/String;") continue
                val impl = method.implementation ?: continue
                val callsFetch = impl.instructions.any { insn ->
                    if (insn is ReferenceInstruction) {
                        val ref = insn.reference
                        ref is MethodReference && ref.name == FETCH_METHOD &&
                            ref.definingClass == "Lcom/google/android/play/core/assetpacks/AssetPackManager;"
                    } else false
                }
                if (!callsFetch) continue

                // need at least 1 local register for the return value
                val locals = impl.registerCount - method.parameterTypes.size - 1
                if (locals < 1) continue

                method.addInstructions(0, """
                    const/4 v0, 0x1
                    return v0
                """.trimIndent())
                hookBDone = true
                logger.info("  HOOK B: ${classDef.type}->${method.name}(Ljava/lang/String;)Z returns true (Play Core fetch skipped)")
                break
            }
        }
        if (!hookBDone) {
            logger.severe("HOOK B FAILED: no Z-returning caller of AssetPackManager.fetch found")
        }

        if (hookADone && hookBDone) {
            logger.info("Force FDR patch applied: empty pack list + fetch bypass OK")
        } else {
            logger.warning("Force FDR patch PARTIALLY applied (see failures above)")
        }
    }
}
