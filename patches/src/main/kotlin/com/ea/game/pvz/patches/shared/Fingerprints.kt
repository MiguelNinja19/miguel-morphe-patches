package com.ea.game.pvz.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

object ShowAdFingerprint : Fingerprint(
    definingClass = "Lcom/popcap/pcsp/marketing/GoogleImaDriver;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Z",
    parameters = emptyList(),
    filters = listOf(string("Starting interactive ad"))
)

object LoadAdFingerprint : Fingerprint(
    definingClass = "Lcom/popcap/pcsp/marketing/GoogleImaDriver;",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    filters = listOf(string("Loading interactive ad"))
)

object PurchaseItemFingerprint : Fingerprint(
    filters = listOf(string("purchaseItem() call with sku(%s)"))
)
