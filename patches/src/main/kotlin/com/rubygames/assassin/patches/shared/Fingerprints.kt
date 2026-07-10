package com.rubygames.assassin.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

object OnCreateFingerprint : Fingerprint(
    definingClass = "Lorg/cocos2dx/cpp/AppActivity;",
    accessFlags = listOf(AccessFlags.PROTECTED),
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/rovio/beacon/Application;",
            name = "setActivity",
        ),
    )
)

object OnAdHiddenFingerprint : Fingerprint(
    definingClass = "Lcom/rovio/beacon/ads/AdsSdk;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Z"),
)
