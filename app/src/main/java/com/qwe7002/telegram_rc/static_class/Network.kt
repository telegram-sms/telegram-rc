package com.qwe7002.telegram_rc.static_class;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.StrictMode;
import android.util.Log;

import com.qwe7002.telegram_rc.config.proxy;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import io.paperdb.Paper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

public class network {
    private static final String TELEGRAM_API_DOMAIN = "api.telegram.org";
    private static final String DNS_OVER_HTTP_ADDRSS = "https://cloudflare-dns.com/dns-query";

    public static boolean getDataEnable(@NotNull Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        assert connectivityManager != null;
        boolean network_status = false;
        android.net.Network[] networks = connectivityManager.getAllNetworks();
        for (android.net.Network network : networks) {
            NetworkCapabilities network_capabilities = connectivityManager.getNetworkCapabilities(network);
            Log.d("check_network_status", "check_network_status: " + network_capabilities);
            assert network_capabilities != null;
            if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                network_status = true;
            }
        }
        return network_status;
    }

    public static boolean checkNetworkStatus(@NotNull Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        boolean network_status = false;
        assert manager != null;
        android.net.Network[] networks = manager.getAllNetworks();
        for (android.net.Network network : networks) {
            NetworkCapabilities network_capabilities = manager.getNetworkCapabilities(network);
            Log.d("check_network_status", "check_network_status: " + network_capabilities);
            assert network_capabilities != null;
            if (network_capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                network_status = true;
            }
        }
        return network_status;
    }

    @NotNull
    @Contract(pure = true)
    public static String getUrl(String token, String func) {
        return "https://" + TELEGRAM_API_DOMAIN + "/bot" + token + "/" + func;
    }

    @NotNull
    public static OkHttpClient getOkhttpObj(boolean dohSwitch) {
        proxy proxyConfig = Paper.book("system_config").read("proxy_config", new proxy());
        OkHttpClient.Builder okhttp = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        Proxy proxy = null;
        assert proxyConfig != null;
        if (proxyConfig.enable) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
            InetSocketAddress proxyAddr = new InetSocketAddress(proxyConfig.host, proxyConfig.port);
            proxy = new Proxy(Proxy.Type.SOCKS, proxyAddr);
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestingHost().equalsIgnoreCase(proxyConfig.host)) {
                        if (proxyConfig.port == getRequestingPort()) {
                            return new PasswordAuthentication(proxyConfig.username, proxyConfig.password.toCharArray());
                        }
                    }
                    return null;
                }
            });
            okhttp.proxy(proxy);
            dohSwitch = true;
        }
        if (dohSwitch) {
            OkHttpClient.Builder dohHttpClient = new OkHttpClient.Builder().retryOnConnectionFailure(true);
            if (proxyConfig.enable && proxyConfig.dns_over_socks5) {
                dohHttpClient.proxy(proxy);
            }
            okhttp.dns(new DnsOverHttps.Builder().client(dohHttpClient.build())
                    .url(HttpUrl.get(DNS_OVER_HTTP_ADDRSS))
                    .bootstrapDnsHosts(getByIp("2606:4700:4700::1001"), getByIp("2606:4700:4700::1111"), getByIp("1.0.0.1"), getByIp("1.1.1.1"))
                    .includeIPv6(true)
                    .build());
        }
        return okhttp.build();
    }

    private static InetAddress getByIp(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
