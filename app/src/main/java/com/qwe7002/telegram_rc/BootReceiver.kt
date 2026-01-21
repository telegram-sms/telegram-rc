package com.qwe7002.telegram_rc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.qwe7002.telegram_rc.MMKV.BEACON_MMKV_ID
import com.qwe7002.telegram_rc.MMKV.STATUS_MMKV_ID
import com.qwe7002.telegram_rc.static_class.Notify
import com.qwe7002.telegram_rc.static_class.ServiceManage
import com.qwe7002.telegram_rc.value.TAG
import com.tencent.mmkv.MMKV
import com.tencent.mmkv.MMKVLogLevel
import rikka.shizuku.Shizuku

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            MMKV.initialize(context)
            MMKV.setLogLevel(MMKVLogLevel.LevelWarning)
            val preferences = MMKV.defaultMMKV()
            if (preferences.contains("initialized")) {
                Log.i(
                    TAG,
                    "Received [" + intent.action + "] broadcast, starting background service."
                )
                ServiceManage.startService(
                    context,
                    preferences.getBoolean("battery_monitoring_switch", false),
                    preferences.getBoolean("chat_command", false)
                )
                ServiceManage.startBeaconService(context)
                KeepAliveJob.startJob(context)
                ReSendJob.startJob(context)
            }

            try {
                MMKV.mmkvWithID(STATUS_MMKV_ID).clear()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear status MMKV: ${e.message}")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    MMKV.mmkvWithID(BEACON_MMKV_ID).putBoolean("beacon_enable", false)
                    Log.w(TAG, "Shizuku not available, beacon disabled.")
                    if(preferences.getBoolean("beacon_switch", false)) {
                        Notify.sendMessage(context, "BootReceiver", "Shizuku not available, Beacon disabled.")
                        ReSendJob.addResendLoop(context, "Shizuku not available, Beacon disabled.")
                    }
                }
            }
            Log.d(TAG, "BootReceiver finished processing.")
        } catch (e: Exception) {
            Log.e(TAG, "Error in BootReceiver: ${e.message}", e)
        }
    }
}

