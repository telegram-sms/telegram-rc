package com.qwe7002.telegram_rc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qwe7002.telegram_rc.root_kit.network
import com.qwe7002.telegram_rc.static_class.log
import com.qwe7002.telegram_rc.static_class.resend
import com.qwe7002.telegram_rc.static_class.service
import io.paperdb.Paper
import kotlin.Any
import kotlin.String

class bootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val TAG = "boot_receiver"
        Log.d(TAG, "Receive action: " + intent.action)
        Paper.init(context)
        val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        if (sharedPreferences.getBoolean("initialized", false)) {
            log.writeLog(
                context,
                "Received [" + intent.action + "] broadcast, starting background service."
            )
            service.startService(
                context,
                sharedPreferences.getBoolean("battery_monitoring_switch", false),
                sharedPreferences.getBoolean("chat_command", false)
            )
            service.startBeaconService(context)
            keepAliveJob.startJob(context)
            if (Paper.book().read<ArrayList<Any>>("resend_list", ArrayList())!!.isNotEmpty()) {
                Log.d(
                    TAG,
                    "An unsent message was detected, and the automatic resend process was initiated."
                )
                resend.start_resend_service(context)
            }
            if (sharedPreferences.getBoolean("root", false)) {
                if (Paper.book("system_config").contains("dummy_ip_addr")) {
                    val dummyIpAddr = Paper.book("system_config").read<String>("dummy_ip_addr")
                    network.addDummyDevice(dummyIpAddr)
                }
            }
        }
        Paper.book("temp").destroy()
    }
}

