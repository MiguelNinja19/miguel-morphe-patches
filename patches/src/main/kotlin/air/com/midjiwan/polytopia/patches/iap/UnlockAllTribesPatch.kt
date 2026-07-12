package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA

@Suppress("unused")
val unlockAllTribesPatch = bytecodePatch(
    name = "Unlock all tribes",
    description = "Unlocks all 20 tribes by setting the debug unlock flag " +
        "in Unity SharedPreferences. Polytopia has a built-in debug class " +
        "(EverythingUnlockedPlatformPurchaseManager) that checks the key " +
        "'polytopia_purchase_debug_unlocked_tribes'. Setting it to include " +
        "all tribe names unlocks them without billing.",
    default = true,
) {
    compatibleWith(POLYTOPIA)

    execute {
        // Find the main Activity (Unity uses UnityPlayerActivity)
        val activityClass = classDefByOrNull { classDef ->
            classDef.superclass == "Lcom/unity3d/player/UnityPlayerActivity;"
        } ?: classDefByOrNull { classDef ->
            classDef.superclass?.contains("Activity") == true &&
            classDef.methods.any { it.name == "onCreate" }
        } ?: throw Exception("Could not find main Activity")

        val mutableClass = mutableClassDefBy(activityClass)

        val onCreateMethod = mutableClass.methods.find {
            it.name == "onCreate" &&
            it.parameterTypes.size == 1 &&
            it.parameterTypes[0].toString() == "Landroid/os/Bundle;" &&
            it.implementation != null
        } ?: throw Exception("Could not find onCreate method")

        println("Found Activity: ${activityClass.type}")

        // All 20 tribes found in the game's metadata
        // Source: global-metadata.dat → TribeType enum + polytaur_xxx_icon sprites
        val allTribes = listOf(
            "Xinxi", "Imperius", "Bardur", "Oumaji", "Kickoo",
            "Hoodrick", "Luxidoor", "Vengir", "Zebasi", "Aimo",
            "Aquarion", "Elyrion", "Polaris", "Magma", "Yadakk",
            "Quetzali", "Cymanti", "Swamp", "Ikarus", "Urkaz"
        ).joinToString(",")

        // Unity PlayerPrefs stores data in:
        // /data/data/<package>/shared_prefs/<package>.v2.playerprefs.xml
        // The key is: polytopia_purchase_debug_unlocked_tribes
        // Value is a comma-separated list of tribe names
        //
        // EverythingUnlockedPlatformPurchaseManager reads this key and
        // unlocks all listed tribes without billing.
        onCreateMethod.addInstructions(1, """
            const-string v0, "air.com.midjiwan.polytopia.v2.playerprefs"
            const/4 v1, 0x0
            invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
            move-result-object v0
            invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences${'$'}Editor;
            move-result-object v0
            const-string v1, "polytopia_purchase_debug_unlocked_tribes"
            const-string v2, "$allTribes"
            invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences${'$'}Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences${'$'}Editor;
            invoke-interface {v0}, Landroid/content/SharedPreferences${'$'}Editor;->apply()V
        """.trimIndent())

        println("✓ Injected debug unlock for all 20 tribes")
        println("  Tribes: $allTribes")
    }
}
