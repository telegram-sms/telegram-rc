package com.qwe7002.telegram_rc.static_class

import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.qwe7002.telegram_rc.R

object Battery {
    public fun getBatteryInfo(context: Context): String {
        val batteryManager =
            checkNotNull(context.getSystemService(BATTERY_SERVICE) as BatteryManager)
        var batteryLevel =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (batteryLevel > 100) {
            Log.i(
                "get_battery_info",
                "The previous battery is over 100%, and the correction is 100%."
            )
            batteryLevel = 100
        }
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = checkNotNull(context.registerReceiver(null, intentFilter))
        val chargeStatus = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val batteryStringBuilder = StringBuilder().append(batteryLevel).append("%")
        when (chargeStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> batteryStringBuilder.append(
                " ("
            ).append(context.getString(R.string.charging)).append(")")

            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> when (batteryStatus.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                -1
            )) {
                BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_PLUGGED_USB, BatteryManager.BATTERY_PLUGGED_WIRELESS -> batteryStringBuilder.append(
                    " ("
                ).append(context.getString(R.string.not_charging)).append(")")
            }
        }
        return batteryStringBuilder.toString()
    }
}
