package diozz.cubex.patches.extension;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Signature spoof — replaces PackageInfo.CREATOR so that every
 * PackageInfo parcel read for THIS app reports the ORIGINAL
 * (pre-patching) signature instead of Morphe's re-signing key.
 *
 * This is what makes Google Play Games / Google sign-in work on a
 * patched app: GMS validates the calling app by comparing the
 * signature reported by PackageManager with the SHA-1 registered in
 * Google's cloud console for the OAuth client of the app.
 *
 * Code adapted from:
 *   ApkSignatureKillerEx (L-JINBIN) — CREATOR replacement technique,
 *   via hoo-dles/morphe-patches (extensions/all/signature),
 *   which is the implementation confirmed to work for
 *   The Battle of Polytopia online multiplayer (issue #27).
 *
 * The two constants below are PLACEHOLDERS: the
 * "Spoof signature" patch replaces them at patch time with the real
 * package name and the Base64 encoded original certificate of the
 * APK being patched. Do not edit them by hand.
 */
@SuppressWarnings("unused")
public class SignatureSpoofApplication extends Application {

    private static final String TAG = "SignatureSpoof";

    static {
        String packageName = "PACKAGE_NAME_PLACEHOLDER";
        String signature = "SIGNATURE_PLACEHOLDER";

        killPM(packageName, signature);
    }

    /**
     * Also callable directly (hooked by the patch in apps that
     * already declare their own Application class).
     */
    public static void killPM(String packageName, String signature) {
        try {
            Signature fakeSignature = new Signature(Base64.decode(signature, Base64.DEFAULT));
            Parcelable.Creator<PackageInfo> creator = getPackageInfoCreator(packageName, fakeSignature);

            try {
                findField(PackageInfo.class, "CREATOR").set(null, creator);
            } catch (Exception e) {
                Log.e(TAG, "Failed to replace PackageInfo.CREATOR", e);
                throw new RuntimeException(e);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions(
                        "Landroid/os/Parcel;",
                        "Landroid/content/pm",
                        "Landroid/app"
                );
            }

            try {
                Object cache = findField(PackageManager.class, "sPackageInfoCache").get(null);
                cache.getClass().getMethod("clear").invoke(cache);
            } catch (Exception ignored) {
                // Not available on every Android version — fine.
            }

            try {
                Map<?, ?> mCreators = (Map<?, ?>) findField(Parcel.class, "mCreators").get(null);
                mCreators.clear();
            } catch (Exception ignored) {
            }

            try {
                Map<?, ?> sPairedCreators = (Map<?, ?>) findField(Parcel.class, "sPairedCreators").get(null);
                sPairedCreators.clear();
            } catch (Exception ignored) {
            }
        } catch (Throwable t) {
            // Never crash the app because of the spoof.
            Log.e(TAG, "killPM failed", t);
        }
    }

    private static Parcelable.Creator<PackageInfo> getPackageInfoCreator(final String packageName,
                                                                        final Signature fakeSignature) {
        final Parcelable.Creator<PackageInfo> originalCreator = PackageInfo.CREATOR;

        return new Parcelable.Creator<PackageInfo>() {
            @Override
            public PackageInfo createFromParcel(Parcel source) {
                PackageInfo packageInfo = originalCreator.createFromParcel(source);
                if (packageInfo.packageName.equals(packageName)) {
                    if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                        packageInfo.signatures[0] = fakeSignature;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        if (packageInfo.signingInfo != null) {
                            Signature[] signaturesArray = packageInfo.signingInfo.getApkContentsSigners();
                            if (signaturesArray != null && signaturesArray.length > 0) {
                                signaturesArray[0] = fakeSignature;
                            }
                        }
                    }
                }
                return packageInfo;
            }

            @Override
            public PackageInfo[] newArray(int size) {
                return originalCreator.newArray(size);
            }
        };
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            while (true) {
                clazz = clazz.getSuperclass();
                if (clazz == null || clazz.equals(Object.class)) {
                    break;
                }
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw e;
        }
    }
}
