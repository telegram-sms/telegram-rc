package com.qwe7002.telegram_rc.static_class;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.fitc.wifihotspot.TetherManager;

import org.jetbrains.annotations.NotNull;

import io.paperdb.Paper;

public class remote_control {


    public static void disableVPNHotspot(android.net.wifi.WifiManager wifiManager) {
        Paper.book("temp").write("wifi_open", false);
        com.qwe7002.telegram_rc.root_kit.activity_manage.force_stop_package(CONST.VPN_HOTSPOT_PACKAGE_NAME);
        com.qwe7002.telegram_rc.root_kit.network.wifi_set_enable(false);
        try {
            while (wifiManager.getWifiState() != android.net.wifi.WifiManager.WIFI_STATE_DISABLED) {
                Thread.sleep(100);
            }
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        com.qwe7002.telegram_rc.root_kit.network.wifi_set_enable(true);
        try {
            while (wifiManager.getWifiState() != android.net.wifi.WifiManager.WIFI_STATE_ENABLED) {
                Thread.sleep(100);
            }
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void enableVPNHotspot(android.net.wifi.WifiManager wifiManager) {
        if (wifiManager.isWifiEnabled()) {
            disableVPNHotspot(wifiManager);
        }
        Paper.book("temp").write("wifi_open", true);
        com.qwe7002.telegram_rc.root_kit.network.wifi_set_enable(true);
        try {
            while (wifiManager.getWifiState() != android.net.wifi.WifiManager.WIFI_STATE_ENABLED) {
                Thread.sleep(100);
            }
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        com.qwe7002.telegram_rc.root_kit.activity_manage.start_foreground_service(CONST.VPN_HOTSPOT_PACKAGE_NAME, CONST.VPN_HOTSPOT_PACKAGE_NAME + ".RepeaterService");
    }


    public static void enableHotspot(Context context, int mode) {
        Paper.book("temp").write("tether_open", true);
        TetherManager manager = new TetherManager(context);
        manager.startTethering(mode, null);
    }

    public static void disableHotspot(Context context, int mode) {
        Paper.book("temp").write("tether_open", false);
        TetherManager manager = new TetherManager(context);
        manager.stopTethering(mode);
    }

    public static boolean isHotspotActive(Context context) {
        TetherManager manager = new TetherManager(context);
        Paper.book("temp").write("tether_open", manager.isTetherActive());
        return manager.isTetherActive();
    }


    public static boolean isVPNHotspotExist(@NotNull Context context) {
        ApplicationInfo info;
        try {
            info = context.getPackageManager().getApplicationInfo(CONST.VPN_HOTSPOT_PACKAGE_NAME, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            info = null;
        }

        return info != null;
    }


    public static boolean isDataUsageAccess(@NotNull Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

}
