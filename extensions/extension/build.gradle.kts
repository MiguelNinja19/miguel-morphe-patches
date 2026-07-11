extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "diozz.cubex.patches.extension"
}

dependencies {
    // Use compileOnly so the extension can reference Billing classes at compile time
    // but uses the app's classes at runtime
    compileOnly("com.android.billingclient:billing:6.2.0")
}
