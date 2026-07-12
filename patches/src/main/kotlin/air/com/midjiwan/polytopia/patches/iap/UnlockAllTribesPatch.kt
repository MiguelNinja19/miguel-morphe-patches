package air.com.midjiwan.polytopia.patches.iap

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import air.com.midjiwan.polytopia.patches.shared.POLYTOPIA

private const val EXTENSION_CLASS = "Ldiozz/cubex/patches/extension/BillingBypass;"

@Suppress("unused")
val unlockAllTribesPatch = bytecodePatch(
    name = "Unlock all tribes",
    description = "Unlocks all 20 tribes by setting the debug flag in " +
        "Unity PlayerPrefs via extension.",
    default = true,
) {
    compatibleWith(POLYTOPIA)
    extendWith("extensions/extension.mpe")

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

        // Only use p0 (this/Context) — no extra registers needed!
        // Extension handles all the SharedPreferences logic
        onCreateMethod.addInstructions(0, """
            invoke-static {p0}, $EXTENSION_CLASS->unlockTribes(Landroid/content/Context;)V
        """.trimIndent())

        println("✓ Injected tribe unlock via extension")
    }
}
