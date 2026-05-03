package com.qwe7002.telegram_rc.static_class

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.fitc.wifihotspot.TetherManager
import com.qwe7002.telegram_rc.value.TAG
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

object MdnsResponder {
    private const val HOSTNAME = "router"
    private const val SERVICE_TYPE = "_http._tcp.local."
    private const val SERVICE_PORT = 80
    private const val MAX_IP_RETRIES = 10
    private const val IP_RETRY_DELAY_MS = 1000L

    private val running = AtomicBoolean(false)
    private val lifecycleLock = Any()

    @Volatile private var jmdns: JmDNS? = null
    @Volatile private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile private var startThread: Thread? = null

    @JvmStatic
    fun start(context: Context) {
        if (!running.compareAndSet(false, true)) {
            Log.d(TAG, "MdnsResponder: already running")
            return
        }
        val appContext = context.applicationContext
        val thread = Thread {
            try {
                var ip = "Unknown"
                for (i in 1..MAX_IP_RETRIES) {
                    if (!running.get() || Thread.currentThread().isInterrupted) return@Thread
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
                // Hold the lock across register() so a concurrent stop() either
                // runs cleanup before we acquire any resources, or waits for
                // register to finish and then tears the same instance down.
                synchronized(lifecycleLock) {
                    if (Thread.currentThread() != startThread || !running.get()) return@synchronized
                    register(appContext, ip)
                }
            } catch (e: Exception) {
                Log.e(TAG, "MdnsResponder: failed to start", e)
                running.set(false)
                synchronized(lifecycleLock) { cleanup() }
            }
        }
        thread.isDaemon = true
        thread.name = "mdns-responder-start"
        startThread = thread
        thread.start()
    }

    @JvmStatic
    fun stop() {
        if (!running.getAndSet(false)) return
        synchronized(lifecycleLock) { cleanup() }
    }

    @SuppressLint("WifiManagerLeak")
    private fun register(context: Context, ip: String) {
        val addr: InetAddress = InetAddress.getByName(ip)

        val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("mdns_responder").apply {
            setReferenceCounted(false)
            acquire()
        }
        // Store before JmDNS.create so cleanup() can release on later failure.
        multicastLock = lock

        // JmDNS binds the host record to "<HOSTNAME>.local" using the supplied
        // address, so both "router.local" and "router._http._tcp.local" resolve
        // to the hotspot IP without needing the @SystemApi NsdManager hostname.
        val instance = JmDNS.create(addr, HOSTNAME)
        jmdns = instance

        val service = ServiceInfo.create(SERVICE_TYPE, HOSTNAME, SERVICE_PORT, "")
        instance.registerService(service)
        Log.i(TAG, "MdnsResponder: registered $HOSTNAME.local -> $ip")
    }

    private fun cleanup() {
        startThread?.interrupt()
        startThread = null
        jmdns?.let {
            try {
                it.unregisterAllServices()
                it.close()
            } catch (e: Exception) {
                Log.e(TAG, "MdnsResponder: error closing jmdns", e)
            }
        }
        jmdns = null
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
        multicastLock = null
    }
}
