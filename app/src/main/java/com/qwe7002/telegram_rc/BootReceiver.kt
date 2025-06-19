package com.qwe7002.telegram_rc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qwe7002.telegram_rc.root_kit.Networks
import com.qwe7002.telegram_rc.static_class.LogManage
import com.qwe7002.telegram_rc.static_class.ServiceManage
import com.tencent.mmkv.MMKV
import io.paperdb.Paper
import kotlin.String

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val logTag = "boot_receiver"
        Log.d(logTag, "Receive action: " + intent.action)
        Paper.init(context)
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
                if (Paper.book("system_config").contains("dummy_ip_addr")) {
                    val dummyIp = Paper.book("system_config").read<String>("dummy_ip_addr")
                    Networks.addDummyDevice(dummyIp.toString())
                }
            }
        }
        Paper.book("temp").destroy()
    }
}

