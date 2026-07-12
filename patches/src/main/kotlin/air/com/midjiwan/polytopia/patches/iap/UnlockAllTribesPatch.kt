package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA

@Suppress("unused")
val unlockAllTribesPatch = bytecodePatch(
    name = "Unlock all tribes",
    description = "Unlocks all 20 tribes by setting the debug flag in " +
        "Unity PlayerPrefs via SharedPreferences.",
    default = true,
) {
    compatibleWith(POLYTOPIA)

    execute {
        val activityClass = classDefByOrNull { classDef ->
            classDef.superclass == "Lcom/unity3d/player/UnityPlayerActivity;"
        } ?: throw Exception("UnityPlayerActivity not found")

        val mutableClass = mutableClassDefBy(activityClass)

        val onCreateMethod = mutableClass.methods.find {
            it.name == "onCreate" &&
            it.parameterTypes.size == 1 &&
            it.parameterTypes[0].toString() == "Landroid/os/Bundle;" &&
            it.implementation != null
        } ?: throw Exception("onCreate not found")

        val allTribes = "Xinxi,Imperius,Bardur,Oumaji,Kickoo,Hoodrick,Luxidoor," +
            "Vengir,Zebasi,Aimo,Aquarion,Elyrion,Polaris,Magma,Yadakk," +
            "Quetzali,Cymanti,Swamp,Ikarus,Urkaz"

        // Use p-registers only (p0=this, p1=bundle) to avoid register conflicts
        // p0 is Context (Activity), so we can call getSharedPreferences on it
        onCreateMethod.addInstructions(0, """
            const-string p1, "air.com.midjiwan.polytopia.v2.playerprefs"
            const/4 v0, 0x0
            invoke-virtual {p0, p1, v0}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;
            move-result-object v0
            invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences${'$'}Editor;
            move-result-object v0
            const-string p1, "polytopia_purchase_debug_unlocked_tribes"
            const-string v1, "$allTribes"
            invoke-interface {v0, p1, v1}, Landroid/content/SharedPreferences${'$'}Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences${'$'}Editor;
            invoke-interface {v0}, Landroid/content/SharedPreferences${'$'}Editor;->apply()V
        """.trimIndent())

        println("✓ Injected debug unlock for 20 tribes")
    }
}
