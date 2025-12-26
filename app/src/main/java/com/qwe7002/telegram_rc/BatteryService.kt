package com.qwe7002.telegram_rc

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.fitc.wifihotspot.TetherManager
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.Notify
import com.qwe7002.telegram_rc.static_class.Other
import com.qwe7002.telegram_rc.static_class.Hotspot
import com.tencent.mmkv.MMKV
import com.tencent.mmkv.MMKVLogLevel
import java.util.Objects

class BatteryService : Service() {
    private lateinit var batteryReceiver: BatteryBroadcastReceiver

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification =
            Other.getNotificationObj(
                applicationContext,
                getString(R.string.battery_monitoring_notify)
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notify.BATTERY, notification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                Notify.BATTERY, notification.build()
            )
        }

        return START_STICKY
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(applicationContext)
        MMKV.setLogLevel(MMKVLogLevel.LevelWarning)
        val preferences = MMKV.defaultMMKV()
        val chargerStatus = preferences.getBoolean("charger_status", false)
        batteryReceiver = BatteryBroadcastReceiver()
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_BATTERY_OKAY)
        filter.addAction(Intent.ACTION_BATTERY_LOW)
        if (chargerStatus) {
            filter.addAction(Intent.ACTION_POWER_CONNECTED)
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }


    internal inner class BatteryBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(Const.TAG, "Receive action: " + intent.action)
            val builder = StringBuilder(context.getString(R.string.system_message_head) + "\n")
            val action = intent.action
            val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
            when (Objects.requireNonNull(action)) {
                Intent.ACTION_BATTERY_OKAY -> builder.append(context.getString(R.string.low_battery_status_end))
                Intent.ACTION_BATTERY_LOW -> {
                    builder.append(context.getString(R.string.battery_low))
                    if (Hotspot.isHotspotActive(context)) {
                        Hotspot.disableHotspot(
                            context,
                            TetherManager.TetherMode.TETHERING_WIFI
                        )
                        builder.append("\n").append(getString(R.string.disable_wifi))
                            .append(context.getString(R.string.action_success))
                    }
                }

                Intent.ACTION_POWER_CONNECTED -> builder.append(context.getString(R.string.charger_connect))

                Intent.ACTION_POWER_DISCONNECTED -> builder.append(context.getString(R.string.charger_disconnect))
            }
            var batteryLevel =
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (batteryLevel > 100) {
                Log.d(Const.TAG, "The previous battery is over 100%, and the correction is 100%.")
                batteryLevel = 100
            }
            val result =
                builder.append("\n").append(context.getString(R.string.current_battery_level))
                    .append(batteryLevel).append("%").toString()
            if (action == Intent.ACTION_BATTERY_LOW || action == Intent.ACTION_BATTERY_OKAY) {
                CcSendJob.startJob(context, context.getString(R.string.app_name), result)
            }
            BatteryNetworkJob.startJob(context, result, action)
        }
    }


}

