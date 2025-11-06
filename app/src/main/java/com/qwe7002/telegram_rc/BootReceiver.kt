package com.qwe7002.telegram_rc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.LogManage
import com.qwe7002.telegram_rc.static_class.ServiceManage
import com.tencent.mmkv.MMKV
import rikka.shizuku.Shizuku

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val logTag = this::class.java.simpleName
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
                if(!Shizuku.pingBinder()){
                    MMKV.mmkvWithID(Const.BEACON_MMKV_ID).putBoolean("beacon_enable", false)
                    LogManage.writeLog(context, "Shizuku not connected, Beacon service disabled.")
                }
                KeepAliveJob.startJob(context)
                ReSendJob.startJob(context)
            }

            MMKV.mmkvWithID(Const.STATUS_MMKV_ID).clear()
            LogManage.writeLog(context, "Cleared status MMKV")
            
            Log.d(logTag, "BootReceiver finished processing.")
        } catch (e: Exception) {
            LogManage.writeLog(context, "Error in BootReceiver: ${e.message}")
        }
    }
}

