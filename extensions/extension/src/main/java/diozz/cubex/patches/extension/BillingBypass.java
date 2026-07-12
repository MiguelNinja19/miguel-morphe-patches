package diozz.cubex.patches.extension;

import android.os.Handler;
import android.os.Looper;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;

import java.lang.reflect.Method;
import java.util.List;

@SuppressWarnings("unused")
public class BillingBypass {

    private static BillingResult getOkResult() {
        return BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .setDebugMessage("")
            .build();
    }

    /**
     * Called during onBillingSetupFinished to ensure store is "connected".
     * Finds and calls nativeOnBillingSetupFinished(0, "", 0) via reflection.
     */
    public static void handleBillingSetupFinished() {
        runOnMainThread(() -> {
            try {
                Class<?> bridgeClass = findBridgeClass();
                if (bridgeClass == null) {
                    System.out.println("[BillingBypass] Bridge class not found");
                    return;
                }

                Method m = bridgeClass.getDeclaredMethod(
                    "nativeOnBillingSetupFinished",
                    int.class, String.class, long.class);
                m.setAccessible(true);
                m.invoke(null, 0, "", 0L);
                System.out.println("[BillingBypass] nativeOnBillingSetupFinished(0, \"\", 0) called");
            } catch (Throwable e) {
                System.out.println("[BillingBypass] Setup error: " + e);
            }
        });
    }

    /**
     * Intercepta launchBillingFlow.
     * Cria Purchase falsa e chama nativeOnPurchasesUpdated na main thread.
     */
    public static BillingResult handleLaunchBillingFlow(
            BillingClient billingClient, Object activity, Object billingFlowParams) {
        System.out.println("[BillingBypass] launchBillingFlow intercepted");

        final String sku = extractSku(billingFlowParams);
        System.out.println("[BillingBypass] SKU: " + sku);

        runOnMainThread(() -> {
            try {
                Class<?> bridgeClass = findBridgeClass();
                if (bridgeClass == null) {
                    System.out.println("[BillingBypass] Bridge class not found");
                    return;
                }

                // Criar Purchase falsa
                Purchase fakePurchase = createFakePurchase(sku);
                if (fakePurchase == null) {
                    System.out.println("[BillingBypass] Failed to create fake Purchase");
                    return;
                }

                Purchase[] purchases = new Purchase[]{fakePurchase};

                // Chamar nativeOnPurchasesUpdated(0, "", [fakePurchase])
                Method m = bridgeClass.getDeclaredMethod(
                    "nativeOnPurchasesUpdated",
                    int.class, String.class, Purchase[].class);
                m.setAccessible(true);
                m.invoke(null, 0, "", purchases);
                System.out.println("[BillingBypass] nativeOnPurchasesUpdated(0, \"\", [fakePurchase]) called!");
            } catch (Throwable e) {
                System.out.println("[BillingBypass] Purchase error: " + e);
                e.printStackTrace();
            }
        });

        return getOkResult();
    }

    /**
     * Procura a classe bridge que contém nativeOnPurchasesUpdated.
     * O nome pode variar (zzbq, zzce, etc) então procuramos em todas.
     */
    private static Class<?> findBridgeClass() {
        // Tentar nomes conhecidos
        String[] knownNames = {
            "com.android.billingclient.api.zzbq",
            "com.android.billingclient.api.zzce",
            "com.android.billingclient.api.zzr",
        };

        for (String name : knownNames) {
            try {
                Class<?> c = Class.forName(name);
                // Verificar se tem nativeOnPurchasesUpdated
                try {
                    c.getDeclaredMethod("nativeOnPurchasesUpdated",
                        int.class, String.class, Purchase[].class);
                    System.out.println("[BillingBypass] Found bridge class: " + name);
                    return c;
                } catch (NoSuchMethodException ignored) {}
            } catch (ClassNotFoundException ignored) {}
        }

        // Procurar em todas as classes do package billing
        try {
            ClassLoader cl = BillingBypass.class.getClassLoader();
            // Tentar classes zz* comuns
            for (int i = 0; i < 26; i++) {
                for (int j = 0; j < 26; j++) {
                    String name = "com.android.billingclient.api.zz" + (char)('a' + i) + (char)('a' + j);
                    try {
                        Class<?> c = Class.forName(name);
                        try {
                            c.getDeclaredMethod("nativeOnPurchasesUpdated",
                                int.class, String.class, Purchase[].class);
                            System.out.println("[BillingBypass] Found bridge class: " + name);
                            return c;
                        } catch (NoSuchMethodException ignored) {}
                    } catch (ClassNotFoundException ignored) {}
                }
            }
        } catch (Exception ignored) {}

        System.out.println("[BillingBypass] Bridge class NOT FOUND!");
        return null;
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
                + "\"orderId\":\"GPA." + time + "-","
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

    private static void runOnMainThread(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            new Handler(Looper.getMainLooper()).post(r);
        }
    }
}
