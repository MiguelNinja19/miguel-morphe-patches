package diozz.cubex.patches.extension;

import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;

import java.lang.reflect.Field;
import java.util.ArrayList;

@SuppressWarnings("unused")
public class BillingBypass {

    private static BillingResult getOkResult() {
        return BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .setDebugMessage("")
            .build();
    }

    public static void handleStartConnection(BillingClientStateListener listener) {
        if (listener != null) {
            listener.onBillingSetupFinished(getOkResult());
        }
    }

    public static void handleQueryPurchases(PurchasesResponseListener listener) {
        if (listener != null) {
            listener.onQueryPurchasesResponse(getOkResult(), new ArrayList<>());
        }
    }

    public static void handleConsumeAsync(ConsumeResponseListener listener) {
        if (listener != null) {
            listener.onConsumeResponse(getOkResult(), "");
        }
    }

    public static void handleAcknowledgePurchase(AcknowledgePurchaseResponseListener listener) {
        if (listener != null) {
            listener.onAcknowledgePurchaseResponse(getOkResult());
        }
    }

    /**
     * Handle launchBillingFlow.
     * Finds the PurchasesUpdatedListener inside the BillingClient via reflection
     * and calls onPurchasesUpdated with a success result.
     */
    public static BillingResult handleLaunchBillingFlow(BillingClient billingClient) {
        try {
            PurchasesUpdatedListener listener = findListenerField(billingClient);
            if (listener != null) {
                listener.onPurchasesUpdated(getOkResult(), new ArrayList<>());
            }
        } catch (Exception ignored) {}
        return getOkResult();
    }

    private static PurchasesUpdatedListener findListenerField(Object obj) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (PurchasesUpdatedListener.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        return (PurchasesUpdatedListener) field.get(obj);
                    } catch (Exception ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
