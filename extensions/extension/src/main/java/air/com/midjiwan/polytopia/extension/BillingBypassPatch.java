package air.com.midjiwan.polytopia.extension;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Free IAP extension for The Battle of Polytopia.
 *
 * This extension handles the complex logic of creating a fake Purchase
 * and calling the native billing callback. Using Java (instead of inline
 * smali) gives us try-catch for safety and reflection for accessing
 * obfuscated billing methods.
 *
 * Called from FreeInAppPurchasesPatch.kt HOOK 5 (launchBillingFlow).
 */
@SuppressWarnings("unused")
public class BillingBypassPatch {

    /**
     * Called from launchBillingFlow. Extracts the SKU from
     * BillingFlowParams, creates a fake Purchase with that SKU, and
     * calls zzbq.nativeOnPurchasesUpdated(0, "", [fakePurchase])
     * via reflection.
     *
     * @param params The BillingFlowParams passed to launchBillingFlow
     */
    public static void onLaunchBillingFlow(Object params) {
        try {
            String sku = extractSku(params);
            if (sku == null) return;

            String json = "{\"productId\":\"" + sku + "\","
                + "\"purchaseToken\":\"polytopia_mod_"
                + System.currentTimeMillis() + "\","
                + "\"packageName\":\"air.com.midjiwan.polytopia\","
                + "\"purchaseState\":1,"
                + "\"purchaseTime\":" + System.currentTimeMillis() + ","
                + "\"acknowledged\":true}";

            Object fakePurchase = createPurchase(json);
            if (fakePurchase == null) return;

            Object purchaseArray = java.lang.reflect.Array.newInstance(
                fakePurchase.getClass(), 1);
            java.lang.reflect.Array.set(purchaseArray, 0, fakePurchase);

            callNativeOnPurchasesUpdated(0, "", purchaseArray);
        } catch (Throwable t) {
            // Never crash — silently ignore
        }
    }

    /**
     * Extract SKU from BillingFlowParams via reflection.
     * Chain: params.zzk() -> List -> get(0) -> zza() -> ProductDetails
     *        -> getProductId() -> String
     */
    private static String extractSku(Object params) {
        try {
            Method zzk = params.getClass().getDeclaredMethod("zzk");
            Object list = zzk.invoke(params);
            if (list == null) return null;
            @SuppressWarnings("unchecked")
            List<Object> paramsList = (List<Object>) list;
            if (paramsList.isEmpty()) return null;
            Object firstParam = paramsList.get(0);

            Method zza = firstParam.getClass().getDeclaredMethod("zza");
            Object productDetails = zza.invoke(firstParam);
            if (productDetails == null) return null;

            Method getProductId = productDetails.getClass()
                .getDeclaredMethod("getProductId");
            return (String) getProductId.invoke(productDetails);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Create a Purchase object via reflection.
     * new Purchase(String json, String signature)
     */
    private static Object createPurchase(String json) {
        try {
            Class<?> purchaseClass = Class.forName(
                "com.android.billingclient.api.Purchase");
            java.lang.reflect.Constructor<?> ctor = purchaseClass
                .getConstructor(String.class, String.class);
            return ctor.newInstance(json, "");
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Call zzbq.nativeOnPurchasesUpdated(int, String, Purchase[])
     * via reflection.
     */
    private static void callNativeOnPurchasesUpdated(
            int responseCode, String debugMessage, Object purchaseArray) {
        try {
            Class<?> purchaseClass = Class.forName(
                "com.android.billingclient.api.Purchase");
            Class<?> purchaseArrayClass = java.lang.reflect.Array
                .newInstance(purchaseClass, 0).getClass();

            Class<?> zzbqClass = Class.forName(
                "com.android.billingclient.api.zzbq");
            Method method = zzbqClass.getDeclaredMethod(
                "nativeOnPurchasesUpdated",
                int.class, String.class, purchaseArrayClass);
            method.setAccessible(true);
            method.invoke(null, responseCode, debugMessage, purchaseArray);
        } catch (Throwable t) {
            // Ignore — native method might not be registered yet
        }
    }
}
