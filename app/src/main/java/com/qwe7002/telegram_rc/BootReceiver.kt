package com.qwe7002.telegram_rc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.LogManage
import com.qwe7002.telegram_rc.static_class.ServiceManage
import com.tencent.mmkv.MMKV

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val TAG = this::class.java.simpleName
        try {
            MMKV.initialize(context)
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
                Log.w(TAG, "Failed to clear status MMKV: ${e.message}")
            }
            
            Log.d(TAG, "BootReceiver finished processing.")
        } catch (e: Exception) {
            Log.e(TAG, "Error in BootReceiver: ${e.message}", e)
        }
    }
}

