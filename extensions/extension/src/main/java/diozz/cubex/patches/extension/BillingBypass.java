package diozz.cubex.patches.extension;

import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * Billing bypass helper — calls billing callbacks with success results
 * so the app thinks purchases succeeded.
 *
 * Uses reflection to work with any version of Google Play Billing library.
 *
 * Based on Lucky Patcher's InApp emulation: instead of contacting Google Play,
 * we simulate successful responses by calling the app's own callback listeners.
 */
@SuppressWarnings("unused")
public class BillingBypass {

    private static final String TAG = "BillingBypass";

    /**
     * Create BillingResult with responseCode=0 (OK) via reflection.
     */
    private static Object createBillingResultOk() {
        try {
            Class<?> brClass = Class.forName("com.android.billingclient.api.BillingResult");
            Object builder = brClass.getMethod("newBuilder").invoke(null);
            builder.getClass().getMethod("setResponseCode", int.class).invoke(builder, 0);
            builder.getClass().getMethod("setDebugMessage", String.class).invoke(builder, "");
            return builder.getClass().getMethod("build").invoke(builder);
        } catch (Exception e) {
            log("Failed to create BillingResult: " + e.getMessage());
            return null;
        }
    }

    /**
     * Handle startConnection(BillingClientStateListener).
     * Calls onBillingSetupFinished(BillingResult.OK) on the listener.
     *
     * @param listener The BillingClientStateListener passed to startConnection.
     */
    public static void handleStartConnection(Object listener) {
        try {
            if (listener == null) return;
            Object result = createBillingResultOk();
            Class<?> lc = Class.forName("com.android.billingclient.api.BillingClientStateListener");
            lc.getMethod("onBillingSetupFinished",
                Class.forName("com.android.billingclient.api.BillingResult"))
                .invoke(listener, result);
            log("startConnection: onBillingSetupFinished(OK)");
        } catch (Exception e) {
            log("startConnection failed: " + e.getMessage());
        }
    }

    /**
     * Handle queryPurchasesAsync(params, PurchasesResponseListener).
     * Calls onQueryPurchasesResponse(BillingResult.OK, emptyList).
     *
     * @param listener The PurchasesResponseListener (last parameter).
     */
    public static void handleQueryPurchases(Object listener) {
        try {
            if (listener == null) return;
            Object result = createBillingResultOk();
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

    /**
     * Handle consumeAsync(ConsumeParams, ConsumeResponseListener).
     * Calls onConsumeResponse(BillingResult.OK, purchaseToken).
     *
     * @param params   The ConsumeParams (contains getPurchaseToken()).
     * @param listener The ConsumeResponseListener.
     */
    public static void handleConsumeAsync(Object params, Object listener) {
        try {
            if (listener == null) return;
            Object result = createBillingResultOk();
            String token = "";
            if (params != null) {
                try {
                    token = (String) params.getClass()
                        .getMethod("getPurchaseToken").invoke(params);
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

    /**
     * Handle acknowledgePurchase(params, AcknowledgePurchaseResponseListener).
     * Calls onAcknowledgePurchaseResponse(BillingResult.OK).
     *
     * @param params   The AcknowledgePurchaseParams (unused but kept for signature).
     * @param listener The AcknowledgePurchaseResponseListener.
     */
    public static void handleAcknowledgePurchase(Object params, Object listener) {
        try {
            if (listener == null) return;
            Object result = createBillingResultOk();
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
     * Handle launchBillingFlow(Activity, BillingFlowParams).
     * Finds the PurchasesUpdatedListener stored inside the BillingClient instance
     * and calls onPurchasesUpdated(BillingResult.OK, emptyList).
     *
     * @param billingClient The BillingClient instance (p0 = this).
     * @return A BillingResult.OK object to return from launchBillingFlow.
     */
    public static Object handleLaunchBillingFlow(Object billingClient) {
        try {
            Object result = createBillingResultOk();
            ArrayList<Object> empty = new ArrayList<>();

            // PurchasesUpdatedListener is stored as a field inside BillingClient
            Object listener = findFieldByTypeName(billingClient, "PurchasesUpdatedListener");

            if (listener != null) {
                Class<?> lc = Class.forName("com.android.billingclient.api.PurchasesUpdatedListener");
                lc.getMethod("onPurchasesUpdated",
                    Class.forName("com.android.billingclient.api.BillingResult"),
                    Class.forName("java.util.List"))
                    .invoke(listener, result, empty);
                log("launchBillingFlow: onPurchasesUpdated(OK, empty)");
            } else {
                log("launchBillingFlow: PurchasesUpdatedListener not found");
            }
        } catch (Exception e) {
            log("launchBillingFlow failed: " + e.getMessage());
        }
        return createBillingResultOk();
    }

    /**
     * Search for a field whose type name contains the keyword.
     * Searches the full class hierarchy including superclasses.
     */
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
