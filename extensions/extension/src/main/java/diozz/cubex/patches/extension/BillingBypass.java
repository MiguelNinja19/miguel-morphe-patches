package diozz.cubex.patches.extension;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
     * Intercepta launchBillingFlow: extrai o SKU, cria uma Purchase falsa,
     * e chama onPurchasesUpdated no listener para creditar a compra.
     */
    public static BillingResult handleLaunchBillingFlow(BillingClient billingClient, Object billingFlowParams) {
        try {
            // 1. Extrair o SKU dos BillingFlowParams
            String sku = extractSku(billingFlowParams);
            System.out.println("[BillingBypass] Intercepted purchase for SKU: " + sku);

            // 2. Criar uma Purchase falsa
            Purchase fakePurchase = createFakePurchase(sku, billingClient);
            if (fakePurchase == null) {
                System.out.println("[BillingBypass] Failed to create fake Purchase");
                return getOkResult();
            }

            // 3. Encontrar o PurchasesUpdatedListener dentro do BillingClient
            PurchasesUpdatedListener listener = findListener(billingClient);
            if (listener == null) {
                System.out.println("[BillingBypass] Listener not found");
                return getOkResult();
            }

            // 4. Chamar onPurchasesUpdated com sucesso
            List<Purchase> purchases = new ArrayList<>();
            purchases.add(fakePurchase);
            listener.onPurchasesUpdated(getOkResult(), purchases);
            System.out.println("[BillingBypass] Called onPurchasesUpdated(OK, fakePurchase)");

        } catch (Exception e) {
            System.out.println("[BillingBypass] Error: " + e.getMessage());
        }
        return getOkResult();
    }

    /**
     * Extrai o SKU do BillingFlowParams.
     * Tenta a API v6+ (getProductDetailsParamsList) e cai para v3/v4 (getSku).
     */
    private static String extractSku(Object params) {
        try {
            // Tenta API v6+
            Object pdpList = params.getClass().getMethod("getProductDetailsParamsList").invoke(params);
            if (pdpList instanceof List && !((List<?>) pdpList).isEmpty()) {
                Object firstPdp = ((List<?>) pdpList).get(0);
                Method getProductId = firstPdp.getClass().getMethod("getProductId");
                return (String) getProductId.invoke(firstPdp);
            }
        } catch (Exception ignored) {}

        try {
            // Tenta API v3/v4
            Method getSku = params.getClass().getMethod("getSku");
            return (String) getSku.invoke(params);
        } catch (Exception ignored) {}

        return "unknown_sku";
    }

    /**
     * Cria um objeto Purchase falso usando reflection.
     */
    private static Purchase createFakePurchase(String sku, BillingClient billingClient) {
        try {
            String packageName = billingClient.getClass().getPackage().getName();
            // Tenta usar o package name real do app
            try {
                packageName = (String) billingClient.getClass().getMethod("getApplicationContext").invoke(billingClient).getClass().getPackage().getName();
            } catch (Exception ignored) {}

            String fakeJson = "{\"productId\":\"" + sku + "\",\"purchaseToken\":\"lp_fake_token_" + System.currentTimeMillis() + "\",\"packageName\":\"" + packageName + "\"}";
            
            // Purchase constructor: new Purchase(String jsonPurchaseDetails, String signature)
            return Purchase.class.getConstructor(String.class, String.class)
                .newInstance(fakeJson, "");
        } catch (Exception e) {
            System.out.println("[BillingBypass] createFakePurchase error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Encontra o PurchasesUpdatedListener dentro do BillingClient.
     */
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
