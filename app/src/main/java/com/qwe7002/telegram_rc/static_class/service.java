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

public class service {
    public static void stopAllService(Context context) {
        Intent intent = new Intent(CONST.BROADCAST_STOP_SERVICE);
        context.sendBroadcast(intent);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void startService(Context context, Boolean battery_switch, Boolean chat_command_switch) {
        Intent battery_service = new Intent(context, com.qwe7002.telegram_rc.battery_service.class);
        Intent chat_long_polling_service = new Intent(context, chat_command_service.class);
        if (isNotifyListener(context)) {
            ComponentName this_component_name = new ComponentName(context, notification_listener_service.class);
            PackageManager packageManager = context.getPackageManager();
            packageManager.setComponentEnabledSetting(this_component_name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            packageManager.setComponentEnabledSetting(this_component_name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }
        if (battery_switch) {
            context.startForegroundService(battery_service);
        }
        if (chat_command_switch) {
            context.startForegroundService(chat_long_polling_service);
        }

    }

    public static void startBeaconService(Context context) {
        Intent beacon_service = new Intent(context, beacon_receiver_service.class);
        context.startForegroundService(beacon_service);

    }

    public static boolean isNotifyListener(Context context) {
        Set<String> packageNames = NotificationManagerCompat.getEnabledListenerPackages(context);
        return packageNames.contains(context.getPackageName());
    }
}
