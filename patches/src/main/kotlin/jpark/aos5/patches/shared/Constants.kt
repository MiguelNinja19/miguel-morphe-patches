/*
 * Copyright 2025 Miguel's Patches
 * https://github.com/MiguelNinja19/miguel-morphe-patches
 *
 * Anger of Stick 5: Zombie (package: jpark.AOS5) - by jpark
 *
 * Cocos2d-x game (C++ in libMyGame.so) with Java billing wrapper
 * (org.cocos2dx.cpp.AppActivity). The Java side handles all Google Play
 * Billing flow and calls back into C++ via nativeOnSuccess(productId,
 * isConsumable) when a purchase succeeds.
 */

package jpark.aos5.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val ANGER_OF_STICK_5 = Compatibility(
        name = "Anger of Stick 5",
        packageName = "jpark.AOS5",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xC62828,
        targets = listOf(
            AppTarget(
                version = "1.1.94"
            ),
            AppTarget(
                version = null,
                isExperimental = true
            )
        )
    )
}
