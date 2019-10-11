package uk.reall.root_kit;

import android.content.Context;
import android.telephony.TelephonyManager;

import java.io.DataOutputStream;

public class data_switch {
    public static void set_data_enabled(Context context) {
        String command = "svc data disable\n ";
        TelephonyManager teleManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        assert teleManager != null;
        if (teleManager.getDataState() == TelephonyManager.DATA_DISCONNECTED) {
            command = "svc data enable\n ";
        }
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());

            outputStream.writeBytes(command);
            outputStream.flush();

            outputStream.writeBytes("exit\n");
            outputStream.flush();
            try {
                su.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }

            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
