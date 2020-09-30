package com.qwe7002.telegram_rc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import io.paperdb.Paper;

public class boot_receiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NotNull final Context context, @NotNull Intent intent) {
        final String TAG = "boot_receiver";
        Log.d(TAG, "Receive action: " + intent.getAction());
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean("initialized", false)) {
            public_func.write_log(context, "Received [" + intent.getAction() + "] broadcast, starting background service.");
            public_func.start_service(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
            Paper.init(context);
            if (Paper.book().read("resend_list", new ArrayList<>()).size() != 0) {
                Log.d(TAG, "An unsent message was detected, and the automatic resend process was initiated.");
                public_func.start_resend_service(context);
            }
            if (!intent.getAction().equals("android.intent.action.MY_PACKAGE_REPLACED")) {
                if (sharedPreferences.getBoolean("root", false)) {
                    String dummy_ip_addr = Paper.book("system_config").read("dummy_ip_addr", null);
                    if (dummy_ip_addr != null) {
                        com.qwe7002.root_kit.network.add_dummy_device(dummy_ip_addr);
                    }
                    WifiManager wifi_manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    assert wifi_manager != null;
                    if (Paper.book().read("wifi_open", false)) {
                        remote_control_public.enable_ap(wifi_manager);
                    }
                    String adb_port = Paper.book().read("adb_port", "-1");
                    if (!adb_port.equals("-1")) {
                        com.qwe7002.root_kit.nadb.set_nadb(adb_port);
                    }
                }
            }
        }
    }
}
