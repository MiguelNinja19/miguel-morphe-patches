package app.morphe.patches.all.misc.prefs

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import java.util.logging.Logger

@Suppress("unused")
val universalSharedPreferencesInjectorPatch = bytecodePatch(
    name = "Universal SharedPreferences injector",
    description = "Injects SharedPreferences values into the app at runtime. " +
        "Configure via the 'prefs-data' option with format: " +
        "file:type:key:value,file2:type2:key2:value2. " +
        "Supported types: int, boolean, string, long, float.",
    default = false,
) {
    val prefsDataOption = stringOption(
        key = "prefs-data",
        default = "",
        title = "SharedPreferences data",
        description = "Comma-separated list of prefs to inject. " +
            "Format: file:type:key:value,file2:type2:key2:value2. " +
            "Types: int, boolean, string, long, float. " +
            "Example: game_prefs:int:coins:99999,game_prefs:boolean:premium:true",
        required = true,
    )

    execute {
        val logger = Logger.getLogger("UniversalSharedPreferencesInjector")

        val prefsData = prefsDataOption.value ?: ""
        if (prefsData.isBlank()) {
            logger.warning("No prefs-data option set. Skipping patch.")
            return@execute
        }

        val entries = prefsData.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size < 4) return@mapNotNull null
            PrefsEntry(
                file = parts[0],
                type = parts[1],
                key = parts[2],
                value = parts.subList(3, parts.size).joinToString(":"),
            )
        }

        if (entries.isEmpty()) {
            logger.warning("No valid entries found in prefs-data option.")
            return@execute
        }

        var targetMethod: MutableMethod? = null
        var targetIndex = 0

        classDefForEach { classDef ->
            if (targetMethod != null) return@classDefForEach

            val superclass = classDef.superclass ?: return@classDefForEach

            if (superclass == "Landroid/app/Application;") {
                val mutableClass = mutableClassDefBy(classDef)
                val onCreate = mutableClass.methods.find {
                    it.name == "onCreate" &&
                        it.parameterTypes.isEmpty() &&
                        it.returnType == "V" &&
                        it.implementation != null
                }
                if (onCreate != null) {
                    targetMethod = onCreate
                    targetIndex = 0
                    logger.info("Found Application.onCreate in ${classDef.type}")
                }
            } else if (superclass.contains("Activity") && targetMethod == null) {
                val mutableClass = mutableClassDefBy(classDef)
                val onCreate = mutableClass.methods.find {
                    it.name == "onCreate" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0].toString() == "Landroid/os/Bundle;" &&
                        it.returnType == "V" &&
                        it.implementation != null
                }
                if (onCreate != null) {
                    targetMethod = onCreate
                    targetIndex = 1
                    logger.info("Found Activity.onCreate in ${classDef.type}")
                }
            }
        }

        val method = targetMethod ?: run {
            logger.warning("Could not find Application.onCreate or Activity.onCreate.")
            return@execute
        }

        val smali = StringBuilder()

        for (entry in entries) {
            smali.append("""
                |const-string v0, "${entry.file}"
                |const/4 v1, 0x0
                |invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
                |move-result-object v0
                |invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences${'$'}Editor;
                |move-result-object v0
            """.trimMargin())

            when (entry.type) {
                "int" -> {
                    val v = entry.value.toIntOrNull() ?: 0
                    smali.append("""
                        |const-string v1, "${entry.key}"
                        |const v2, $v
                        |invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences${'$'}Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences${'$'}Editor;
                    """.trimMargin())
                }
                "boolean" -> {
                    val v = if (entry.value.lowercase() == "true") 1 else 0
                    smali.append("""
                        |const-string v1, "${entry.key}"
                        |const/4 v2, $v
                        |invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences${'$'}Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences${'$'}Editor;
                    """.trimMargin())
                }
                "long" -> {
                    val v = entry.value.toLongOrNull() ?: 0L
                    smali.append("""
                        |const-string v1, "${entry.key}"
                        |const-wide v2, ${v}L
                        |invoke-interface {v0, v1, v2, v3}, Landroid/content/SharedPreferences${'$'}Editor;->putLong(Ljava/lang/String;J)Landroid/content/SharedPreferences${'$'}Editor;
                    """.trimMargin())
                }
                "float" -> {
                    val v = entry.value.toFloatOrNull() ?: 0f
                    smali.append("""
                        |const-string v1, "${entry.key}"
                        |const v2, ${v}f
                        |invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences${'$'}Editor;->putFloat(Ljava/lang/String;F)Landroid/content/SharedPreferences${'$'}Editor;
                    """.trimMargin())
                }
                "string" -> {
                    smali.append("""
                        |const-string v1, "${entry.key}"
                        |const-string v2, "${entry.value}"
                        |invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences${'$'}Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences${'$'}Editor;
                    """.trimMargin())
                }
                else -> {
                    logger.warning("Unknown type '${entry.type}' for key '${entry.key}'. Skipping.")
                    continue
                }
            }

            smali.append("""
                |invoke-interface {v0}, Landroid/content/SharedPreferences${'$'}Editor;->apply()V
            """.trimMargin())
        }

        method.addInstructions(targetIndex, smali.toString())
        logger.info("Injected ${entries.size} SharedPreferences entries")
    }
}

private data class PrefsEntry(
    val file: String,
    val type: String,
    val key: String,
    val value: String,
)
