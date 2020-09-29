package com.qwe7002.telegram_rc;

import android.net.wifi.WifiManager;
import android.os.Build;

import io.paperdb.Paper;

public class remote_control_public {

    public static void disable_ap(WifiManager wifi_manager) {
        Paper.book().write("wifi_open", false);
        com.qwe7002.root_kit.network.wifi_set_enable(false);
        try {
            while (wifi_manager.getWifiState() != WifiManager.WIFI_STATE_DISABLED) {
                //noinspection BusyWait
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void enable_ap(WifiManager wifi_manager) {
        com.qwe7002.root_kit.network.add_dummy_device();
        if (wifi_manager.isWifiEnabled()) {
            com.qwe7002.root_kit.network.wifi_set_enable(false);
            try {
                while (wifi_manager.getWifiState() != WifiManager.WIFI_STATE_DISABLED) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Paper.book().write("wifi_open", true);
        com.qwe7002.root_kit.network.wifi_set_enable(true);
        try {
            while (wifi_manager.getWifiState() != WifiManager.WIFI_STATE_ENABLED) {
                //noinspection BusyWait
                Thread.sleep(100);
            }
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            com.qwe7002.root_kit.activity_manage.start_foreground_service(public_func.VPN_HOTSPOT_PACKAGE_NAME, public_func.VPN_HOTSPOT_PACKAGE_NAME + ".RepeaterService");
        } else {
            com.qwe7002.root_kit.activity_manage.start_service(public_func.VPN_HOTSPOT_PACKAGE_NAME, public_func.VPN_HOTSPOT_PACKAGE_NAME + ".RepeaterService");
        }
    }
}
