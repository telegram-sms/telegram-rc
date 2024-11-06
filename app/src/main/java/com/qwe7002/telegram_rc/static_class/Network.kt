@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_rc.static_class

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import com.qwe7002.telegram_rc.config.proxy
import io.paperdb.Paper
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
            Context.CONNECTIVITY_SERVICE
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
            Context.CONNECTIVITY_SERVICE
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
    fun getOkhttpObj(dohSwitch: Boolean): OkHttpClient {
        var doh = dohSwitch
        val proxyConfig = Paper.book("system_config").read("proxy_config", proxy())
        val okhttp: OkHttpClient.Builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        lateinit var proxy: Proxy
        if (proxyConfig!!.enable) {
            val policy = ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)
            val inetSocketAddress = InetSocketAddress(proxyConfig.host, proxyConfig.port)
            proxy = Proxy(Proxy.Type.SOCKS, inetSocketAddress)
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    if (requestingHost.equals(proxyConfig.host, ignoreCase = true)) {
                        if (proxyConfig.port == requestingPort) {
                            return PasswordAuthentication(
                                proxyConfig.username,
                                proxyConfig.password.toCharArray()
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
}
