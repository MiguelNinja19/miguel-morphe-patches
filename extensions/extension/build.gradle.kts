extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "diozz.cubex.patches.extension"
}

dependencies {
    compileOnly("com.android.billingclient:billing:6.2.0")
    // Required by SignatureSpoofApplication (hidden API access on Android P+).
    // Bundled into the extension .mpe dex (implementation, not compileOnly).
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
}
