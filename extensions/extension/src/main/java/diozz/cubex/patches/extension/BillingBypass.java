package diozz.cubex.patches.extension;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;

import java.lang.reflect.Method;
import java.util.List;

@SuppressWarnings("unused")
public class BillingBypass {

    public static BillingResult handleLaunchBillingFlow(
            BillingClient billingClient, Object activity, Object billingFlowParams) {
        try {
            System.out.println("[BillingBypass] launchBillingFlow intercepted");

            // 1. Extract SKU from BillingFlowParams
            String sku = extractSku(billingFlowParams);
            System.out.println("[BillingBypass] SKU: " + sku);

            // 2. Create fake Purchase with the SKU
            Purchase fakePurchase = createFakePurchase(sku);
            if (fakePurchase == null) {
                System.out.println("[BillingBypass] Failed to create fake Purchase");
                return getOkResult();
            }

            // 3. Find bridge class with nativeOnPurchasesUpdated
            Class<?> bridgeClass = findBridgeClass();
            if (bridgeClass == null) {
                System.out.println("[BillingBypass] Bridge class not found");
                return getOkResult();
            }

            // 4. Call nativeOnPurchasesUpdated(0, "", [fakePurchase])
            Purchase[] purchases = new Purchase[]{fakePurchase};
            Method nativeMethod = bridgeClass.getDeclaredMethod(
                "nativeOnPurchasesUpdated",
                int.class, String.class, Purchase[].class);
            nativeMethod.setAccessible(true);
            nativeMethod.invoke(null, 0, "", purchases);
            System.out.println("[BillingBypass] nativeOnPurchasesUpdated called!");

        } catch (Throwable e) {
            System.out.println("[BillingBypass] Error: " + e);
            e.printStackTrace();
        }
        return getOkResult();
    }

    private static BillingResult getOkResult() {
        return BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .setDebugMessage("")
            .build();
    }

    private static String extractSku(Object params) {
        if (params == null) return "unknown_sku";

        // Try v6+: getProductDetailsParamsList → get(0) → zza() → getProductId()
        try {
            Method getList = findMethodReturning(params.getClass(), List.class);
            if (getList != null) {
                List<?> list = (List<?>) getList.invoke(params);
                if (list != null && !list.isEmpty()) {
                    Object firstParam = list.get(0);
                    Method getDetails = findMethodReturning(firstParam.getClass(),
                        Class.forName("com.android.billingclient.api.ProductDetails"));
                    if (getDetails != null) {
                        Object productDetails = getDetails.invoke(firstParam);
                        if (productDetails != null) {
                            Method getProductId = productDetails.getClass().getMethod("getProductId");
                            String sku = (String) getProductId.invoke(productDetails);
                            if (sku != null && !sku.isEmpty()) return sku;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[BillingBypass] v6+ extraction failed: " + e.getMessage());
        }

        // Try v3: getSku()
        try {
            Method getSku = params.getClass().getMethod("getSku");
            String sku = (String) getSku.invoke(params);
            if (sku != null && !sku.isEmpty()) return sku;
        } catch (Exception ignored) {}

        return "unknown_sku";
    }

    private static Purchase createFakePurchase(String sku) {
        try {
            long time = System.currentTimeMillis();
            String fakeJson = "{"
                + "\"productId\":\"" + sku + "\","
                + "\"purchaseToken\":\"lp_fake_" + time + "\","
                + "\"packageName\":\"air.com.midjiwan.polytopia\","
                + "\"purchaseState\":1,"
                + "\"purchaseTime\":" + time + ","
                + "\"acknowledged\":true"
                + "}";
            System.out.println("[BillingBypass] JSON: " + fakeJson);
            return new Purchase(fakeJson, "");
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
        // Search all zz* classes
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

    private static Method findMethodReturning(Class<?> clazz, Class<?> returnType) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getReturnType().equals(returnType) && method.getParameterTypes().length == 0) {
                method.setAccessible(true);
                return method;
            }
        }
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null) {
            return findMethodReturning(superClass, returnType);
        }
        return null;
    }
}
