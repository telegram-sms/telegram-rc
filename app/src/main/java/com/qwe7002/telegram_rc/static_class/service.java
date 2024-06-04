package com.qwe7002.telegram_rc.static_class;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import com.qwe7002.telegram_rc.BatteryService;
import com.qwe7002.telegram_rc.BeaconReceiverService;
import com.qwe7002.telegram_rc.chat_command_service;
import com.qwe7002.telegram_rc.NotificationListenerService;

import java.util.Set;

public class service {
    public static void stopAllService(Context context) {
        Intent intent = new Intent(CONST.BROADCAST_STOP_SERVICE);
        context.sendBroadcast(intent);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.e("Service", "stopAllService: ", e);
        }
    }

    public static void startService(Context context, Boolean batterySwitch, Boolean chatCommandSwitch) {


        if (isNotifyListener(context)) {
            ComponentName this_component_name = new ComponentName(context, NotificationListenerService.class);
            PackageManager packageManager = context.getPackageManager();
            packageManager.setComponentEnabledSetting(this_component_name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            packageManager.setComponentEnabledSetting(this_component_name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }
        if (batterySwitch) {
            Intent batteryService = new Intent(context, BatteryService.class);
            context.startForegroundService(batteryService);
        }
        if (chatCommandSwitch) {
            Intent chatLongPollingService = new Intent(context, chat_command_service.class);
            context.startForegroundService(chatLongPollingService);
        }

    }

    public static void startBeaconService(Context context) {

        Intent beacon_service = new Intent(context, BeaconReceiverService.class);
        context.startForegroundService(beacon_service);

    }

    public static boolean isNotifyListener(Context context) {
        Set<String> packageNames = NotificationManagerCompat.getEnabledListenerPackages(context);
        return packageNames.contains(context.getPackageName());
    }
}
