package diozz.cubex.patches.extension;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;

import java.lang.reflect.Field;
import java.util.ArrayList;
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
     * Intercepta launchBillingFlow(Activity, BillingFlowParams).
     * Recebe 3 params: this(BillingClient), Activity, BillingFlowParams.
     */
    public static BillingResult handleLaunchBillingFlow(
            BillingClient billingClient, Object activity, Object billingFlowParams) {
        try {
            System.out.println("[BillingBypass] launchBillingFlow intercepted");

            // 1. Extrair o SKU
            String sku = extractSku(billingFlowParams);
            System.out.println("[BillingBypass] SKU: " + sku);

            // 2. Criar Purchase falsa
            Purchase fakePurchase = createFakePurchase(sku);
            if (fakePurchase == null) {
                System.out.println("[BillingBypass] Failed to create fake Purchase");
                return getOkResult();
            }
            System.out.println("[BillingBypass] Fake purchase created");

            // 3. Encontrar listener
            PurchasesUpdatedListener listener = findListener(billingClient);
            if (listener == null) {
                System.out.println("[BillingBypass] Listener not found!");
                return getOkResult();
            }
            System.out.println("[BillingBypass] Listener found: " + listener.getClass().getName());

            // 4. Chamar onPurchasesUpdated
            List<Purchase> purchases = new ArrayList<>();
            purchases.add(fakePurchase);
            listener.onPurchasesUpdated(getOkResult(), purchases);
            System.out.println("[BillingBypass] onPurchasesUpdated called with success!");

        } catch (Throwable e) {
            System.out.println("[BillingBypass] Error: " + e);
            e.printStackTrace();
        }
        return getOkResult();
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
            String fakeJson = "{\"productId\":\"" + sku + "\","
                + "\"purchaseToken\":\"lp_fake_" + System.currentTimeMillis() + "\","
                + "\"packageName\":\"air.com.midjiwan.polytopia\","
                + "\"purchaseState\":0,"
                + "\"purchaseTime\":" + System.currentTimeMillis() + "}";
            System.out.println("[BillingBypass] Fake JSON: " + fakeJson);
            return Purchase.class.getConstructor(String.class, String.class)
                .newInstance(fakeJson, "");
        } catch (Exception e) {
            System.out.println("[BillingBypass] createFakePurchase error: " + e);
            return null;
        }
    }

    private static PurchasesUpdatedListener findListener(BillingClient client) {
        Class<?> clazz = client.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (PurchasesUpdatedListener.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        return (PurchasesUpdatedListener) field.get(client);
                    } catch (Exception ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
