package diozz.cubex.patches.extension;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@SuppressWarnings("unused")
public class BillingBypass {

    public static BillingResult handleLaunchBillingFlow(
            BillingClient billingClient, Object activity, Object billingFlowParams) {
        try {
            System.out.println("[BillingBypass] launchBillingFlow intercepted");

            String sku = extractSku(billingFlowParams);
            System.out.println("[BillingBypass] SKU: " + sku);

            Purchase fakePurchase = createFakePurchase(sku);
            if (fakePurchase == null) {
                return getOkResult();
            }

            // Find the zzbq instance inside BillingClient to get the correct zza (pointer)
            Object zzbqInstance = findZzbqInstance(billingClient);
            long zza = 0L;
            if (zzbqInstance != null) {
                try {
                    Field zzaField = zzbqInstance.getClass().getDeclaredField("zza");
                    zzaField.setAccessible(true);
                    zza = zzaField.getLong(zzbqInstance);
                } catch (Exception ignored) {}
            }

            // Call nativeOnPurchasesUpdated(0, "", [fakePurchase])
            Class<?> bridgeClass = findBridgeClass();
            if (bridgeClass != null) {
                Purchase[] purchases = new Purchase[]{fakePurchase};
                Method nativeMethod = bridgeClass.getDeclaredMethod(
                    "nativeOnPurchasesUpdated",
                    int.class, String.class, Purchase[].class);
                nativeMethod.setAccessible(true);
                nativeMethod.invoke(null, 0, "", purchases);
                System.out.println("[BillingBypass] nativeOnPurchasesUpdated called!");
            }
        } catch (Throwable e) {
            System.out.println("[BillingBypass] Error: " + e);
            e.printStackTrace();
        }
        return getOkResult();
    }

    public static void handleBillingSetupFinished() {
        // Let original code handle it, just log
        System.out.println("[BillingBypass] onBillingSetupFinished intercepted");
    }

    private static BillingResult getOkResult() {
        return BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .setDebugMessage("")
            .build();
    }

    private static String extractSku(Object params) {
        if (params == null) return "unknown_sku";
        try {
            // BillingFlowParams.zzk() returns List<ProductDetailsParams>
            Method zzk = findMethodReturning(params.getClass(), List.class);
            if (zzk != null) {
                List<?> list = (List<?>) zzk.invoke(params);
                if (list != null && !list.isEmpty()) {
                    Object firstParam = list.get(0);
                    // ProductDetailsParams.zza() returns ProductDetails
                    Method zza = findMethodReturning(firstParam.getClass(),
                        Class.forName("com.android.billingclient.api.ProductDetails"));
                    if (zza != null) {
                        Object productDetails = zza.invoke(firstParam);
                        if (productDetails != null) {
                            Method getProductId = productDetails.getClass().getMethod("getProductId");
                            String sku = (String) getProductId.invoke(productDetails);
                            if (sku != null && !sku.isEmpty()) return sku;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[BillingBypass] v6 extraction failed: " + e.getMessage());
        }
        // Try v3
        try {
            Method getSku = params.getClass().getMethod("getSku");
            return (String) getSku.invoke(params);
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

    private static Object findZzbqInstance(Object billingClient) {
        try {
            // BillingClientImpl.zzf is a zzs instance
            Field zzfField = billingClient.getClass().getDeclaredField("zzf");
            zzfField.setAccessible(true);
            Object zzs = zzfField.get(billingClient);
            if (zzs == null) return null;
            // zzs.zzc is the zzb instance
            Field zzcField = zzs.getClass().getDeclaredField("zzc");
            zzcField.setAccessible(true);
            return zzcField.get(zzs);
        } catch (Exception e) {
            System.out.println("[BillingBypass] findZzbqInstance failed: " + e.getMessage());
            return null;
        }
    }

    private static Class<?> findBridgeClass() {
        String[] names = {"zzbq", "zzce", "zzr"};
        for (String n : names) {
            try {
                Class<?> c = Class.forName("com.android.billingclient.api." + n);
                try {
                    c.getDeclaredMethod("nativeOnPurchasesUpdated",
                        int.class, String.class, Purchase[].class);
                    return c;
                } catch (NoSuchMethodException ignored) {}
            } catch (ClassNotFoundException ignored) {}
        }
        for (int i = 0; i < 26; i++) {
            for (int j = 0; j < 26; j++) {
                String n = "com.android.billingclient.api.zz" + (char)('a'+i) + (char)('a'+j);
                try {
                    Class<?> c = Class.forName(n);
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
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getReturnType().equals(returnType) && m.getParameterTypes().length == 0) {
                m.setAccessible(true);
                return m;
            }
        }
        if (clazz.getSuperclass() != null) return findMethodReturning(clazz.getSuperclass(), returnType);
        return null;
    }
}
