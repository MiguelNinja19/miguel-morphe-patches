group = "diozz.cubex.patches"

patches {
    about {
        name = "CubeX Solver Patches"
        description = "Patches for CubeX Solver (Cube Solver) by Pipi Chick Studio / ZipoApps. Unlocks premium features, removes non-rewarded ads, removes relaunch/pairip-style protection, and grants rewarded-ad rewards without watching."
        source = "https://github.com/diozz-cubex-patches/morphe-patches"
        author = "diozz-cubex-patches"
        contact = "https://github.com/diozz-cubex-patches/morphe-patches/issues"
        website = "https://github.com/diozz-cubex-patches/morphe-patches"
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
