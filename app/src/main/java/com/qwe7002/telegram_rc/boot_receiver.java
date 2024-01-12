package com.qwe7002.telegram_rc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;

import com.fitc.wifihotspot.TetherManager;
import com.qwe7002.telegram_rc.static_class.log;
import com.qwe7002.telegram_rc.static_class.remote_control;
import com.qwe7002.telegram_rc.static_class.resend;
import com.qwe7002.telegram_rc.static_class.service;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import io.paperdb.Paper;

public class boot_receiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NotNull final Context context, @NotNull Intent intent) {
        final String TAG = "boot_receiver";
        android.util.Log.d(TAG, "Receive action: " + intent.getAction());
        Paper.init(context);
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (sharedPreferences.getBoolean("initialized", false)) {
            log.writeLog(context, "Received [" + intent.getAction() + "] broadcast, starting background service.");
            service.startService(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
            if (Paper.book().read("resend_list", new ArrayList<>()).size() != 0) {
                android.util.Log.d(TAG, "An unsent message was detected, and the automatic resend process was initiated.");
                resend.start_resend_service(context);
            }
            if (sharedPreferences.getBoolean("root", false)) {
                if (Paper.book("system_config").contains("dummy_ip_addr")) {
                    String dummy_ip_addr = Paper.book("system_config").read("dummy_ip_addr");
                    com.qwe7002.telegram_rc.root_kit.network.addDummyDevice(dummy_ip_addr);
                }
                if (Boolean.TRUE.equals(Paper.book("temp").read("wifi_open", false))) {
                    WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                    assert wifiManager != null;
                    remote_control.enableVPNHotspot(wifiManager);
                }
                if (Boolean.TRUE.equals(Paper.book("temp").read("tether_open", false))) {
                    remote_control.enableHotspot(context, Paper.book("temp").read("tether_mode", TetherManager.TetherMode.TETHERING_WIFI));
                }
            }
        }
        Paper.book("temp").destroy();
    }
}

