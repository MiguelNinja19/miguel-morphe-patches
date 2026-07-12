package diozz.cubex.patches.extension;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class BillingBypass {

    private static String pendingSku = null;
    private static boolean billingInitialized = false;

    /**
     * Called from launchBillingFlow to store the SKU for later.
     */
    public static void storeSku(Object billingFlowParams) {
        try {
            String sku = extractSku(billingFlowParams);
            pendingSku = sku;
            System.out.println("[BillingBypass] Stored SKU: " + sku);
        } catch (Exception e) {
            System.out.println("[BillingBypass] storeSku error: " + e);
        }
    }

    /**
     * Called from onPurchasesUpdated to intercept the result.
     * Creates a fake Purchase with the stored SKU and calls nativeOnPurchasesUpdated(0, "", [fakePurchase]).
     * 
     * @param responseCode The original response code (we ignore it)
     * @param debugMsg The original debug message (we ignore it)
     * @return true if we handled it (caller should return void), false if no SKU was stored
     */
    public static boolean interceptPurchase(int responseCode, String debugMsg) {
        if (pendingSku == null) {
            System.out.println("[BillingBypass] No pending SKU, letting original handle it");
            return false;
        }

        try {
            System.out.println("[BillingBypass] Intercepting purchase result: code=" + responseCode + " sku=" + pendingSku);

            // Create fake Purchase
            Purchase fakePurchase = createFakePurchase(pendingSku);
            if (fakePurchase == null) {
                System.out.println("[BillingBypass] Failed to create fake Purchase");
                pendingSku = null;
                return false;
            }

            // Call nativeOnPurchasesUpdated(0, "", [fakePurchase]) via reflection
            Class<?> bridgeClass = findBridgeClass();
            if (bridgeClass == null) {
                System.out.println("[BillingBypass] Bridge class not found");
                pendingSku = null;
                return false;
            }

            Purchase[] purchases = new Purchase[]{fakePurchase};
            java.lang.reflect.Method m = bridgeClass.getDeclaredMethod(
                "nativeOnPurchasesUpdated",
                int.class, String.class, Purchase[].class);
            m.setAccessible(true);
            m.invoke(null, 0, "", purchases);
            System.out.println("[BillingBypass] nativeOnPurchasesUpdated(0, \"\", [fakePurchase]) called!");

            // Clear the pending SKU
            pendingSku = null;
            return true;
        } catch (Throwable e) {
            System.out.println("[BillingBypass] interceptPurchase error: " + e);
            e.printStackTrace();
            pendingSku = null;
            return false;
        }
    }

    /**
     * Called from onBillingSetupFinished to force responseCode=0.
     * The original method will call nativeOnBillingSetupFinished with the correct zza.
     * We just need to tell the caller to use responseCode=0.
     * 
     * @return 0 (BILLING_RESPONSE_RESULT_OK)
     */
    public static int getOkResponseCode() {
        System.out.println("[BillingBypass] Forcing billing setup responseCode=0");
        return 0;
    }

    private static String extractSku(Object params) {
        if (params == null) return "unknown_sku";
        try {
            Object pdpList = params.getClass().getMethod("getProductDetailsParamsList").invoke(params);
            if (pdpList instanceof List && !((List<?>) pdpList).isEmpty()) {
                Object firstPdp = ((List<?>) pdpList).get(0);
                return (String) firstPdp.getClass().getMethod("getProductId").invoke(firstPdp);
            }
        } catch (Exception ignored) {}
        try {
            return (String) params.getClass().getMethod("getSku").invoke(params);
        } catch (Exception ignored) {}
        return "unknown_sku";
    }

    private static Purchase createFakePurchase(String sku) {
        try {
            long time = System.currentTimeMillis();
            String fakeJson = "{"
                + "\"productId\":\"" + sku + "\","
                + "\"orderId\":\"GPA." + time + "-" + time + "\","
                + "\"packageName\":\"air.com.midjiwan.polytopia\","
                + "\"purchaseTime\":" + time + ","
                + "\"purchaseState\":0,"
                + "\"developerPayload\":\"\","
                + "\"purchaseToken\":\"lp_fake_" + time + "\","
                + "\"acknowledged\":true"
                + "}";
            System.out.println("[BillingBypass] Fake JSON: " + fakeJson);
            return Purchase.class.getConstructor(String.class, String.class)
                .newInstance(fakeJson, "");
        } catch (Exception e) {
            System.out.println("[BillingBypass] createFakePurchase error: " + e);
            return null;
        }
    }

    private static Class<?> findBridgeClass() {
        String[] knownNames = {
            "com.android.billingclient.api.zzbq",
            "com.android.billingclient.api.zzce",
            "com.android.billingclient.api.zzr",
        };
        for (String name : knownNames) {
            try {
                Class<?> c = Class.forName(name);
                try {
                    c.getDeclaredMethod("nativeOnPurchasesUpdated",
                        int.class, String.class, Purchase[].class);
                    return c;
                } catch (NoSuchMethodException ignored) {}
            } catch (ClassNotFoundException ignored) {}
        }
        // Search zz* classes
        for (int i = 0; i < 26; i++) {
            for (int j = 0; j < 26; j++) {
                String name = "com.android.billingclient.api.zz" + (char)('a' + i) + (char)('a' + j);
                try {
                    Class<?> c = Class.forName(name);
                    try {
                        c.getDeclaredMethod("nativeOnPurchasesUpdated",
                            int.class, String.class, Purchase[].class);
                        return c;
                    } catch (NoSuchMethodException ignored) {}
                } catch (ClassNotFoundException ignored) {}
            }
        }
        return null;
    }
}
