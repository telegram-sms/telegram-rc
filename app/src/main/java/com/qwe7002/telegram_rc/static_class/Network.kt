@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_rc.static_class

import android.Manifest
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Context.TELEPHONY_SERVICE
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.tencent.mmkv.MMKV
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import org.jetbrains.annotations.Contract
import java.net.Authenticator
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object Network {
    private const val TELEGRAM_API_DOMAIN = "api.telegram.org"
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
            Log.d("check_network_status", "check_network_status: $networkCapabilities")
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
            Log.d("check_network_status", "check_network_status: $networkCapabilities")
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
        return "https://$TELEGRAM_API_DOMAIN/bot$token/$func"
    }

    @JvmStatic
    fun getOkhttpObj(): OkHttpClient {
        var doh = MMKV.defaultMMKV().getBoolean("doh_switch", true)

        val proxyConfig = MMKV.mmkvWithID("proxy")
        val okhttp: OkHttpClient.Builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        lateinit var proxy: Proxy
        if (proxyConfig.getBoolean("enabled", false)) {
            val policy = ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)
            val inetSocketAddress = InetSocketAddress(proxyConfig.getString("host",""), proxyConfig.getInt("port", 1080))
            proxy = Proxy(Proxy.Type.SOCKS, inetSocketAddress)
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    if (requestingHost.equals(proxyConfig.getString("host",""), ignoreCase = true)) {
                        if (proxyConfig.getInt("port", 1080) == requestingPort) {
                            return PasswordAuthentication(
                                proxyConfig.getString("username",""),
                                proxyConfig.getString("password","").toString().toCharArray()
                            )
                        }
                    }
                    return null
                }
            })
            okhttp.proxy(proxy)
            doh = true
        }
        if (doh) {
            val dohHttpClient: OkHttpClient.Builder =OkHttpClient. Builder().retryOnConnectionFailure(true)
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
                netType = if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    check5GState(telephony)
                } else {
                    if (telephony.dataNetworkType == TelephonyManager.NETWORK_TYPE_NR) {
                        "5G"
                    } else {
                        "4G"
                    }
                }
            }

            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_TD_SCDMA, TelephonyManager.NETWORK_TYPE_UMTS -> netType =
                "3G"

            TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> netType =
                "2G"

            TelephonyManager.NETWORK_TYPE_GSM -> {
                "2G"
            }

            TelephonyManager.NETWORK_TYPE_UNKNOWN -> {
                "Unknown"
            }
        }
        return netType
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun check5GState(telephonyManager: TelephonyManager): String {
        val cellInfoList = telephonyManager.getAllCellInfo()
        var hasLte = false
        var hasNr = false

        for (cellInfo in cellInfoList) {
            if (cellInfo.isRegistered) {
                if (cellInfo is CellInfoLte) {
                    hasLte = true
                } else if (cellInfo is CellInfoNr) {
                    hasNr = true
                }
            }
        }

        if (hasLte && hasNr) {
            return "NSA NR"
        } else if (hasNr) {
            return "SA 5G"
        }
        return "LTE"
    }
    fun getNetworkType(context: Context): String {
        var netType = "Unknown"
        val connectManager =
            checkNotNull(context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
        val telephonyManager = checkNotNull(
            context
                .getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        )
        val networks = connectManager.allNetworks
        for (network in networks) {
            val networkCapabilities =
                checkNotNull(connectManager.getNetworkCapabilities(network))
            if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    netType = "WIFI"
                    break
                }
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_PHONE_STATE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.i("get_network_type", "No permission.")
                        return netType
                    }
                    netType = checkCellularNetworkType(
                        context,
                        telephonyManager
                    )
                }
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                    netType = "Bluetooth"
                }
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    netType = "Ethernet"
                }
            }
        }

        return netType
    }
}
