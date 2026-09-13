package diozz.cubex.patches.extension;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import java.util.Locale;

/**
 * MicroG integration runtime checks — injected into the main
 * activity's onCreate by the "MicroG integration" patch.
 *
 * Simplified port of the Morphe / hoo-dles GmsCoreSupport extension
 * (no external shared-library dependencies: plain Toast + AlertDialog).
 *
 * Flow (same as ReVanced/Morphe GmsCore support):
 *  1. Is ReVanced GmsCore (app.revanced.android.gms) installed?
 *     No  -> toast + open https://morphe.software/microg + exit.
 *     Yes -> 2. Is it new enough? No -> toast + launch it.
 *  3. Is it whitelisted from battery optimizations?
 *     No  -> dialog with a button that opens the whitelist screen.
 *
 * Polytopia multiplayer context (issue #27): with MicroG integration
 * + Spoof signature, Google Play Games v2 sign-in works and online
 * multiplayer / owned tribes / skins tied to the account sync.
 */
@SuppressWarnings("unused")
public final class MicroGSupport {

    private static final String GMS_CORE_VENDOR_GROUP_ID = "app.revanced";
    private static final String GMS_CORE_PACKAGE_NAME
            = GMS_CORE_VENDOR_GROUP_ID + ".android.gms";
    private static final Uri GMS_CORE_PROVIDER
            = Uri.parse("content://" + GMS_CORE_VENDOR_GROUP_ID + ".android.gsf.gservices/prefix");
    private static final String GMS_CORE_DOWNLOAD_URL = "https://morphe.software/microg";

    /**
     * Minimum ReVanced GmsCore version code (0.255.07-dev family)
     * required for Play Games v2 sign-in.
     */
    private static final long MIN_REQUIRED_VERSION_CODE = 255070104L;

    private MicroGSupport() {
        // Static only.
    }

    /**
     * Injection point (called from the main activity's onCreate).
     */
    public static void checkGmsCore(Activity context) {
        try {
            // 1. Verify GmsCore is installed.
            try {
                PackageManager manager = context.getPackageManager();
                PackageInfo info = manager.getPackageInfo(GMS_CORE_PACKAGE_NAME, 0);

                long version = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        ? info.getLongVersionCode()
                        : info.versionCode;
                if (version < MIN_REQUIRED_VERSION_CODE) {
                    toastLong(context, "ReVanced GmsCore is outdated. Open it to update it.");
                    Intent launchIntent = manager.getLaunchIntentForPackage(GMS_CORE_PACKAGE_NAME);
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(launchIntent);
                    }
                    return;
                }
            } catch (PackageManager.NameNotFoundException exception) {
                toastLong(context, "MicroG (ReVanced GmsCore) is not installed. Opening the download page…");
                open(context, GMS_CORE_DOWNLOAD_URL);
                return;
            }

            // 2. Check if GmsCore is whitelisted from battery optimizations,
            //    otherwise the Google sign-in / FCM push for multiplayer
            //    turn notifications will randomly die in the background.
            if (isAndroidAutomotive(context)) {
                // No battery optimization settings on Android Automotive.
                return;
            }
            if (batteryOptimizationsEnabled(context)) {
                showBatteryOptimizationDialog(context);
                return;
            }

            // 3. Check if GmsCore is currently running in the background.
            var client = context.getContentResolver().acquireContentProviderClient(GMS_CORE_PROVIDER);
            //noinspection TryFinallyCanBeTryWithResources
            try {
                if (client == null) {
                    // GmsCore is installed and whitelisted but not running yet.
                    // It will start on demand when the game binds to it —
                    // just log, do not nag the user.
                    android.util.Log.i("MicroGSupport", "GmsCore provider not up yet (will start on demand)");
                }
            } finally {
                if (client != null) client.close();
            }
        } catch (Exception ex) {
            android.util.Log.e("MicroGSupport", "checkGmsCore failure", ex);
        }
    }

    private static void toastLong(Context context, String message) {
        Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private static void open(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ex) {
            android.util.Log.e("MicroGSupport", "Failed to open " + url, ex);
        }
    }

    private static void showBatteryOptimizationDialog(final Activity context) {
        // Use a delay to allow the activity to finish initializing
        // (showing a dialog during onCreate throws BadTokenException,
        // and dark mode would render it with wrong colors).
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                new AlertDialog.Builder(context)
                        .setTitle("MicroG battery optimization")
                        .setMessage("ReVanced GmsCore is running with battery optimizations enabled. "
                                + "This can break Google sign-in and multiplayer push notifications. "
                                + "Allow GmsCore to run in the background?")
                        .setPositiveButton("Open settings", (DialogInterface d, int w) ->
                                openGmsCoreDisableBatteryOptimizationsIntent(context))
                        .setNegativeButton("Later", null)
                        .setCancelable(true)
                        .show();
            } catch (Exception ex) {
                android.util.Log.e("MicroGSupport", "Failed to show battery dialog", ex);
            }
        }, 100);
    }

    @SuppressLint("BatteryLife") // Permission is part of GmsCore
    private static void openGmsCoreDisableBatteryOptimizationsIntent(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.fromParts("package", GMS_CORE_PACKAGE_NAME, null));
            activity.startActivityForResult(intent, 0);
        } catch (Exception ex) {
            // Fallback: open the generic battery optimization list.
            try {
                activity.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * @return If GmsCore is NOT whitelisted from battery optimizations.
     */
    private static boolean batteryOptimizationsEnabled(Context context) {
        //noinspection ObsoleteSdkInt
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        var powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return !powerManager.isIgnoringBatteryOptimizations(GMS_CORE_PACKAGE_NAME);
    }

    private static boolean isAndroidAutomotive(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /**
     * Kept as an extension API (matches the Morphe/Hoodles extension
     * surface). Returns the vendor group of the microG build this
     * patch targets: ReVanced GmsCore.
     */
    @SuppressWarnings("unused")
    private static String getGmsCoreVendorGroupId() {
        return GMS_CORE_VENDOR_GROUP_ID;
    }

    private static String lowerCaseManufacturer() {
        return Build.MANUFACTURER.toLowerCase(Locale.ROOT).replace(" ", "-");
    }
}
