package com.qwe7002.telegram_rc.static_class

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.fitc.wifihotspot.TetherManager
import com.qwe7002.telegram_rc.value.TAG
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

object MdnsResponder {
    private const val HOSTNAME = "router"
    private const val SERVICE_TYPE = "_http._tcp."
    private const val SERVICE_PORT = 80
    private const val MAX_IP_RETRIES = 10
    private const val IP_RETRY_DELAY_MS = 1000L

    private val running = AtomicBoolean(false)
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var startThread: Thread? = null

    @JvmStatic
    fun start(context: Context) {
        // setHostname / setHostAddresses on NsdServiceInfo require Android 14+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.w(TAG, "MdnsResponder: custom hostname requires Android 14+, skipping")
            return
        }
        if (!running.compareAndSet(false, true)) {
            Log.d(TAG, "MdnsResponder: already running")
            return
        }
        val appContext = context.applicationContext
        val thread = Thread {
            try {
                var ip = "Unknown"
                for (i in 1..MAX_IP_RETRIES) {
                    if (Thread.currentThread().isInterrupted) return@Thread
                    ip = Network.getHotspotIpAddress(TetherManager.TetherMode.TETHERING_WIFI)
                    if (ip != "Unknown") break
                    try {
                        Thread.sleep(IP_RETRY_DELAY_MS)
                    } catch (e: InterruptedException) {
                        Log.d(TAG, "MdnsResponder: startup interrupted")
                        Thread.currentThread().interrupt()
                        return@Thread
                    }
                }
                if (ip == "Unknown") {
                    Log.w(TAG, "MdnsResponder: could not resolve hotspot IP, abort")
                    running.set(false)
                    return@Thread
                }
                register(appContext, ip)
            } catch (e: Exception) {
                Log.e(TAG, "MdnsResponder: failed to start", e)
                cleanup(appContext)
            }
        }
        thread.isDaemon = true
        thread.name = "mdns-responder-start"
        startThread = thread
        thread.start()
    }

    @JvmStatic
    fun stop(context: Context) {
        if (!running.getAndSet(false)) return
        cleanup(context.applicationContext)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @SuppressLint("WifiManagerLeak")
    private fun register(context: Context, ip: String) {
        val addr: InetAddress = Inet4Address.getByName(ip)

        val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("mdns_responder").apply {
            setReferenceCounted(false)
            acquire()
        }
        multicastLock = lock

        val info = NsdServiceInfo().apply {
            serviceName = HOSTNAME
            serviceType = SERVICE_TYPE
            hostname = HOSTNAME
            hostAddresses = listOf(addr)
            port = SERVICE_PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "MdnsResponder: registered ${info.hostname}.local -> $ip")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "MdnsResponder: registration failed code=$errorCode")
                running.set(false)
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "MdnsResponder: unregistered")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "MdnsResponder: unregistration failed code=$errorCode")
            }
        }
        val nsdManager = context.getSystemService(NsdManager::class.java)
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        registrationListener = listener
    }

    private fun cleanup(context: Context) {
        startThread?.interrupt()
        startThread = null
        registrationListener?.let { listener ->
            try {
                val nsdManager = context.getSystemService(NsdManager::class.java)
                nsdManager?.unregisterService(listener)
            } catch (e: Exception) {
                Log.e(TAG, "MdnsResponder: error unregistering", e)
            }
        }
        registrationListener = null
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
        multicastLock = null
    }
}
