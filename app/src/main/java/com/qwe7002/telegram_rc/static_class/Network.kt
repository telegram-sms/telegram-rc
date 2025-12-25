@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_rc.static_class

import android.Manifest
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Context.TELEPHONY_SERVICE
import android.content.Context.TELEPHONY_SUBSCRIPTION_SERVICE
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.fitc.wifihotspot.TetherManager
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.Other.getActiveCard
import com.tencent.mmkv.MMKV
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.jetbrains.annotations.Contract
import java.net.Authenticator
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object Network {
    private const val DNS_OVER_HTTP = "https://cloudflare-dns.com/dns-query"

    @JvmStatic
    fun getDataEnable(context: Context): Boolean {
        val connectivityManager = (context.getSystemService(
            CONNECTIVITY_SERVICE
        ) as ConnectivityManager)
        var networkStatus = false
        val networks = connectivityManager.allNetworks
        for (network in networks) {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            Log.d(Const.TAG, "check_network_status: $networkCapabilities")
            assert(networkCapabilities != null)
            if (networkCapabilities!!.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                networkStatus = true
            }
        }
        return networkStatus
    }

    @JvmStatic
    fun checkNetworkStatus(context: Context): Boolean {
        val manager = context.getSystemService(
            CONNECTIVITY_SERVICE
        ) as ConnectivityManager
        var networkStatus = false
        val networks = manager.allNetworks
        for (network in networks) {
            val networkCapabilities = manager.getNetworkCapabilities(network)
            Log.d(Const.TAG, "check_network_status: $networkCapabilities")
            assert(networkCapabilities != null)
            if (networkCapabilities!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                networkStatus = true
            }
        }
        return networkStatus
    }

    @JvmStatic
    @Contract(pure = true)
    fun getUrl(token: String, func: String): String {
        val api = MMKV.defaultMMKV().getString("api_address", "api.telegram.org")
        return "https://$api/bot$token/$func"
    }

    @JvmStatic
    fun getOkhttpObj(): OkHttpClient {
        val doh = MMKV.defaultMMKV().getBoolean("doh_switch", true)

        val proxyConfig = MMKV.mmkvWithID(Const.PROXY_MMKV_ID)
        val okhttp: OkHttpClient.Builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        lateinit var proxy: Proxy
        if (proxyConfig.getBoolean("enabled", false)) {
            val policy = ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)
            val inetSocketAddress = InetSocketAddress(
                proxyConfig.getString("host", ""),
                proxyConfig.getInt("port", 1080)
            )
            proxy = Proxy(Proxy.Type.SOCKS, inetSocketAddress)
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    if (requestingHost.equals(
                            proxyConfig.getString("host", ""),
                            ignoreCase = true
                        )
                    ) {
                        if (proxyConfig.getInt("port", 1080) == requestingPort) {
                            return PasswordAuthentication(
                                proxyConfig.getString("username", ""),
                                proxyConfig.getString("password", "").toString().toCharArray()
                            )
                        }
                    }
                    return null
                }
            })
            okhttp.proxy(proxy)
        }
        if (doh) {
            val dohHttpClient: OkHttpClient.Builder =
                OkHttpClient.Builder().retryOnConnectionFailure(true)
            okhttp.dns(
                DnsOverHttps.Builder().client(dohHttpClient.build())
                    .url(DNS_OVER_HTTP.toHttpUrl())
                    .bootstrapDnsHosts(
                        getByIp("2606:4700:4700::1001"),
                        getByIp("2606:4700:4700::1111"),
                        getByIp("1.0.0.1"),
                        getByIp("1.1.1.1")
                    )
                    .includeIPv6(true)
                    .build()
            )
        }
        return okhttp.build()
    }

    private fun getByIp(host: String): InetAddress {
        try {
            return InetAddress.getByName(host)
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun checkCellularNetworkType(
        context: Context,
        telephony: TelephonyManager
    ): String {
        var netType = "Unknown"
        when (telephony.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_NR,
            TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN -> {
                // For 5G (NR) we always try to determine the exact type if possible
                // For LTE and IWLAN, we check if it's actually 5G in disguise
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    netType = check5GState(context, telephony)
                } else {
                    // Without location permission, we can only provide basic info
                    netType = when (telephony.dataNetworkType) {
                        TelephonyManager.NETWORK_TYPE_NR -> "NR"
                        TelephonyManager.NETWORK_TYPE_LTE,
                        TelephonyManager.NETWORK_TYPE_IWLAN -> "LTE"

                        else -> "Unknown" // Fallback, though shouldn't happen
                    }
                }
            }

            // 3G network types
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA,
            TelephonyManager.NETWORK_TYPE_UMTS -> netType = "3G"

            // 2G network types
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> netType = "2G"

            TelephonyManager.NETWORK_TYPE_UNKNOWN -> netType = "Unknown"
        }
        return netType
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE])
    private fun check5GState(context: Context, telephonyManager: TelephonyManager): String {
        val cellInfoList =
        // For Android Q+, we'll use a callback-based approach with requestCellInfoUpdate
            // This will request updated cell info, but updates are rate-limited
            requestUpdatedCellInfo(context, telephonyManager)

        if (cellInfoList.isEmpty()) {
            Log.d(Const.TAG, "No cell info available")
            return when (telephonyManager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "NR"
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyManager.NETWORK_TYPE_IWLAN -> "LTE"

                else -> "Unknown" // Fallback, though shouldn't happen
            }
        }

        var hasLte = false
        var hasNr = false

        var lteCellCount = 0
        var nrCellCount = 0

        for (cellInfo in cellInfoList) {
            if (cellInfo.isRegistered) {
                when (cellInfo) {
                    is CellInfoLte -> {
                        hasLte = true
                        lteCellCount++
                    }

                    is CellInfoNr -> {
                        hasNr = true
                        nrCellCount++
                    }
                }
            }
        }

        // Determine the specific network type
        return when {
            hasLte && hasNr -> {
                // LTE and NR both registered = EN-DC (NR NSA)
                "NR NSA"
            }

            hasNr && nrCellCount > 1 -> {
                // 5G networks with advanced capabilities
                "NR Advanced"
            }

            hasNr -> {
                // Standard 5G networks
                "NR SA"
            }

            hasLte && lteCellCount > 1 -> {
                // LTE with carrier aggregation = LTE-A
                "LTE-A"
            }

            hasLte -> {
                // Plain LTE
                "LTE"
            }

            else -> {
                // Fallback
                when (telephonyManager.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "NR"
                    TelephonyManager.NETWORK_TYPE_LTE,
                    TelephonyManager.NETWORK_TYPE_IWLAN -> "LTE"

                    else -> "Unknown"
                }
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE])
    fun requestUpdatedCellInfo(
        context: Context,
        telephonyManager: TelephonyManager
    ): List<CellInfo> {
        // On Android Q+, we request updated cell info, but this is rate-limited
        // If the request fails or times out, we fall back to the cached data
        var result: List<CellInfo> = telephonyManager.allCellInfo
        val latch = CountDownLatch(1)

        try {
            telephonyManager.requestCellInfoUpdate(
                context.mainExecutor,
                object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cells: List<CellInfo>) {
                        result = cells
                        latch.countDown()
                    }

                    override fun onError(errorCode: Int, detail: Throwable?) {
                        Log.w(
                            Const.TAG,
                            "Failed to get updated cell info. Error code: $errorCode",
                            detail
                        )
                        latch.countDown()
                    }
                })

            // Wait up to 2 seconds for the update
            latch.await(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(Const.TAG, "Exception while requesting cell info update", e)
        }

        return result
    }

    fun getNetworkType(context: Context): String {
        var netType = "Unknown"
        val connectManager =
            checkNotNull(context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
        var telephonyManager : TelephonyManager
        if(getActiveCard(context) == 2){
            Log.d(Const.TAG, "Dual SIM detected")
            telephonyManager = checkNotNull(
                context
                    .getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            ).createForSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId())
        }else{
            Log.d(Const.TAG, "Single SIM detected")
            telephonyManager = checkNotNull(
                context
                    .getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            )
        }

        // Check permission once before entering loop
        val hasPhoneStatePermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        for (network in connectManager.allNetworks) {
            val networkCapabilities =
                checkNotNull(connectManager.getNetworkCapabilities(network))
            if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        netType = "WIFI"
                        break
                    }

                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        if (!hasPhoneStatePermission) {
                            Log.i(Const.TAG, "get_network_type: No permission.")
                            return netType
                        }
                        netType = checkCellularNetworkType(
                            context,
                            telephonyManager
                        )
                    }

                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> {
                        netType = "Bluetooth"
                    }

                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                        netType = "Ethernet"
                    }

                    else -> {
                        // Handle other transport types if needed
                        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)) {
                            netType = "LowPAN"
                        }
                    }
                }
            }
        }

        return netType
    }

    fun getHotspotIpAddress(type: Int): String {
        // Get all network interfaces
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            try {
                val networkInterface = interfaces.nextElement()
                val interfaceName = networkInterface.name
                val prefix = when (type) {
                    TetherManager.TetherMode.TETHERING_BLUETOOTH -> "bnep"
                    TetherManager.TetherMode.TETHERING_USB -> "rndis"
                    TetherManager.TetherMode.TETHERING_ETHERNET -> "eth"
                    else -> "wlan"
                }
                if (interfaceName.startsWith(prefix)) {
                    Log.d(Const.TAG, "Checking interface: $interfaceName")
                    val addresses = networkInterface.inetAddresses

                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            Log.d(
                                Const.TAG,
                                "Found IP on $interfaceName: ${address.hostAddress}"
                            )
                            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                            return address.hostAddress
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(Const.TAG, "Error getting hotspot IP: ${e.message}")
            }
        }
        return "Unknown"
    }
}
