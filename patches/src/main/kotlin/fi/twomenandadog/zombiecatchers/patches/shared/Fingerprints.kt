package fi.twomenandadog.zombiecatchers.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

object CoreComponentFactoryClinitFingerprint : Fingerprint(
    definingClass = "Landroidx/core/app/CoreComponentFactory;",
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/pairip/StartupLauncher;",
            name = "launch",
        ),
    )
)

object AppComponentFactoryClinitFingerprint : Fingerprint(
    definingClass = "Landroid/app/AppComponentFactory;",
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/pairip/StartupLauncher;",
            name = "launch",
        ),
    )
)
