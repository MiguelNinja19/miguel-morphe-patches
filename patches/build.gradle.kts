group = "miguel.morphe.patches"

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
