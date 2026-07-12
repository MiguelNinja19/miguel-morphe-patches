package diozz.cubex.patches.extension;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;

import java.lang.reflect.Method;
import java.util.List;

@SuppressWarnings("unused")
public class BillingBypass {

    /**
     * Intercepta launchBillingFlow(Activity, BillingFlowParams).
     * Extrai o SKU, cria Purchase falsa, e chama nativeOnPurchasesUpdated(0, "", [fakePurchase]).
     * Retorna BillingResult.OK para não abrir a Play Store.
     */
    public static BillingResult handleLaunchBillingFlow(
            BillingClient billingClient, Object activity, Object billingFlowParams) {
        try {
            System.out.println("[BillingBypass] launchBillingFlow intercepted");

            // 1. Extrair SKU dos BillingFlowParams via reflection
            String sku = extractSku(billingFlowParams);
            System.out.println("[BillingBypass] Extracted SKU: " + sku);

            // 2. Criar Purchase falsa com o SKU
            Purchase fakePurchase = createFakePurchase(sku);
            if (fakePurchase == null) {
                System.out.println("[BillingBypass] Failed to create fake Purchase, returning OK");
                return getOkResult();
            }
            System.out.println("[BillingBypass] Fake Purchase created");

            // 3. Encontrar a classe bridge (zzbq) que tem nativeOnPurchasesUpdated
            Class<?> bridgeClass = findBridgeClass();
            if (bridgeClass == null) {
                System.out.println("[BillingBypass] Bridge class not found, returning OK");
                return getOkResult();
            }
            System.out.println("[BillingBypass] Found bridge class: " + bridgeClass.getName());

            // 4. Criar array com a Purchase falsa
            Purchase[] purchases = new Purchase[]{fakePurchase};

            // 5. Chamar nativeOnPurchasesUpdated(0, "", [fakePurchase]) via reflection
            Method nativeMethod = bridgeClass.getDeclaredMethod(
                "nativeOnPurchasesUpdated",
                int.class, String.class, Purchase[].class);
            nativeMethod.setAccessible(true);
            nativeMethod.invoke(null, 0, "", purchases);
            System.out.println("[BillingBypass] nativeOnPurchasesUpdated(0, \"\", [fakePurchase]) called!");

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

    /**
     * Extrai o SKU dos BillingFlowParams usando reflection.
     * Tenta múltiplas APIs (v3 getSku, v6 getProductDetailsParamsList).
     */
    private static String extractSku(Object params) {
        if (params == null) return "unknown_sku";

        // Tenta API v6+: getProductDetailsParamsList() → get(0) → zza() → getProductId()
        try {
            Method getList = findMethodReturning(params.getClass(), List.class);
            if (getList != null) {
                List<?> list = (List<?>) getList.invoke(params);
                if (list != null && !list.isEmpty()) {
                    Object firstParam = list.get(0);
                    // ProductDetailsParams.zza() returns ProductDetails
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
            System.out.println("[BillingBypass] v6+ SKU extraction failed: " + e.getMessage());
        }

        // Tenta API v3: getSku()
        try {
            Method getSku = params.getClass().getMethod("getSku");
            String sku = (String) getSku.invoke(params);
            if (sku != null && !sku.isEmpty()) return sku;
        } catch (Exception ignored) {}

        return "unknown_sku";
    }

    /**
     * Cria uma Purchase falsa com o SKU fornecido.
     * O JSON contém productId, purchaseToken, packageName, purchaseState, purchaseTime.
     */
    private static Purchase createFakePurchase(String sku) {
        try {
            long time = System.currentTimeMillis();
            String fakeJson = "{"
                + "\"productId\":\"" + sku + "\","
                + "\"purchaseToken\":\"lp_fake_" + time + "\","
                + "\"packageName\":\"" + getPackageName() + "\","
                + "\"purchaseState\":1,"
                + "\"purchaseTime\":" + time + ","
                + "\"acknowledged\":true"
                + "}";
            System.out.println("[BillingBypass] Fake JSON: " + fakeJson);
            return new Purchase(fakeJson, "");
        } catch (Exception e) {
            System.out.println("[BillingBypass] createFakePurchase error: " + e);
            return null;
        }
    }

    private static String getPackageName() {
        // Try to get package name from the BillingClient context
        // Fallback to a generic package name
        return "com.app.patched";
    }

    /**
     * Procura a classe bridge que contém nativeOnPurchasesUpdated.
     * Tenta nomes conhecidos (zzbq, zzce, etc) e depois procura em todas as classes zz*.
     */
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

        // Procurar em todas as classes zz* do package billing
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

    /**
     * Encontra um método de uma classe que retorna o tipo especificado.
     */
    private static Method findMethodReturning(Class<?> clazz, Class<?> returnType) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getReturnType().equals(returnType) && method.getParameterTypes().length == 0) {
                method.setAccessible(true);
                return method;
            }
        }
        // Tenta superclasse
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null) {
            return findMethodReturning(superClass, returnType);
        }
        return null;
    }
}
