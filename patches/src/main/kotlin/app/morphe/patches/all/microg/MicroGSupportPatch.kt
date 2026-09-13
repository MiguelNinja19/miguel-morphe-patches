/*
 * MicroG integration patch (universal "all apps").
 *
 * Makes a patched app use ReVanced GmsCore (microG) instead of the
 * real Google Play Services, so Google sign-in / Play Games works on
 * the re-signed APK — which is exactly what unlocks ONLINE
 * MULTIPLAYER with your account (and account-owned tribes/skins) in
 * The Battle of Polytopia (issue #27).
 *
 * Ported from MorpheApp/morphe-patches via hoo-dles/morphe-patches
 * (patches/all/microg/MicroGSupportPatch.kt), adapted to this repo:
 *  - extension .mpe injected directly with extendWith (no shared
 *    extension patch)
 *  - local utils instead of the official patches library
 *  - runtime check class without the shared-library dependencies
 *
 * What it does:
 *  1. Rewrites every "com.google…" GMS permission/action/authority/
 *     package string in the app bytecode to "app.revanced.android.gms"
 *     so the GMS client binds to microG.
 *  2. Rewrites the manifest: c2dm → microG c2dm, adds a <queries>
 *     entry so the app can see microG on Android 11+, and adds the
 *     SPOOFED_PACKAGE_SIGNATURE / MICROG_PACKAGE_NAME meta-data that
 *     ReVanced GmsCore reads.
 *  3. Neutralizes "Google Play Services not available" style checks
 *     (return success early).
 *  4. Injects MicroGSupport.checkGmsCore into the main activity's
 *     onCreate (verifies microG is installed, new enough and
 *     whitelisted from battery optimizations).
 */

package app.morphe.patches.all.microg

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.util.returnEarlyOrNull
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.w3c.dom.Element

internal const val EXTENSION_CLASS_DESCRIPTOR = "Ldiozz/cubex/patches/extension/MicroGSupport;"
internal const val GMS_CORE_VENDOR_GROUP_ID = "app.revanced"

/**
 * Manifest changes required by GmsCore:
 *  - c2dm and other Google packages referenced by the manifest are
 *    rewritten to the microG equivalents
 *  - a <queries> entry makes microG visible on Android 11+
 *  - SPOOFED_PACKAGE_SIGNATURE + MICROG_PACKAGE_NAME meta-data is
 *    read by ReVanced GmsCore to know which app it is spoofing
 */
private val gmsCoreSupportResourcePatch = resourcePatch {
    dependsOn(microGMetadataPatch)

    execute {
        fun addSpoofingMetadata() {
            document("AndroidManifest.xml").use { document ->
                val applicationNode = document
                    .getElementsByTagName("application")
                    .item(0)

                fun adoptChild(tagName: String, block: Element.() -> Unit) {
                    val child = applicationNode.ownerDocument.createElement(tagName)
                    child.block()
                    applicationNode.appendChild(child)
                }

                // Spoof package signature (read by ReVanced GmsCore).
                adoptChild("meta-data") {
                    setAttribute("android:name", "$GMS_CORE_VENDOR_GROUP_ID.android.gms.SPOOFED_PACKAGE_SIGNATURE")
                    setAttribute("android:value", MicroGMetadata.signatureSHA1)
                }

                // GmsCore presence detection in extension.
                adoptChild("meta-data") {
                    setAttribute("android:name", "$GMS_CORE_VENDOR_GROUP_ID.MICROG_PACKAGE_NAME")
                    setAttribute("android:value", "$GMS_CORE_VENDOR_GROUP_ID.android.gms")
                }
            }
        }

        fun patchManifest() {
            val transformations = mutableMapOf(
                "com.google.android.c2dm" to "$GMS_CORE_VENDOR_GROUP_ID.android.c2dm",
                "com.google.android.libraries.photos.api.mars" to "$GMS_CORE_VENDOR_GROUP_ID.android.apps.photos.api.mars",
                "</queries>" to "<package android:name=\"$GMS_CORE_VENDOR_GROUP_ID.android.gms\"/></queries>",
            )

            val manifest = get("AndroidManifest.xml")
            manifest.writeText(
                transformations.entries.fold(manifest.readText()) { acc, (from, to) ->
                    acc.replace(from, to)
                }
            )
        }

        patchManifest()
        addSpoofingMetadata()
    }
}

@Suppress("unused")
val microGSupportPatch = bytecodePatch(
    name = "MicroG integration",
    description = "Allows the app to work without root by using microG " +
        "(ReVanced GmsCore) instead of Google Play Services. Use together " +
        "with the 'Spoof signature' patch so Google Play Games / Google " +
        "sign-in accepts the patched app (enables online multiplayer with " +
        "your account in Polytopia, for example).",
    default = false,
) {
    dependsOn(
        microGMetadataPatch,
        gmsCoreSupportResourcePatch,
    )

    // Inject the runtime extension (contains MicroGSupport).
    extendWith("extensions/extension.mpe")

    execute {
        // ── 1. Rewrite GMS strings in the bytecode ─────────────────
        fun transformStringReferences(transform: (str: String) -> String?) = classDefForEach { classDef ->
            val mutableClass by lazy {
                mutableClassDefBy(classDef)
            }

            classDef.methods.forEach classLoop@{ method ->
                val implementation = method.implementation ?: return@classLoop

                val mutableMethod by lazy {
                    mutableClass.methods.firstOrNull {
                        it.name == method.name &&
                            it.parameterTypes.map { p -> p.toString() } == method.parameterTypes.map { p -> p.toString() } &&
                            it.returnType == method.returnType
                    }
                }

                implementation.instructions.forEachIndexed { index, instruction ->
                    val string = ((instruction as? Instruction21c)?.reference as? StringReference)?.string
                        ?: return@forEachIndexed

                    // Apply transformation.
                    val transformedString = transform(string) ?: return@forEachIndexed

                    val target = mutableMethod ?: return@forEachIndexed
                    target.replaceInstruction(
                        index,
                        BuilderInstruction21c(
                            Opcode.CONST_STRING,
                            instruction.registerA,
                            ImmutableStringReference(transformedString),
                        )
                    )
                }
            }
        }

        // region Collection of transformations applied to all strings.

        fun commonTransform(referencedString: String): String? = when (referencedString) {
            "com.google",
            "com.google.android.gms",
            in Constants.PERMISSIONS,
            in Constants.ACTIONS,
            in Constants.AUTHORITIES,
            in Constants.CAPABILITIES,
                -> referencedString.replace("com.google", GMS_CORE_VENDOR_GROUP_ID)

            // No vendor prefix for whatever reason...
            "subscribedfeeds" -> "$GMS_CORE_VENDOR_GROUP_ID.subscribedfeeds"
            else -> null
        }

        fun contentUrisTransform(str: String): String? {
            // only when content:// uri
            if (str.startsWith("content://")) {
                // check if matches any authority
                for (authority in Constants.AUTHORITIES) {
                    val uriPrefix = "content://$authority"
                    if (str.startsWith(uriPrefix)) {
                        return str.replace(
                            uriPrefix,
                            "content://${authority.replace("com.google", GMS_CORE_VENDOR_GROUP_ID)}",
                        )
                    }
                }

                // gms also has a 'subscribedfeeds' authority, check for that one too
                val subFeedsUriPrefix = "content://subscribedfeeds"
                if (str.startsWith(subFeedsUriPrefix)) {
                    return str.replace(subFeedsUriPrefix, "content://$GMS_CORE_VENDOR_GROUP_ID.subscribedfeeds")
                }
            }

            return null
        }

        // endregion

        // Transform all strings using all provided transforms, first match wins.
        val transformations = listOf(
            ::commonTransform,
            ::contentUrisTransform,
        )

        transformStringReferences transform@{ string ->
            transformations.forEach { transform ->
                transform(string)?.let { transformedString -> return@transform transformedString }
            }

            return@transform null
        }

        // ── 2. Neutralize Play Services availability checks ────────

        // GCM "service not available" check — return early.
        ServiceCheckFingerprint.returnEarlyOrNull()

        // Return status code 0 (SUCCESS) for Play Services availability checks.
        listOf(
            IsGooglePlayServicesAvailableFingerprint,
            GooglePlayUtilityFingerprint,
            IsGooglePlayServicesAvailableLightFingerprint
        ).forEach {
            it.returnEarlyOrNull(intValue = 0)
        }

        // ── 3. Inject the GmsCore presence check into the main ──────
        //       activity's onCreate (microG installed / new enough /
        //       whitelisted from battery optimizations).

        MainActivityOnCreateFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p0}, $EXTENSION_CLASS_DESCRIPTOR->checkGmsCore(Landroid/app/Activity;)V
            """.trimIndent()
        )
    }
}
