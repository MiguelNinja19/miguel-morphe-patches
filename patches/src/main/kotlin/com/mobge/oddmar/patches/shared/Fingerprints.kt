package com.mobge.oddmar.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * com.mobge.oddmarbilling.HasProductBeenPurchasedCall$1.OnQuerySuccess(Z)V
 * Called when queryPurchases completes. The boolean parameter indicates
 * if the product was purchased. Patch to always pass true.
 *
 * Smali:
 *   .method private OnQuerySuccess(Z)V
 *     const-string v2, "Querying if product "
 *     ... logs ...
 *     iget-object v2, p0, ...->_productID:Ljava/lang/String;
 *     const-string v4, " has been purchased succeed. purchased: "
 */
object HasProductBeenPurchasedCallbackFingerprint : Fingerprint(
    definingClass = "Lcom/mobge/oddmarbilling/HasProductBeenPurchasedCall$1;",
    accessFlags = listOf(AccessFlags.PRIVATE),
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/mobge/oddmarbilling/HasProductBeenPurchasedCall;",
            name = "access$200",
        ),
    )
)
