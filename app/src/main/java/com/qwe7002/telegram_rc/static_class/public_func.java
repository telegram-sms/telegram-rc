package com.qwe7002.telegram_rc.static_class;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.StrictMode;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qwe7002.telegram_rc.R;
import com.qwe7002.telegram_rc.beacon_receiver_service;
import com.qwe7002.telegram_rc.chat_command_service;
import com.qwe7002.telegram_rc.config.proxy;
import com.qwe7002.telegram_rc.data_structure.sms_request_info;
import com.qwe7002.telegram_rc.notification_listener_service;
import com.qwe7002.telegram_rc.resend_service;
import com.qwe7002.telegram_rc.wifi_connect_status_service;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.paperdb.Paper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;


public class public_func {
    private static final String TELEGRAM_API_DOMAIN = "api.telegram.org";
    private static final String DNS_OVER_HTTP_ADDRSS = "https://cloudflare-dns.com/dns-query";

    public static String get_nine_key_map_convert(String input) {
        final Map<Character, Integer> nine_key_map = new HashMap<Character, Integer>() {
            {
                put('A', 2);
                put('B', 2);
                put('C', 2);
                put('D', 3);
                put('E', 3);
                put('F', 3);
                put('G', 4);
                put('H', 4);
                put('I', 4);
                put('J', 5);
                put('K', 5);
                put('L', 5);
                put('M', 6);
                put('N', 6);
                put('O', 6);
                put('P', 7);
                put('Q', 7);
                put('R', 7);
                put('S', 7);
                put('T', 8);
                put('U', 8);
                put('V', 8);
                put('W', 9);
                put('X', 9);
                put('Y', 9);
                put('Z', 9);
            }
        };
        StringBuilder result_stringbuilder = new StringBuilder();
        char[] ussd_char_array = input.toUpperCase().toCharArray();
        for (char c : ussd_char_array) {
            if (Character.isUpperCase(c)) {
                result_stringbuilder.append(nine_key_map.get(c));
            } else {
                result_stringbuilder.append(c);
            }
        }
        return result_stringbuilder.toString();
    }

    public static boolean get_data_enable(@NotNull Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        assert connectivityManager != null;
        boolean network_status = false;
        Network[] networks = connectivityManager.getAllNetworks();
        if (networks.length != 0) {
            for (Network network : networks) {
                NetworkCapabilities network_capabilities = connectivityManager.getNetworkCapabilities(network);
                Log.d("check_network_status", "check_network_status: " + network_capabilities);
                assert network_capabilities != null;
                if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    network_status = true;
                }
            }
        }
        return network_status;
    }

    public static boolean check_network_status(@NotNull Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        boolean network_status = false;
        assert manager != null;
        Network[] networks = manager.getAllNetworks();
        if (networks.length != 0) {
            for (Network network : networks) {
                NetworkCapabilities network_capabilities = manager.getNetworkCapabilities(network);
                Log.d("check_network_status", "check_network_status: " + network_capabilities);
                assert network_capabilities != null;
                if (network_capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                    network_status = true;
                }
            }
        }
        return network_status;
    }

    public static long parse_string_to_long(String int_str) {
        long result = 0;
        try {
            result = Long.parseLong(int_str);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return result;
    }

    @NotNull
    public static String get_send_phone_number(@NotNull String phone_number) {
        phone_number = get_nine_key_map_convert(phone_number);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < phone_number.length(); ++i) {
            char c = phone_number.charAt(i);
            if (c == '+' || Character.isDigit(c)) {
                result.append(c);
            }
        }
        return result.toString();
    }

    public static String get_dual_sim_card_display(Context context, int slot, boolean show_name) {
        String dual_sim = "";
        if (slot == -1) {
            return dual_sim;
        }
        if (public_func.get_active_card(context) >= 2) {
            String result = "";
            if (show_name) {
                result = "(" + get_sim_display_name(context, slot) + ")";
            }
            dual_sim = "SIM" + (slot + 1) + result + " ";
        }
        return dual_sim;
    }

    @NotNull
    @Contract(pure = true)
    public static String get_url(String token, String func) {
        return "https://" + TELEGRAM_API_DOMAIN + "/bot" + token + "/" + func;
    }

    @NotNull
    public static OkHttpClient get_okhttp_obj(boolean doh_switch, proxy proxy_item) {
        OkHttpClient.Builder okhttp = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        Proxy proxy = null;
        if (proxy_item.enable) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
            InetSocketAddress proxyAddr = new InetSocketAddress(proxy_item.host, proxy_item.port);
            proxy = new Proxy(Proxy.Type.SOCKS, proxyAddr);
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestingHost().equalsIgnoreCase(proxy_item.host)) {
                        if (proxy_item.port == getRequestingPort()) {
                            return new PasswordAuthentication(proxy_item.username, proxy_item.password.toCharArray());
                        }
                    }
                    return null;
                }
            });
            okhttp.proxy(proxy);
            doh_switch = true;
        }
        if (doh_switch) {
            OkHttpClient.Builder doh_http_client = new OkHttpClient.Builder().retryOnConnectionFailure(true);
            if (proxy_item.enable && proxy_item.dns_over_socks5) {
                doh_http_client.proxy(proxy);
            }
            okhttp.dns(new DnsOverHttps.Builder().client(doh_http_client.build())
                    .url(HttpUrl.get(DNS_OVER_HTTP_ADDRSS))
                    .bootstrapDnsHosts(get_by_ip("2606:4700:4700::1001"), get_by_ip("2606:4700:4700::1111"), get_by_ip("1.0.0.1"), get_by_ip("1.1.1.1"))
                    .includeIPv6(true)
                    .build());
        }
        return okhttp.build();
    }

    private static InetAddress get_by_ip(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static boolean is_phone_number(@NotNull String str) {
        for (int i = str.length(); --i >= 0; ) {
            char c = str.charAt(i);
            if (c == '+') {
                continue;
            }
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    @TargetApi(Build.VERSION_CODES.N)
    public static String get_data_sim_id(Context context) {
        String result = "Unknown";
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.d("get_data_sim_id", "No permission.");
            return result;
        }
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        assert subscriptionManager != null;
        SubscriptionInfo info = subscriptionManager.getActiveSubscriptionInfo(SubscriptionManager.getDefaultDataSubscriptionId());
        if (info == null) {
            return result;
        }
        result = String.valueOf(info.getSimSlotIndex() + 1);
        return result;
    }


    public static void add_resend_loop(Context context, String message) {
        ArrayList<String> resend_list;
        resend_list = Paper.book().read("resend_list", new ArrayList<>());
        resend_list.add(message);
        Paper.book().write("resend_list", resend_list);
        start_resend_service(context);
    }

    public static void start_resend_service(Context context) {
        Intent intent = new Intent(context, resend_service.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static Long get_message_id(String result) {
        JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject().get("result").getAsJsonObject();
        return result_obj.get("message_id").getAsLong();
    }

    @NotNull
    public static Notification get_notification_obj(Context context, String notification_name) {
        NotificationChannel channel = new NotificationChannel(notification_name, notification_name,
                NotificationManager.IMPORTANCE_MIN);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(channel);
        Notification.Builder notification = new Notification.Builder(context, notification_name).setAutoCancel(false)
                .setSmallIcon(R.drawable.ic_stat)
                .setOngoing(true)
                .setTicker(context.getString(R.string.app_name))
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(notification_name + context.getString(R.string.service_is_running));
        return notification.build();
    }

    public static void stop_all_service(Context context) {
        Intent intent = new Intent(const_value.BROADCAST_STOP_SERVICE);
        context.sendBroadcast(intent);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void start_service(Context context, Boolean battery_switch, Boolean chat_command_switch, Boolean wifi_status_monitor_switch) {
        Intent battery_service = new Intent(context, com.qwe7002.telegram_rc.battery_service.class);
        Intent chat_long_polling_service = new Intent(context, chat_command_service.class);
        Intent wifi_connect_ststus_service = new Intent(context, wifi_connect_status_service.class);
        start_beacon_service(context);
        if (is_notify_listener(context)) {
            ComponentName this_component_name = new ComponentName(context, notification_listener_service.class);
            PackageManager package_manager = context.getPackageManager();
            package_manager.setComponentEnabledSetting(this_component_name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            package_manager.setComponentEnabledSetting(this_component_name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }
        if (wifi_status_monitor_switch) {
            context.startForegroundService(wifi_connect_ststus_service);
        }
        if (battery_switch) {
            context.startForegroundService(battery_service);
        }
        if (chat_command_switch) {
            context.startForegroundService(chat_long_polling_service);
        }

    }

    public static void start_beacon_service(Context context) {
        Intent beacon_service = new Intent(context, beacon_receiver_service.class);
        context.startForegroundService(beacon_service);

    }

    public static int get_sub_id(Context context, int slot) {
        int active_card = public_func.get_active_card(context);
        if (active_card >= 2) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                return -1;
            }
            SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            assert subscriptionManager != null;
            return subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot).getSubscriptionId();
        }
        return -1;
    }

    public static int get_active_card(Context context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return -1;
        }
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        assert subscriptionManager != null;
        return subscriptionManager.getActiveSubscriptionInfoCount();
    }

    public static String get_sim_display_name(Context context, int slot) {
        final String TAG = "get_sim_display_name";
        String result = "Unknown";
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No permission.");
            return result;
        }
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        assert subscriptionManager != null;
        SubscriptionInfo info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot);
        if (info == null) {
            Log.d(TAG, "The active card is in the second card slot.");
            if (get_active_card(context) == 1 && slot == 0) {
                info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(1);
            }
            if (info == null) {
                return result;
            }
            return result;
        }
        result = info.getDisplayName().toString();
        if (info.getDisplayName().toString().contains("CARD") || info.getDisplayName().toString().contains("SUB")) {
            result = info.getCarrierName().toString();
        }
        return result;
    }


    public static void add_message_list(long message_id, String phone, int slot) {
        sms_request_info item = new sms_request_info();
        item.phone = phone;
        item.card = slot;
        Paper.book().write(String.valueOf(message_id), item);
        Log.d("add_message_list", "add_message_list: " + message_id);
    }

    public static boolean is_notify_listener(Context context) {
        Set<String> packageNames = NotificationManagerCompat.getEnabledListenerPackages(context);
        return packageNames.contains(context.getPackageName());
    }
}
