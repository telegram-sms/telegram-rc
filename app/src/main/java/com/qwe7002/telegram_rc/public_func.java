package com.qwe7002.telegram_rc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.PermissionChecker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.dnsoverhttps.DnsOverHttps;

class public_func {
    static final String broadcast_stop_service = "com.qwe7002.telegram_rc.stop_all";
    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    static final String VPN_HOTSPOT_PACKAGE_NAME = "be.mygod.vpnhotspot";

    static boolean get_data_enable(Context context) {
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

    static int parse_int(String int_str) {
        int result = 0;
        try {
            result = Integer.parseInt(int_str);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            //Avoid errors caused by unconvertible inputs.
        }
        return result;
    }

    static String get_send_phone_number(String phone_number) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < phone_number.length(); ++i) {
            char c = phone_number.charAt(i);
            if (c == '+' || Character.isDigit(c)) {
                result.append(c);
            }
        }
        return result.toString();
    }

    static String get_dual_sim_card_display(Context context, int slot, boolean show_name) {
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


    static boolean check_network_status(Context context) {
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

    static String get_url(String token, String func) {
        return "https://api.telegram.org/bot" + token + "/" + func;
    }

    static OkHttpClient get_okhttp_obj(boolean doh_switch) {
        OkHttpClient.Builder okhttp = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        if (doh_switch) {
            okhttp.dns(new DnsOverHttps.Builder().client(new OkHttpClient.Builder().retryOnConnectionFailure(true).build())
                    .url(HttpUrl.get("https://cloudflare-dns.com/dns-query"))
                    .bootstrapDnsHosts(getByIp("1.0.0.1"), getByIp("9.9.9.9"), getByIp("185.222.222.222"), getByIp("2606:4700:4700::1001"), getByIp("2620:fe::fe"), getByIp("2a09::"))
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
            // unlikely
            throw new RuntimeException(e);
        }
    }

    static boolean is_phone_number(String str) {
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

    @SuppressLint("HardwareIds")
    static String get_network_type(Context context) {
        String net_type = "Unknown";
        ConnectivityManager connect_manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        assert connect_manager != null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Network[] networks = connect_manager.getAllNetworks();
            if (networks.length != 0) {
                for (Network network : networks) {
                    NetworkCapabilities network_capabilities = connect_manager.getNetworkCapabilities(network);
                    assert network_capabilities != null;
                    if (!network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            net_type = "WIFI";

                        }
                        if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                                boolean is_att = false;
                                assert telephonyManager != null;
                                SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                                assert subscriptionManager != null;
                                SubscriptionInfo info = subscriptionManager.getActiveSubscriptionInfo(SubscriptionManager.getDefaultDataSubscriptionId());
                                if (info != null) {
                                    is_att = info.getCarrierName().toString().contains("AT&T");
                                }
                                net_type = check_cellular_network_type(telephonyManager.getDataNetworkType(), is_att);
                            } else {
                                Log.d("get_network_type", "No permission.");
                            }
                        }
                        if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                            net_type = "Bluetooth";
                        }
                        if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                            net_type = "Ethernet";
                        }
                    }
                }
            }
        } else {
            //noinspection deprecation
            NetworkInfo network_info = connect_manager.getActiveNetworkInfo();
            if (network_info == null) {
                return net_type;
            }
            //noinspection deprecation
            switch (network_info.getType()) {
                //noinspection deprecation
                case ConnectivityManager.TYPE_WIFI:
                    net_type = "WIFI";
                    break;
                //noinspection deprecation
                case ConnectivityManager.TYPE_MOBILE:
                    boolean is_att = false;
                    TelephonyManager telephonyManager = (TelephonyManager) context
                            .getSystemService(Context.TELEPHONY_SERVICE);
                    assert telephonyManager != null;
                    Log.d("get_network_type", telephonyManager.getSubscriberId());
                    if (telephonyManager.getSubscriberId().startsWith("3104101")) {
                        is_att = true;
                    }
                    //noinspection deprecation
                    net_type = check_cellular_network_type(network_info.getSubtype(), is_att);
                    break;
            }
        }

        return net_type;
    }

    private static String check_cellular_network_type(int type, boolean is_att) {
        String net_type = "Unknown";
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_NR:
                net_type = "5G";
                if (is_att) {
                    net_type = "5G+";
                }
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                net_type = "LTE";
                if (is_att) {
                    net_type = "5G E";
                }
                break;
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                if (is_att) {
                    net_type = "4G";
                    break;
                }
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
            case TelephonyManager.NETWORK_TYPE_UMTS:
                net_type = "3G";
                break;
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                net_type = "2G";
                break;
        }
        return net_type;
    }

    @TargetApi(Build.VERSION_CODES.N)
    static String get_data_sim_id(Context context) {
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

    static void send_sms(Context context, String send_to, String content, int slot, int sub_id) {
        if (androidx.core.content.PermissionChecker.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PermissionChecker.PERMISSION_GRANTED) {
            Log.d("send_sms", "No permission.");
            return;
        }
        if (!is_phone_number(send_to)) {
            write_log(context, "[" + send_to + "] is an illegal phone number");
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = public_func.get_url(bot_token, "sendMessage");
        message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        android.telephony.SmsManager sms_manager;
        if (sub_id == -1) {
            sms_manager = SmsManager.getDefault();
        } else {
            sms_manager = SmsManager.getSmsManagerForSubscriptionId(sub_id);
        }
        String dual_sim = get_dual_sim_card_display(context, slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));
        String send_content = "[" + dual_sim + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + send_to + "\n" + context.getString(R.string.content) + content;
        String message_id = "-1";
        request_body.text = send_content + "\n" + context.getString(R.string.status) + context.getString(R.string.sending);
        Gson gson = new Gson();
        String request_body_raw = gson.toJson(request_body);
        RequestBody body = RequestBody.create(request_body_raw, public_func.JSON);
        OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        try {
            Response response = call.execute();
            if (response.code() != 200 || response.body() == null) {
                throw new IOException(String.valueOf(response.code()));
            }
            message_id = get_message_id(Objects.requireNonNull(response.body()).string());
        } catch (IOException e) {
            e.printStackTrace();
            public_func.write_log(context, "failed to send message:" + e.getMessage());
        }
        ArrayList<String> divideContents = sms_manager.divideMessage(content);
        ArrayList<PendingIntent> send_receiver_list = new ArrayList<>();
        IntentFilter filter = new IntentFilter("send_sms");
        BroadcastReceiver receiver = new sms_send_receiver();
        context.getApplicationContext().registerReceiver(receiver, filter);
        Intent sent_intent = new Intent("send_sms");
        sent_intent.putExtra("message_id", message_id);
        sent_intent.putExtra("message_text", send_content);
        sent_intent.putExtra("sub_id", sms_manager.getSubscriptionId());
        PendingIntent sentIntent = PendingIntent.getBroadcast(context, 0, sent_intent, PendingIntent.FLAG_CANCEL_CURRENT);
        send_receiver_list.add(sentIntent);
        sms_manager.sendMultipartTextMessage(send_to, null, divideContents, send_receiver_list, null);
    }

    static void send_fallback_sms(Context context, String content, int sub_id) {
        final String TAG = "send_fallback_sms";
        if (androidx.core.content.PermissionChecker.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PermissionChecker.PERMISSION_GRANTED) {
            Log.d(TAG, ": No permission.");
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        String trust_number = sharedPreferences.getString("trusted_phone_number", null);
        if (trust_number == null) {
            Log.i(TAG, "The trusted number is empty.");
            return;
        }
        if (!sharedPreferences.getBoolean("fallback_sms", false)) {
            Log.i(TAG, "Did not open the SMS to fall back.");
            return;
        }
        android.telephony.SmsManager sms_manager;
        if (sub_id == -1) {
            sms_manager = SmsManager.getDefault();
        } else {
            sms_manager = SmsManager.getSmsManagerForSubscriptionId(sub_id);
        }
        ArrayList<String> divideContents = sms_manager.divideMessage(content);
        sms_manager.sendMultipartTextMessage(trust_number, null, divideContents, null, null);
    }

    static String get_message_id(String result) {
        JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject().get("result").getAsJsonObject();
        return result_obj.get("message_id").getAsString();
    }

    static Notification get_notification_obj(Context context, String notification_name) {
        Notification.Builder notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notification_name, notification_name,
                    NotificationManager.IMPORTANCE_MIN);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(channel);
            notification = new Notification.Builder(context, notification_name);
        } else {
            //Notification generation method after O
            notification = new Notification.Builder(context).setPriority(Notification.PRIORITY_MIN);
        }
        notification.setAutoCancel(false)
                .setSmallIcon(R.drawable.ic_stat)
                .setOngoing(true)
                .setTicker(context.getString(R.string.app_name))
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(notification_name + context.getString(R.string.service_is_running));
        return notification.build();
    }

    static void stop_all_service(Context context) {
        Intent intent = new Intent(broadcast_stop_service);
        context.sendBroadcast(intent);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static void start_service(Context context, Boolean battery_switch, Boolean chat_command_switch) {
        Intent battery_service = new Intent(context, battery_service.class);
        Intent chat_long_polling_service = new Intent(context, chat_command_service.class);
        if (is_notify_listener(context)) {
            ComponentName thisComponent = new ComponentName(context, notification_listener_service.class);
            PackageManager pm = context.getPackageManager();
            pm.setComponentEnabledSetting(thisComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(thisComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (battery_switch) {
                context.startForegroundService(battery_service);
            }
            if (chat_command_switch) {
                context.startForegroundService(chat_long_polling_service);
            }
        } else {
            if (battery_switch) {
                context.startService(battery_service);
            }
            if (chat_command_switch) {
                context.startService(chat_long_polling_service);
            }
        }
    }

    static int get_sub_id(Context context, int slot) {
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

    static int get_active_card(Context context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return -1;
        }
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        assert subscriptionManager != null;
        return subscriptionManager.getActiveSubscriptionInfoCount();
    }

    static String get_sim_display_name(Context context, int slot) {
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


    static void write_log(Context context, String log) {
        Log.i("write_log", log);
        int new_file_mode = Context.MODE_APPEND;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.UK);
        String write_string = "\n" + simpleDateFormat.format(new Date(System.currentTimeMillis())) + " " + log;
        write_file(context, "error.log", write_string, new_file_mode);
    }

    static String read_log(Context context, int line) {
        String result = context.getString(R.string.no_logs);
        String log_content = public_func.read_file_last_line(context, "error.log", line);
        if (!log_content.isEmpty()) {
            result = log_content;
        }
        return result;
    }

    @SuppressWarnings("WeakerAccess")
    static String read_file_last_line(Context context, @SuppressWarnings("SameParameterValue") String file, int line) {
        String TAG = "read_file_last_line";
        StringBuilder builder = new StringBuilder();
        FileInputStream file_stream = null;
        FileChannel channel = null;
        try {
            file_stream = context.openFileInput(file);
            channel = file_stream.getChannel();
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            buffer.position((int) channel.size());
            int count = 0;
            for (long i = channel.size() - 1; i >= 0; i--) {
                char c = (char) buffer.get((int) i);
                builder.insert(0, c);
                if (c == '\n') {
                    if (count == (line - 1)) {
                        break;
                    }
                    ++count;
                }
            }
            return builder.toString();
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Unable to read the file.");
            return "";
        } finally {
            try {
                if (file_stream != null) {
                    file_stream.close();
                }
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static void write_file(Context context, String file_name, String write_string, int mode) {
        FileOutputStream file_stream = null;
        try {
            file_stream = context.openFileOutput(file_name, mode);
            byte[] bytes = write_string.getBytes();
            file_stream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (file_stream != null) {
                try {
                    file_stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static void add_message_list(String message_id, String phone, int slot, int sub_id) {
        message_item item = new message_item();
        item.phone = phone;
        item.card = slot;
        item.sub_id = sub_id;
        Paper.book().write(message_id, item);
        Log.d("add_message_list", "add_message_list: " + message_id);
    }

    static boolean is_notify_listener(Context context) {
        Set<String> packageNames = NotificationManagerCompat.getEnabledListenerPackages(context);
        return packageNames.contains(context.getPackageName());
    }
}
