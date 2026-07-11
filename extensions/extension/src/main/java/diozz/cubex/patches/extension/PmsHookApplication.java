package diozz.cubex.patches.extension;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Signature verification bypass — creates a Proxy of IPackageManager
 * that returns the original APK signatures when the app calls
 * getPackageInfo(GET_SIGNATURES).
 *
 * Based on Lucky Patcher's sigkill.dex (PmsHookApplication).
 */
@SuppressWarnings("unused")
public class PmsHookApplication implements InvocationHandler {

    private static String appPkgName = "";
    private static Signature[] signatures;
    private Object base;

    public static void hook(Context context, String signaturesBase64) {
        try {
            if (!signaturesBase64.isEmpty() && signatures == null) {
                DataInputStream dis = new DataInputStream(
                    new ByteArrayInputStream(Base64.decode(signaturesBase64, 0)));
                int count = dis.read() & 255;
                byte[][] bytes = new byte[count][];
                for (int i = 0; i < count; i++) {
                    bytes[i] = new byte[dis.readInt()];
                    dis.readFully(bytes[i]);
                }
                signatures = new Signature[count];
                for (int i = 0; i < count; i++) {
                    signatures[i] = new Signature(bytes[i]);
                }
            }

            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass
                .getDeclaredMethod("currentActivityThread")
                .invoke(null);

            Field sPmField = activityThreadClass.getDeclaredField("sPackageManager");
            sPmField.setAccessible(true);

            Object realPm = sPmField.get(activityThread);

            Class<?> iPmClass = Class.forName("android.content.pm.IPackageManager");
            PmsHookApplication handler = new PmsHookApplication();
            handler.base = realPm;
            appPkgName = context.getPackageName();

            Object proxy = Proxy.newProxyInstance(
                iPmClass.getClassLoader(),
                new Class[]{iPmClass},
                handler
            );

            sPmField.set(activityThread, proxy);

            Object pm = context.getPackageManager();
            Field mPmField = pm.getClass().getDeclaredField("mPM");
            mPmField.setAccessible(true);
            mPmField.set(pm, proxy);

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void hook(Context context) {
        hook(context, "");
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("getPackageInfo".equals(method.getName()) && args != null && args.length >= 2) {
            String pkgName = (String) args[0];
            int flags = ((Number) args[1]).intValue();

            if ((flags & 64) != 0 && appPkgName.equals(pkgName) && signatures != null) {
                PackageInfo info = (PackageInfo) method.invoke(base, args);
                info.signatures = new Signature[signatures.length];
                System.arraycopy(signatures, 0, info.signatures, 0, signatures.length);
                return info;
            }
        }

        if ("getInstallerPackageName".equals(method.getName())) {
            return "com.android.vending";
        }

        return method.invoke(base, args);
    }
}
