package diozz.cubex.patches.extension;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@SuppressWarnings("unused")
public class BillingBypass {

    /**
     * Unlock all tribes by writing to Unity PlayerPrefs via SharedPreferences.
     */
    public static void unlockTribes(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(
                "air.com.midjiwan.polytopia.v2.playerprefs", 0);
            String tribes = "Xinxi,Imperius,Bardur,Oumaji,Kickoo,Hoodrick," +
                "Luxidoor,Vengir,Zebasi,Aimo,Aquarion,Elyrion,Polaris,Magma," +
                "Yadakk,Quetzali,Cymanti,Swamp,Ikarus,Urkaz";
            prefs.edit().putString(
                "polytopia_purchase_debug_unlocked_tribes", tribes).apply();
            System.out.println("[BillingBypass] Unlocked 20 tribes via debug key");
        } catch (Exception e) {
            System.out.println("[BillingBypass] unlockTribes error: " + e);
        }
    }

    /**
     * Handle onBillingSetupFinished in zzbq.
     * Forces responseCode=0 (OK) by calling nativeOnBillingSetupFinished(0, "", zza).
     * This makes the C# PurchaseManager think the store is connected.
     */
    public static void handleBillingSetupFinished(Object zzbqInstance, BillingResult billingResult) {
        try {
            System.out.println("[BillingBypass] onBillingSetupFinished intercepted");
            Class<?> bridgeClass = zzbqInstance.getClass();
            
            // Get the zza (native pointer) from the zzbq instance
            Field zzaField = bridgeClass.getDeclaredField("zza");
            zzaField.setAccessible(true);
            long zza = zzaField.getLong(zzbqInstance);
            System.out.println("[BillingBypass] Found zza=" + zza);

            // Call nativeOnBillingSetupFinished(0, "", zza) via reflection
            Method nativeMethod = bridgeClass.getDeclaredMethod(
                "nativeOnBillingSetupFinished",
                int.class, String.class, long.class);
            nativeMethod.setAccessible(true);
            nativeMethod.invoke(null, 0, "", zza);
            System.out.println("[BillingBypass] nativeOnBillingSetupFinished(0, \"\", " + zza + ") called!");
        } catch (Exception e) {
            System.out.println("[BillingBypass] handleBillingSetupFinished error: " + e);
        }
    }

    /**
     * Handle launchBillingFlow.
     * Extracts SKU, creates fake Purchase, and calls nativeOnPurchasesUpdated.
     */
    public static BillingResult handleLaunchBillingFlow(
            BillingClient billingClient, Object activity, Object billingFlowParams) {
        try {
            System.out.println("[BillingBypass] launchBillingFlow intercepted");

            String sku = extractSku(billingFlowParams);
            System.out.println("[BillingBypass] SKU: " + sku);

            Purchase fakePurchase = createFakePurchase(sku);
            if (fakePurchase == null) {
                System.out.println("[BillingBypass] Failed to create Purchase");
                return getOkResult();
            }

            // nativeOnPurchasesUpdated is static — no need for zzbq instance
            Class<?> bridgeClass = findBridgeClass();
            if (bridgeClass != null) {
                Purchase[] purchases = new Purchase[]{fakePurchase};
                Method nativeMethod = bridgeClass.getDeclaredMethod(
                    "nativeOnPurchasesUpdated",
                    int.class, String.class, Purchase[].class);
                nativeMethod.setAccessible(true);
                nativeMethod.invoke(null, 0, "", purchases);
                System.out.println("[BillingBypass] nativeOnPurchasesUpdated(0,\"\",[purchase]) called!");
            }
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
        try {
            Method zzk = findMethodReturning(params.getClass(), List.class);
            if (zzk != null) {
                List<?> list = (List<?>) zzk.invoke(params);
                if (list != null && !list.isEmpty()) {
                    Object firstParam = list.get(0);
                    Method zza = findMethodReturning(firstParam.getClass(),
                        Class.forName("com.android.billingclient.api.ProductDetails"));
                    if (zza != null) {
                        Object pd = zza.invoke(firstParam);
                        if (pd != null) {
                            Method getProductId = pd.getClass().getMethod("getProductId");
                            String sku = (String) getProductId.invoke(pd);
                            if (sku != null && !sku.isEmpty()) return sku;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[BillingBypass] v6 extraction failed: " + e.getMessage());
        }
        try {
            Method getSku = params.getClass().getMethod("getSku");
            return (String) getSku.invoke(params);
        } catch (Exception ignored) {}
        return "unknown_sku";
    }

    private static Purchase createFakePurchase(String sku) {
        try {
            long time = System.currentTimeMillis();
            String json = "{"
                + "\"productId\":\"" + sku + "\","
                + "\"purchaseToken\":\"lp_fake_" + time + "\","
                + "\"packageName\":\"air.com.midjiwan.polytopia\","
                + "\"purchaseState\":1,"
                + "\"purchaseTime\":" + time + ","
                + "\"acknowledged\":true"
                + "}";
            System.out.println("[BillingBypass] JSON: " + json);
            return new Purchase(json, "");
        } catch (Exception e) {
            System.out.println("[BillingBypass] createFakePurchase error: " + e);
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
