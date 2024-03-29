package com.qwe7002.telegram_rc.static_class;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.PowerManager;

import com.fitc.wifihotspot.TetherManager;

import org.jetbrains.annotations.NotNull;

import io.paperdb.Paper;

public class remote_control_func {


    public static void disable_vpn_ap(android.net.wifi.WifiManager wifi_manager) {
        Paper.book("temp").write("wifi_open", false);
        com.qwe7002.telegram_rc.root_kit.activity_manage.force_stop_package(const_value.VPN_HOTSPOT_PACKAGE_NAME);
        com.qwe7002.telegram_rc.root_kit.network.wifi_set_enable(false);
        try {
            while (wifi_manager.getWifiState() != android.net.wifi.WifiManager.WIFI_STATE_DISABLED) {
                Thread.sleep(100);
            }
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        com.qwe7002.telegram_rc.root_kit.network.wifi_set_enable(true);
        try {
            while (wifi_manager.getWifiState() != android.net.wifi.WifiManager.WIFI_STATE_ENABLED) {
                Thread.sleep(100);
            }
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void enable_vpn_ap(android.net.wifi.WifiManager wifi_manager) {
        if (wifi_manager.isWifiEnabled()) {
            disable_vpn_ap(wifi_manager);
        }
        Paper.book("temp").write("wifi_open", true);
        com.qwe7002.telegram_rc.root_kit.network.wifi_set_enable(true);
        try {
            while (wifi_manager.getWifiState() != android.net.wifi.WifiManager.WIFI_STATE_ENABLED) {
                Thread.sleep(100);
            }
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        com.qwe7002.telegram_rc.root_kit.activity_manage.start_foreground_service(const_value.VPN_HOTSPOT_PACKAGE_NAME, const_value.VPN_HOTSPOT_PACKAGE_NAME + ".RepeaterService");
    }

    public static void force_switch_nic_tether(Context context) {
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            //noinspection deprecation
            if (!powerManager.isScreenOn()) {
                com.qwe7002.telegram_rc.root_kit.shell.run_shell_command("input keyevent 26");
                Thread.sleep(1000);
            }
            com.qwe7002.telegram_rc.root_kit.activity_manage.start_activity("com.android.settings", ".TetherSettings");
            Thread.sleep(3000);
            com.qwe7002.telegram_rc.root_kit.shell.run_shell_command("input keyevent 20");
            Thread.sleep(1000);
            com.qwe7002.telegram_rc.root_kit.shell.run_shell_command("input keyevent 20");
            Thread.sleep(1000);
            com.qwe7002.telegram_rc.root_kit.shell.run_shell_command("input keyevent 20");
            Thread.sleep(1000);
            com.qwe7002.telegram_rc.root_kit.shell.run_shell_command("input keyevent 20");
            Thread.sleep(1000);
            com.qwe7002.telegram_rc.root_kit.shell.run_shell_command("input keyevent 66");
            Thread.sleep(1000);
            com.qwe7002.telegram_rc.root_kit.shell.run_shell_command("input keyevent 4");
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void enable_tether(Context context, int mode) {
        Paper.book("temp").write("tether_open", true);
        TetherManager manager = new TetherManager(context);
        manager.startTethering(mode, null);
    }

    public static void disable_tether(Context context, int mode) {
        Paper.book("temp").write("tether_open", false);
        TetherManager manager = new TetherManager(context);
        manager.stopTethering(mode);
    }

    public static boolean is_tether_active(Context context) {
        TetherManager manager = new TetherManager(context);
        Paper.book("temp").write("tether_open", manager.isTetherActive());
        return manager.isTetherActive();
    }


    public static boolean is_vpn_hotsport_exist(@NotNull Context context) {
        ApplicationInfo info;
        try {
            info = context.getPackageManager().getApplicationInfo(const_value.VPN_HOTSPOT_PACKAGE_NAME, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            info = null;
        }

        return info != null;
    }

    public static boolean is_termux_exist(@NotNull Context context) {
        ApplicationInfo info;
        try {
            info = context.getPackageManager().getApplicationInfo(const_value.TERMUX_PACKAGE_NAME, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            info = null;
        }

        return info != null;
    }

    public static boolean is_data_usage_access(@NotNull Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

}
