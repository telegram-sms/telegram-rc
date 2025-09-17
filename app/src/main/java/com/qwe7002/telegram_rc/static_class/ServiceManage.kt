package com.qwe7002.telegram_rc.static_class

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.qwe7002.telegram_rc.BatteryService
import com.qwe7002.telegram_rc.BeaconReceiverService
import com.qwe7002.telegram_rc.NotifyListenerService
import com.qwe7002.telegram_rc.ChatService

object ServiceManage {
    @JvmStatic
    fun stopAllService(context: Context) {
        Log.d("Service", "stopAllService: stopping all services")
        context.stopService(Intent(context, BatteryService::class.java))
        context.stopService(Intent(context, ChatService::class.java))
        if (hasLocationPermissions(context)) {
            context.stopService(Intent(context, BeaconReceiverService::class.java))
        }
    }

    @JvmStatic
    fun startService(context: Context, batterySwitch: Boolean, chatCommandSwitch: Boolean) {
        if (isNotifyListener(context)) {
            val thisComponentName =
                ComponentName(context, NotifyListenerService::class.java)
            val packageManager = context.packageManager
            packageManager.setComponentEnabledSetting(
                thisComponentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            packageManager.setComponentEnabledSetting(
                thisComponentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        if (batterySwitch) {
            context.startForegroundService(Intent(context, BatteryService::class.java))
        }
        if (chatCommandSwitch) {
            context.startForegroundService(Intent(context, ChatService::class.java))
        }
    }

    @JvmStatic
    fun startBeaconService(context: Context) {
        if (hasLocationPermissions(context)) {
            context.startForegroundService(Intent(context, BeaconReceiverService::class.java))
        }
    }

    public fun hasLocationPermissions(context: Context): Boolean {
        val fineLocationPermission =
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocationPermission =
            context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)

        var hasPermission = fineLocationPermission == PackageManager.PERMISSION_GRANTED &&
                coarseLocationPermission == PackageManager.PERMISSION_GRANTED

        // For Android 10+, also check background location permission
        val backgroundLocationPermission =
            context.checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        hasPermission =
            hasPermission && backgroundLocationPermission == PackageManager.PERMISSION_GRANTED

        val foregroundServiceLocationPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                context.checkSelfPermission(android.Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            } else {
                PackageManager.PERMISSION_GRANTED
            }
        return hasPermission &&
                foregroundServiceLocationPermission == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun isNotifyListener(context: Context): Boolean {
        val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
        return packageNames.contains(context.packageName)
    }
}
