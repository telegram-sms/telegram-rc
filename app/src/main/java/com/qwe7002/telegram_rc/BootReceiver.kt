package com.qwe7002.telegram_rc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qwe7002.telegram_rc.root_kit.Networks
import com.qwe7002.telegram_rc.static_class.Const
import com.qwe7002.telegram_rc.static_class.LogManage
import com.qwe7002.telegram_rc.static_class.ServiceManage
import com.tencent.mmkv.MMKV

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val logTag = "boot_receiver"
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
            if (preferences.getBoolean("root", false)) {
                val rootMMKV = MMKV.mmkvWithID(Const.ROOT_MMKV_ID)
                if (rootMMKV.contains("dummy_ip_addr")) {
                    val dummyIp = rootMMKV.getString("dummy_ip_addr", "")
                    if(dummyIp?.isNotEmpty() == true) {
                        Networks.addDummyDevice(dummyIp)
                    }
                }
            }
        }
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).clear()
        Log.d(logTag, "BootReceiver finished processing.")
    }
}

