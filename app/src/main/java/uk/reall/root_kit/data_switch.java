package uk.reall.root_kit;

import android.content.Context;
import android.telephony.TelephonyManager;

public class data_switch {
    @SuppressWarnings("UnusedReturnValue")
    public static boolean switch_data_enabled(Context context) {
        boolean result;
        TelephonyManager teleManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        assert teleManager != null;
        if (teleManager.getDataState() == TelephonyManager.DATA_DISCONNECTED) {
            result = data_enabled();
        } else {
            result = data_disable();
        }
        return result;
    }

    public static boolean data_enabled() {
        return root_kit.run_shell_command("svc data enable\n ");
    }

    private static boolean data_disable() {
        return root_kit.run_shell_command("svc data disable\n ");
    }
}
