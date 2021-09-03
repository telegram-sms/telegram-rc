package com.qwe7002.telegram_rc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.fitc.wifihotspot.TetherManager;
import com.qwe7002.telegram_rc.static_class.log_func;
import com.qwe7002.telegram_rc.static_class.remote_control_func;
import com.qwe7002.telegram_rc.static_class.resend_func;
import com.qwe7002.telegram_rc.static_class.service_func;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import io.paperdb.Paper;

public class boot_receiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NotNull final Context context, @NotNull Intent intent) {
        final String TAG = "boot_receiver";
        Log.d(TAG, "Receive action: " + intent.getAction());
        Paper.init(context);
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean("initialized", false)) {
            log_func.write_log(context, "Received [" + intent.getAction() + "] broadcast, starting background service.");
            service_func.start_service(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false), sharedPreferences.getBoolean("wifi_monitor_switch", false));
            if (Paper.book().read("resend_list", new ArrayList<>()).size() != 0) {
                Log.d(TAG, "An unsent message was detected, and the automatic resend process was initiated.");
                resend_func.start_resend_service(context);
            }
            if (sharedPreferences.getBoolean("root", false)) {
                if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)) {
                    if (remote_control_func.is_termux_exist(context)) {
                        Log.i(TAG, "Termux detected, try to start init.rc");
                        com.qwe7002.telegram_rc.root_kit.startup.start_termux_script("init.rc");
                    }
                }
                if (Paper.book("system_config").contains("dummy_ip_addr")) {
                    String dummy_ip_addr = Paper.book("system_config").read("dummy_ip_addr");
                    com.qwe7002.telegram_rc.root_kit.network.add_dummy_device(dummy_ip_addr);
                }
                if (Paper.book("temp").read("wifi_open", false)) {
                    WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                    assert wifiManager != null;
                    remote_control_func.enable_vpn_ap(wifiManager);
                }
                if (Paper.book("temp").read("tether_open", false)) {
                    WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                    assert wifiManager != null;
                    remote_control_func.enable_tether(context, Paper.book("temp").read("tether_mode", TetherManager.TetherMode.TETHERING_WIFI));
                }
            }
        }
    }
}

