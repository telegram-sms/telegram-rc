package com.qwe7002.telegram_rc

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.qwe7002.telegram_rc.static_class.Hotspot
import com.qwe7002.telegram_rc.static_class.MdnsResponder
import com.qwe7002.telegram_rc.static_class.Notify
import com.qwe7002.telegram_rc.static_class.Other.getNotificationObj
import com.qwe7002.telegram_rc.value.TAG

class MdnsResponderService : Service() {

    override fun onCreate() {
        super.onCreate()
        val notification = getNotificationObj(
            applicationContext, getString(R.string.mdns_service_name)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notify.MDNS_RESPONDER,
                notification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(Notify.MDNS_RESPONDER, notification.build())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MdnsResponder.start(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        MdnsResponder.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @JvmStatic
        fun start(context: Context) {
            try {
                val intent = Intent(context, MdnsResponderService::class.java)
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "MdnsResponderService: failed to start", e)
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, MdnsResponderService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "MdnsResponderService: failed to stop", e)
            }
        }

        // Reconcile the service with the current hotspot state. Safe to call
        // from any entry point (boot, service restart, app upgrade) — starts
        // the responder if the Wi-Fi hotspot is on, stops it otherwise.
        @JvmStatic
        fun syncWithHotspot(context: Context) {
            if (Hotspot.isHotspotActive(context)) start(context) else stop(context)
        }
    }
}
