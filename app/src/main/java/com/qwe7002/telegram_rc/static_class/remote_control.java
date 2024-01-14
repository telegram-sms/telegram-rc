package com.qwe7002.telegram_rc.static_class;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.fitc.wifihotspot.TetherManager;

import org.jetbrains.annotations.NotNull;

import io.paperdb.Paper;

public class remote_control {
    public static void disableVPNHotspot(android.net.wifi.WifiManager wifiManager) {
        Paper.book("temp").write("wifi_open", false);
        com.qwe7002.telegram_rc.root_kit.activity_manage.forceStopService("be.mygod.vpnhotspot");
        com.qwe7002.telegram_rc.root_kit.network.setWifi(false);
        try {
            while (wifiManager.getWifiState() != android.net.wifi.WifiManager.WIFI_STATE_DISABLED) {
                Thread.sleep(100);
            }
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        com.qwe7002.telegram_rc.root_kit.network.setWifi(true);
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
        com.qwe7002.telegram_rc.root_kit.network.setWifi(true);
        try {
            while (wifiManager.getWifiState() != android.net.wifi.WifiManager.WIFI_STATE_ENABLED) {
                Thread.sleep(100);
            }
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        com.qwe7002.telegram_rc.root_kit.activity_manage.startForegroundService("be.mygod.vpnhotspot", "be.mygod.vpnhotspot.RepeaterService");
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
            info = context.getPackageManager().getApplicationInfo("be.mygod.vpnhotspot", 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            info = null;
        }

        return info != null;
    }
    public static boolean isShizukuExist(@NotNull Context context) {
        ApplicationInfo info;
        try {
            info = context.getPackageManager().getApplicationInfo("moe.shizuku.privileged.api", 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            info = null;
        }

        return info != null;
    }



}
