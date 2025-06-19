package com.qwe7002.telegram_rc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.net.InetAddress

class MdnsService : Service() {
    private val TAG = "MdnsService"
    private val CHANNEL_ID = "MdnsServiceChannel"
    private val NOTIFICATION_ID = 8
    private lateinit var nsdManager: NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    override fun onCreate() {
        super.onCreate()
        nsdManager = getSystemService(NSD_SERVICE) as NsdManager
        registerMdnsService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterMdnsService()
        stopForeground(true)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("mDNS Service")
            .setContentText("Broadcasting hotspot gateway IP")
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "mDNS Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun registerMdnsService() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo
        val gatewayIp = InetAddress.getByAddress(
            byteArrayOf(
                (dhcpInfo.gateway shr 0).toByte(),
                (dhcpInfo.gateway shr 8).toByte(),
                (dhcpInfo.gateway shr 16).toByte(),
                (dhcpInfo.gateway shr 24).toByte()
            )
        ).hostAddress ?: "0.0.0.0"

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "HotspotGateway"
            serviceType = "_http._tcp."
            port = 80
            setAttribute("gateway_ip", gatewayIp)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "服务注册成功: ${serviceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "服务注册失败: $errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "服务已注销: ${serviceInfo.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "服务注销失败: $errorCode")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun unregisterMdnsService() {
        registrationListener?.let {
            nsdManager.unregisterService(it)
        }
    }
}
