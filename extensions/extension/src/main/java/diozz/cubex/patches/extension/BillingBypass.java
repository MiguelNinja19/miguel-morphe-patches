package diozz.cubex.patches.extension;

import java.lang.reflect.Field;
import java.util.ArrayList;

@SuppressWarnings("unused")
public class BillingBypass {

    private static final String TAG = "BillingBypass";
    private static Object cachedResultOk = null;

    private static Object getBillingResultOk() {
        if (cachedResultOk != null) return cachedResultOk;
        try {
            Class<?> brClass = Class.forName("com.android.billingclient.api.BillingResult");
            Object builder = brClass.getMethod("newBuilder").invoke(null);
            builder.getClass().getMethod("setResponseCode", int.class).invoke(builder, 0);
            builder.getClass().getMethod("setDebugMessage", String.class).invoke(builder, "");
            cachedResultOk = builder.getClass().getMethod("build").invoke(builder);
        } catch (Exception e) {
            log("Failed to create BillingResult: " + e.getMessage());
        }
        return cachedResultOk;
    }

    public static void handleStartConnection(Object listener) {
        try {
            if (listener == null) return;
            Object result = getBillingResultOk();
            if (result == null) return;
            Class.forName("com.android.billingclient.api.BillingClientStateListener")
                .getMethod("onBillingSetupFinished",
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
            Class.forName("com.android.billingclient.api.PurchasesResponseListener")
                .getMethod("onQueryPurchasesResponse",
                    Class.forName("com.android.billingclient.api.BillingResult"),
                    Class.forName("java.util.List"))
                .invoke(listener, result, empty);
            log("queryPurchases: onQueryPurchasesResponse(OK, empty)");
        } catch (Exception e) {
            log("queryPurchases failed: " + e.getMessage());
        }
    }

    // SIMPLIFIED: only takes listener (p2), not params (p1)
    public static void handleConsumeAsync(Object listener) {
        try {
            if (listener == null) return;
            Object result = getBillingResultOk();
            if (result == null) return;
            Class.forName("com.android.billingclient.api.ConsumeResponseListener")
                .getMethod("onConsumeResponse",
                    Class.forName("com.android.billingclient.api.BillingResult"),
                    String.class)
                .invoke(listener, result, "");
            log("consumeAsync: onConsumeResponse(OK)");
        } catch (Exception e) {
            log("consumeAsync failed: " + e.getMessage());
        }
    }

    // SIMPLIFIED: only takes listener (p2), not params (p1)
    public static void handleAcknowledgePurchase(Object listener) {
        try {
            if (listener == null) return;
            Object result = getBillingResultOk();
            if (result == null) return;
            Class.forName("com.android.billingclient.api.AcknowledgePurchaseResponseListener")
                .getMethod("onAcknowledgePurchaseResponse",
                    Class.forName("com.android.billingclient.api.BillingResult"))
                .invoke(listener, result);
            log("acknowledgePurchase: onAcknowledgePurchaseResponse(OK)");
        } catch (Exception e) {
            log("acknowledgePurchase failed: " + e.getMessage());
        }
    }

    public static Object handleLaunchBillingFlow(Object billingClient) {
        try {
            Object result = getBillingResultOk();
            if (result == null) return null;
            ArrayList<Object> empty = new ArrayList<>();
            Object listener = findFieldByTypeName(billingClient, "PurchasesUpdatedListener");
            if (listener != null) {
                Class.forName("com.android.billingclient.api.PurchasesUpdatedListener")
                    .getMethod("onPurchasesUpdated",
                        Class.forName("com.android.billingclient.api.BillingResult"),
                        Class.forName("java.util.List"))
                    .invoke(listener, result, empty);
                log("launchBillingFlow: onPurchasesUpdated(OK, empty)");
            }
        } catch (Exception e) {
            log("launchBillingFlow failed: " + e.getMessage());
        }
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
