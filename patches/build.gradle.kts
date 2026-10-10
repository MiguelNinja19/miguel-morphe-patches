group = "miguel.morphe.patches"

// Dependency repositories (same setup as hoo-dles/morphe-patches and
// MorpheApp/morphe-patches): needed to resolve
// app.morphe:morphe-patches-library from Morphe's GitHub Packages registry.
// On CI the GITHUB_ACTOR/GITHUB_TOKEN env vars provide the credentials.
repositories {
    google()
    mavenCentral()
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

patches {
    about {
        name = "Miguel's Patches"
        description = "Morphe patches by MiguelNinja19. Multiple apps supported — see the patch list below."
        source = "https://github.com/MiguelNinja19/miguel-morphe-patches"
        author = "MiguelNinja19"
        contact = "https://github.com/MiguelNinja19/miguel-morphe-patches/issues"
        website = "https://github.com/MiguelNinja19/miguel-morphe-patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath: Configuration by configurations.creating

dependencies {
    // Provides the newer patcher API (packageMetadata.signingCertificates,
    // app.morphe.patcher.apk.ApkSignatureScheme, context-aware
    // Fingerprint.method) required by the "MicroG integration" and
    // "Spoof signature" patches. The Gradle plugin alone (1.3.4) ships an
    // older patcher without these APIs — this is the same library both
    // hoo-dles/morphe-patches and MorpheApp/morphe-patches add.
    implementation(libs.morphe.patches.library)

    // Required due to smali at runtime, or build fails (same as upstream
    // patch repos; harmless if the smali version in use does not need it).
    implementation(libs.guava)

    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
