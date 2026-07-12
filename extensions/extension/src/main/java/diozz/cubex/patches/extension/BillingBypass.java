package diozz.cubex.patches.extension;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;

import java.lang.reflect.Method;

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
     * Chama nativeOnPurchasesUpdated diretamente via reflection.
     */
    public static BillingResult handleLaunchBillingFlow(
            BillingClient billingClient, Object activity, Object billingFlowParams) {
        try {
            System.out.println("[BillingBypass] launchBillingFlow intercepted");

            // Chamar nativeOnPurchasesUpdated diretamente via reflection
            // nativeOnPurchasesUpdated(int responseCode, String debugMsg, Purchase[] purchases)
            Class<?> zzbqClass = Class.forName("com.android.billingclient.api.zzbq");
            Method nativeMethod = zzbqClass.getDeclaredMethod(
                "nativeOnPurchasesUpdated",
                int.class, String.class, Purchase[].class);
            nativeMethod.setAccessible(true);

            // responseCode=0 (OK), debugMsg="", purchases=null
            nativeMethod.invoke(null, 0, "", null);
            System.out.println("[BillingBypass] nativeOnPurchasesUpdated(0, \"\", null) called!");

        } catch (Throwable e) {
            System.out.println("[BillingBypass] Error: " + e);
            e.printStackTrace();
        }
        return getOkResult();
    }
}
