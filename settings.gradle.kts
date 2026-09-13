rootProject.name = "diozz-cubex-patches"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    // Bumped from 1.3.2 to 1.3.4 for the "MicroG integration" and
    // "Spoof signature" patches: they need packageMetadata.signingCertificates,
    // document("AndroidManifest.xml") and MutableClassDef.setSuperClass,
    // which are available in 1.3.4 (the version hoo-dles/morphe-patches
    // and MorpheApp/morphe-patches build against). All the patch DSL this
    // repo already uses is unchanged between 1.3.2 and 1.3.4.
    // If anything else breaks, revert this line to "1.3.2" and remove the
    // setSuperClass block marked in SpoofSignaturePatch.kt.
    id("app.morphe.patches") version "1.3.4"
}
