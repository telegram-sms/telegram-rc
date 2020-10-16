package com.qwe7002.telegram_rc;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.fitc.wifihotspot.TetherManager;

import io.paperdb.Paper;

public class remote_control_public {

    public static boolean is_miui() {
        String manufacturer = Build.MANUFACTURER;
        return "xiaomi".equalsIgnoreCase(manufacturer);
    }


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

    static boolean is_vpn_hotsport_exist(Context context) {
        ApplicationInfo info;
        try {
            info = context.getPackageManager().getApplicationInfo(public_func.VPN_HOTSPOT_PACKAGE_NAME, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            info = null;
        }

        return info != null;
    }

    static boolean is_data_usage(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }
}
