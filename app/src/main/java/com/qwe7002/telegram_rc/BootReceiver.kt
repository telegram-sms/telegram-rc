package com.qwe7002.telegram_rc

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.LogManage
import com.qwe7002.telegram_rc.static_class.Notify
import com.qwe7002.telegram_rc.static_class.ServiceManage
import com.tencent.mmkv.MMKV
import com.tencent.mmkv.MMKVLogLevel
import rikka.shizuku.Shizuku

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val logTag = this::class.java.simpleName
        try {
            MMKV.initialize(context)
            MMKV.setLogLevel(MMKVLogLevel.LevelWarning)
            val preferences = MMKV.defaultMMKV()
            if (preferences.contains("initialized")) {
                LogManage.writeLog(
                    context,
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
                MMKV.mmkvWithID(Const.STATUS_MMKV_ID).clear()
            } catch (e: Exception) {
                Log.w(logTag, "Failed to clear status MMKV: ${e.message}")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    MMKV.mmkvWithID(Const.BEACON_MMKV_ID).putBoolean("beacon_enable", false)
                    LogManage.writeLog(context, "Shizuku not available, beacon disabled.")
                    Notify.sendMessage(context, "BootReceiver", "Shizuku not available, Beacon disabled.")
                }
            }
            Log.d(logTag, "BootReceiver finished processing.")
        } catch (e: Exception) {
            Log.e(logTag, "Error in BootReceiver: ${e.message}", e)
        }
    }
}

