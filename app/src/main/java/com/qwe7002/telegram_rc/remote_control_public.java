package com.qwe7002.telegram_rc;

import android.content.Context;
import android.os.Build;

import com.fitc.wifihotspot.TetherManager;

import io.paperdb.Paper;

public class remote_control_public {

    public static void disable_vpn_ap(android.net.wifi.WifiManager wifi_manager) {
        Paper.book().write("wifi_open", false);
        com.qwe7002.root_kit.network.wifi_set_enable(false);
        try {
            while (wifi_manager.getWifiState() != android.net.wifi.WifiManager.WIFI_STATE_DISABLED) {
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void enable_vpn_ap(android.net.wifi.WifiManager wifi_manager) {
        if (wifi_manager.isWifiEnabled()) {
            com.qwe7002.root_kit.network.wifi_set_enable(false);
            try {
                while (wifi_manager.getWifiState() != android.net.wifi.WifiManager.WIFI_STATE_DISABLED) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Paper.book().write("wifi_open", true);
        com.qwe7002.root_kit.network.wifi_set_enable(true);
        try {
            while (wifi_manager.getWifiState() != android.net.wifi.WifiManager.WIFI_STATE_ENABLED) {
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

    public static void enable_tether(Context context) {
        Paper.book().write("tether_open", true);
        TetherManager manager = new TetherManager(context);
        manager.startTethering(null);
    }

    public static void disable_tether(Context context) {
        Paper.book().write("tether_open", false);
        TetherManager manager = new TetherManager(context);
        manager.stopTethering();
    }

    public static boolean is_tether_active(Context context) {
        TetherManager manager = new TetherManager(context);
        Paper.book().write("tether_open", manager.isTetherActive());
        return manager.isTetherActive();
    }
}
