package com.qwe7002.telegram_rc.static_class;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.NotificationManagerCompat;

import com.qwe7002.telegram_rc.beacon_receiver_service;
import com.qwe7002.telegram_rc.chat_command_service;
import com.qwe7002.telegram_rc.notification_listener_service;

import java.util.Set;

public class service_func {
    public static void stop_all_service(Context context) {
        Intent intent = new Intent(const_value.BROADCAST_STOP_SERVICE);
        context.sendBroadcast(intent);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void start_service(Context context, Boolean battery_switch, Boolean chat_command_switch) {
        Intent battery_service = new Intent(context, com.qwe7002.telegram_rc.battery_service.class);
        Intent chat_long_polling_service = new Intent(context, chat_command_service.class);
        start_beacon_service(context);
        if (is_notify_listener(context)) {
            ComponentName this_component_name = new ComponentName(context, notification_listener_service.class);
            PackageManager package_manager = context.getPackageManager();
            package_manager.setComponentEnabledSetting(this_component_name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            package_manager.setComponentEnabledSetting(this_component_name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }
        if (battery_switch) {
            context.startForegroundService(battery_service);
        }
        if (chat_command_switch) {
            context.startForegroundService(chat_long_polling_service);
        }

    }

    public static void start_beacon_service(Context context) {
        Intent beacon_service = new Intent(context, beacon_receiver_service.class);
        context.startForegroundService(beacon_service);

    }

    public static boolean is_notify_listener(Context context) {
        Set<String> packageNames = NotificationManagerCompat.getEnabledListenerPackages(context);
        return packageNames.contains(context.getPackageName());
    }
}
