package diozz.cubex.patches.extension;

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

    public static BillingResult handleLaunchBillingFlow(
            BillingClient billingClient, Object activity, Object billingFlowParams) {
        try {
            System.out.println("[BillingBypass] launchBillingFlow intercepted");

            // 1. Extrair o SKU dos BillingFlowParams
            String sku = extractSku(billingFlowParams);
            System.out.println("[BillingBypass] SKU: " + sku);

            // 2. Criar Purchase falsa com o SKU
            Purchase fakePurchase = createFakePurchase(sku);
            if (fakePurchase == null) {
                System.out.println("[BillingBypass] Failed to create fake Purchase");
                return getOkResult();
            }
            System.out.println("[BillingBypass] Fake purchase created for: " + sku);

            // 3. Chamar nativeOnPurchasesUpdated com a Purchase falsa
            // nativeOnPurchasesUpdated(int responseCode, String debugMsg, Purchase[] purchases)
            Class<?> zzbqClass = Class.forName("com.android.billingclient.api.zzbq");
            Method nativeMethod = zzbqClass.getDeclaredMethod(
                "nativeOnPurchasesUpdated",
                int.class, String.class, Purchase[].class);
            nativeMethod.setAccessible(true);

            // Criar array com a Purchase falsa
            Purchase[] purchases = new Purchase[]{fakePurchase};
            nativeMethod.invoke(null, 0, "", purchases);
            System.out.println("[BillingBypass] nativeOnPurchasesUpdated(0, \"\", [fakePurchase]) called!");

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
}
