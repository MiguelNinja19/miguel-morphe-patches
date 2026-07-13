package air.com.midjiwan.polytopia.extension;

import android.content.Context;
import java.io.File;
import java.io.FileWriter;

/**
 * Writes the debug config file (user.cfg) that activates
 * EverythingUnlockedPlatformPurchaseManager in Polytopia.
 *
 * This is a Java extension because the target method (onCreate) has
 * .locals 1 which is insufficient for file I/O in pure smali.
 */
@SuppressWarnings("unused")
public class DebugUnlockPatch {

    public static void writeDebugConfig(Context context) {
        try {
            File dir = context.getFilesDir();
            if (dir == null) return;

            File file = new File(dir, "user.cfg");
            FileWriter writer = new FileWriter(file, false);
            writer.write("debugUnlock = true\n");
            writer.write("purchaseDebug = true\n");
            writer.close();
        } catch (Exception e) {
            // Ignore — we don't want to crash the app if file ops fail
        }
    }
}
