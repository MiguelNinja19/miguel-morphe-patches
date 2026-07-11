package diozz.cubex.patches.extension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Billing bypass helper — calls billing callbacks with success results.
 * Uses reflection to work with any version of Google Play Billing library.
 */
@SuppressWarnings("unused")
public class BillingBypass {

    private static final String TAG = "BillingBypass";
    private static Object cachedBillingResultOk = null;

    /**
     * Create and cache BillingResult with responseCode=0 (OK).
     */
    private static Object getBillingResultOk() {
        if (cachedBillingResultOk != null) return cachedBillingResultOk;
        try {
            Class<?> brClass = Class.forName("com.android.billingclient.api.BillingResult");
            Object builder = brClass.getMethod("newBuilder").invoke(null);
            builder.getClass().getMethod("setResponseCode", int.class).invoke(builder, 0);
            builder.getClass().getMethod("setDebugMessage", String.class).invoke(builder, "");
            cachedBillingResultOk = builder.getClass().getMethod("build").invoke(builder);
        } catch (Exception e) {
            log("Failed to create BillingResult: " + e.getMessage());
        }
        return cachedBillingResultOk;
    }

    public static void handleStartConnection(Object listener) {
        try {
            if (listener == null) return;
            Object result = getBillingResultOk();
            if (result == null) return;

            Class<?> lc = Class.forName("com.android.billingclient.api.BillingClientStateListener");
            lc.getMethod("onBillingSetupFinished",
                Class.forName("com.android.billingclient.api.BillingResult"))
                .invoke(listener, result);
            log("startConnection: onBillingSetupFinished(OK)");
        } catch (Exception e) {
            log("startConnection failed: " + e.getMessage());
        }
    }

    public static void handleQueryPurchases(Object listener) {
        try {
            if (listener == null) return;
            Object result = getBillingResultOk();
            if (result == null) return;

            ArrayList<Object> empty = new ArrayList<>();
            Class<?> lc = Class.forName("com.android.billingclient.api.PurchasesResponseListener");
            lc.getMethod("onQueryPurchasesResponse",
                Class.forName("com.android.billingclient.api.BillingResult"),
                Class.forName("java.util.List"))
                .invoke(listener, result, empty);
            log("queryPurchases: onQueryPurchasesResponse(OK, empty)");
        } catch (Exception e) {
            log("queryPurchases failed: " + e.getMessage());
        }
    }

    public static void handleConsumeAsync(Object params, Object listener) {
        try {
            if (listener == null) return;
            Object result = getBillingResultOk();
            if (result == null) return;

            String token = "";
            if (params != null) {
                try {
                    token = (String) params.getClass().getMethod("getPurchaseToken").invoke(params);
                } catch (Exception ignored) {}
            }
            Class<?> lc = Class.forName("com.android.billingclient.api.ConsumeResponseListener");
            lc.getMethod("onConsumeResponse",
                Class.forName("com.android.billingclient.api.BillingResult"),
                String.class)
                .invoke(listener, result, token);
            log("consumeAsync: onConsumeResponse(OK)");
        } catch (Exception e) {
            log("consumeAsync failed: " + e.getMessage());
        }
    }

    public static void handleAcknowledgePurchase(Object params, Object listener) {
        try {
            if (listener == null) return;
            Object result = getBillingResultOk();
            if (result == null) return;

            Class<?> lc = Class.forName("com.android.billingclient.api.AcknowledgePurchaseResponseListener");
            lc.getMethod("onAcknowledgePurchaseResponse",
                Class.forName("com.android.billingclient.api.BillingResult"))
                .invoke(listener, result);
            log("acknowledgePurchase: onAcknowledgePurchaseResponse(OK)");
        } catch (Exception e) {
            log("acknowledgePurchase failed: " + e.getMessage());
        }
    }

    /**
     * Handle launchBillingFlow.
     * Returns a non-null BillingResult.OK to prevent NPE in the app.
     */
    public static Object handleLaunchBillingFlow(Object billingClient) {
        try {
            Object result = getBillingResultOk();
            ArrayList<Object> empty = new ArrayList<>();

            Object listener = findFieldByTypeName(billingClient, "PurchasesUpdatedListener");

            if (listener != null && result != null) {
                Class<?> lc = Class.forName("com.android.billingclient.api.PurchasesUpdatedListener");
                lc.getMethod("onPurchasesUpdated",
                    Class.forName("com.android.billingclient.api.BillingResult"),
                    Class.forName("java.util.List"))
                    .invoke(listener, result, empty);
                log("launchBillingFlow: onPurchasesUpdated(OK, empty)");
            } else {
                log("launchBillingFlow: listener or result null");
            }
        } catch (Exception e) {
            log("launchBillingFlow failed: " + e.getMessage());
        }
        // ALWAYS return a valid BillingResult, never null
        return getBillingResultOk();
    }

    private static Object findFieldByTypeName(Object obj, String keyword) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType().getName().contains(keyword)) {
                    try {
                        field.setAccessible(true);
                        return field.get(obj);
                    } catch (Exception ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static void log(String msg) {
        System.out.println("[" + TAG + "] " + msg);
    }
}
