package app.morphe.patches.all.signature

import com.android.tools.smali.dexlib2.analysis.reflection.util.ReflectionUtils

object Constants {
    const val SPOOF_CLASS_JAVA_NAME = "diozz.cubex.patches.extension.SignatureSpoofApplication"
    val SPOOF_CLASS_SMALI_NAME: String = ReflectionUtils.javaToDexName(SPOOF_CLASS_JAVA_NAME)
}
