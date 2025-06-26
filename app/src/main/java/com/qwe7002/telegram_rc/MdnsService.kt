package com.qwe7002.telegram_rc

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.qwe7002.telegram_rc.static_class.Other.getNotificationObj
import java.net.InetAddress

class MdnsService : Service() {
    private val TAG = "MdnsService"
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
        val notification = getNotificationObj(
            applicationContext, "mDNS Service "
        ).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )

        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

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
        Log.d(TAG, "registerMdnsService: $gatewayIp")
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "HotspotGateway"
            serviceType = "_http._tcp."
            port = 2525
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
