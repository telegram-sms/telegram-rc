package com.qwe7002.telegram_rc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.fitc.wifihotspot.TetherManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qwe7002.telegram_rc.data_structure.polling_json;
import com.qwe7002.telegram_rc.data_structure.reply_markup_keyboard;
import com.qwe7002.telegram_rc.data_structure.request_message;
import com.qwe7002.telegram_rc.data_structure.sms_request_info;
import com.qwe7002.telegram_rc.static_class.CONST;
import com.qwe7002.telegram_rc.static_class.log;
import com.qwe7002.telegram_rc.static_class.network;
import com.qwe7002.telegram_rc.static_class.notify;
import com.qwe7002.telegram_rc.static_class.other;
import com.qwe7002.telegram_rc.static_class.remote_control;
import com.qwe7002.telegram_rc.static_class.service;
import com.qwe7002.telegram_rc.static_class.sms;
import com.qwe7002.telegram_rc.static_class.ussd;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * @noinspection CallToPrintStackTrace
 */
@SuppressWarnings({"BusyWait", "ConstantConditions"})
public class chat_command_service extends Service {
    private static final String TAG = "chat_command";
    //Global counter
    private static long offset = 0;
    private static int send_sms_next_status = SEND_SMS_STATUS.STANDBY_STATUS;
    // global object
    private OkHttpClient okhttp_client;
    private String message_thread_id;
    private broadcast_receiver broadcast_receiver;
    private PowerManager.WakeLock wakelock;
    private WifiManager.WifiLock wifilock;
    private Context context;
    private SharedPreferences sharedPreferences;
    private ConnectivityManager connectivity_manager;
    private network_callback callback;
    private static boolean first_request = true;


    private static String checkCellularNetworkType(int type, SharedPreferences sharedPreferences) {
        String net_type = "Unknown";
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_NR:
                net_type = "NR";
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                net_type = "LTE";
                if (sharedPreferences.getBoolean("root", false)) {
                    if (com.qwe7002.telegram_rc.root_kit.radio.isLTECA()) {
                        net_type += "+";
                    }
                    if (com.qwe7002.telegram_rc.root_kit.radio.isNRConnected()) {
                        net_type += " & NR";
                        break;
                    }
                    if (com.qwe7002.telegram_rc.root_kit.radio.isNRStandby()) {
                        net_type += " (NR Standby)";
                    }
                }
                break;
            case TelephonyManager.NETWORK_TYPE_HSPAP:
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


    private String bot_username = "";
    private boolean privacy_mode;
    private static Thread thread_main;
    private String chat_id;
    private String bot_token;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundNotification();
        return START_STICKY;
    }

    void startForegroundNotification() {
        Notification.Builder notification = other.getNotificationObj(context, getString(R.string.chat_command_service_name));
// Create a PendingIntent for the broadcast receiver
        Intent deleteIntent = new Intent(context, DeleteReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, notify.CHAT_COMMAND, deleteIntent, PendingIntent.FLAG_CANCEL_CURRENT);
// Set the deleteIntent on the notification
        notification.setDeleteIntent(pendingIntent);
        startForeground(notify.CHAT_COMMAND, notification.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    public class DeleteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("chat", "onReceive: Received notification that it was removed, try to pull it up again.");
            // Get the notification ID from the intent
            int notificationId = intent.getIntExtra(Notification.EXTRA_NOTIFICATION_ID, 0);
            if (notificationId == notify.CHAT_COMMAND) {
                startForegroundNotification();
            }
        }
    }

    @Override
    public void onDestroy() {
        wifilock.release();
        wakelock.release();
        unregisterReceiver(broadcast_receiver);
        connectivity_manager.unregisterNetworkCallback(callback);
        stopForeground(true);
        super.onDestroy();
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @NotNull
    static String getBatteryInfo(@NotNull Context context) {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        assert batteryManager != null;
        int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (batteryLevel > 100) {
            android.util.Log.i("get_battery_info", "The previous battery is over 100%, and the correction is 100%.");
            batteryLevel = 100;
        }
        IntentFilter intentfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, intentfilter);
        assert batteryStatus != null;
        int charge_status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        StringBuilder batteryStringBuilder = new StringBuilder().append(batteryLevel).append("%");
        switch (charge_status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
            case BatteryManager.BATTERY_STATUS_FULL:
                batteryStringBuilder.append(" (").append(context.getString(R.string.charging)).append(")");
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                switch (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    case BatteryManager.BATTERY_PLUGGED_AC:
                    case BatteryManager.BATTERY_PLUGGED_USB:
                    case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                        batteryStringBuilder.append(" (").append(context.getString(R.string.not_charging)).append(")");
                        break;
                }
                break;
        }
        return batteryStringBuilder.toString();
    }

    private boolean getMe() {
        OkHttpClient okhttpClientNew = okhttp_client;
        String request_uri = network.getUrl(bot_token, "getMe");
        Request request = new Request.Builder().url(request_uri).build();
        Call call = okhttpClientNew.newCall(request);
        Response response;
        try {
            response = call.execute();
        } catch (IOException e) {
            e.printStackTrace();
            log.writeLog(context, "Get username failed:" + e.getMessage());
            return false;
        }
        if (response.code() == 200) {
            String result;
            try {
                result = Objects.requireNonNull(response.body()).string();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject();
            if (result_obj.get("ok").getAsBoolean()) {
                bot_username = result_obj.get("result").getAsJsonObject().get("username").getAsString();
                log.writeLog(context, "Get the bot username: " + bot_username);
            }
            return true;
        }
        return false;
    }

    public static String getNetworkType(@NotNull Context context, SharedPreferences sharedPreferences) {
        String net_type = "Unknown";
        ConnectivityManager connect_manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        assert connect_manager != null;
        TelephonyManager telephonyManager = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        assert telephonyManager != null;
        android.net.Network[] networks = connect_manager.getAllNetworks();
        for (android.net.Network network : networks) {
            NetworkCapabilities network_capabilities = connect_manager.getNetworkCapabilities(network);
            assert network_capabilities != null;
            if (!network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    net_type = "WIFI";
                    break;
                }
                if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                        android.util.Log.i("get_network_type", "No permission.");
                        return net_type;
                    }
                    net_type = checkCellularNetworkType(telephonyManager.getDataNetworkType(), sharedPreferences);
                }
                if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                    net_type = "Bluetooth";
                }
                if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    net_type = "Ethernet";
                }
            }
        }

        return net_type;
    }

    private void receiveHandle(@NotNull JsonObject result_obj, boolean get_id_only) {
        long update_id = result_obj.get("update_id").getAsLong();
        offset = update_id + 1;
        if (get_id_only) {
            android.util.Log.i(TAG, "receive_handle: get_id_only");
            return;
        }

        String message_type = "";
        final request_message request_body = new request_message();
        request_body.chat_id = chat_id;
        request_body.message_thread_id = message_thread_id;
        JsonObject message_obj = null;
        String callback_data = null;
        if (result_obj.has("message")) {
            message_obj = result_obj.get("message").getAsJsonObject();
            message_type = message_obj.get("chat").getAsJsonObject().get("type").getAsString();
        }
        if (result_obj.has("channel_post")) {
            message_type = "channel";
            message_obj = result_obj.get("channel_post").getAsJsonObject();
        }
        if (result_obj.has("callback_query")) {
            message_type = "callback_query";
            JsonObject callback_query = result_obj.get("callback_query").getAsJsonObject();
            callback_data = callback_query.get("data").getAsString();
        }
        if (message_type.equals("callback_query") && send_sms_next_status != SEND_SMS_STATUS.STANDBY_STATUS) {
            int slot = Paper.book("send_temp").read("slot", -1);
            long message_id = Paper.book("send_temp").read("message_id", -1L);
            String to = Paper.book("send_temp").read("to", "");
            String content = Paper.book("send_temp").read("content", "");
            assert callback_data != null;
            if (!callback_data.equals(CALLBACK_DATA_VALUE.SEND)) {
                set_sms_send_status_standby();
                String request_uri = network.getUrl(bot_token, "editMessageText");
                String dual_sim = other.getDualSimCardDisplay(context, slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));
                String send_content = "[" + dual_sim + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + to + "\n" + context.getString(R.string.content) + content;
                request_body.text = send_content + "\n" + context.getString(R.string.status) + context.getString(R.string.cancel_button);
                request_body.message_id = message_id;
                request_body.message_thread_id = message_thread_id;
                Gson gson = new Gson();
                String request_body_raw = gson.toJson(request_body);
                RequestBody body = RequestBody.create(request_body_raw, CONST.JSON);
                OkHttpClient okhttp_client = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true));
                Request request = new Request.Builder().url(request_uri).method("POST", body).build();
                Call call = okhttp_client.newCall(request);
                try {
                    Response response = call.execute();
                    if (response.code() != 200 || response.body() == null) {
                        throw new IOException(String.valueOf(response.code()));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    log.writeLog(context, "failed to send message:" + e.getMessage());
                }
                return;
            }
            int sub_id = -1;
            if (other.getActiveCard(context) == 1) {
                slot = -1;
            } else {
                sub_id = other.getSubId(context, slot);
            }
            sms.sendSMS(context, to, content, slot, sub_id, message_id);
            set_sms_send_status_standby();
            return;
        }
        if (message_obj == null) {
            log.writeLog(context, "Request type is not allowed by security policy.");
            return;
        }

        JsonObject from_obj = null;
        String from_topic_id = "";
        final boolean message_type_is_private = message_type.equals("private");
        if (message_obj.has("from")) {
            from_obj = message_obj.get("from").getAsJsonObject();
            if (!message_type_is_private && from_obj.get("is_bot").getAsBoolean()) {
                android.util.Log.i(TAG, "receive_handle: receive from bot.");
                return;
            }
        }
        if (!Objects.equals(message_thread_id, "")) {
            if (message_obj.has("is_topic_message")) {
                from_topic_id = message_obj.get("message_thread_id").getAsString();
            }
            if (!Objects.equals(message_thread_id, from_topic_id)) {
                android.util.Log.i(TAG, "Topic ID[" + from_topic_id + "] not allow.");
                return;
            }
        }
        if (message_obj.has("chat")) {
            from_obj = message_obj.get("chat").getAsJsonObject();
        }

        assert from_obj != null;
        String from_id = from_obj.get("id").getAsString();
        if (!Objects.equals(chat_id, from_id)) {
            log.writeLog(context, "Chat ID[" + from_id + "] not allow");
            return;
        }

        String command = "";
        String command_bot_username = "";
        String request_msg = "";
        if (message_obj.has("text")) {
            request_msg = message_obj.get("text").getAsString();
        }
        if (message_obj.has("reply_to_message")) {
            sms_request_info save_item = Paper.book().read(message_obj.get("reply_to_message").getAsJsonObject().get("message_id").getAsString(), null);
            if (save_item != null && !request_msg.isEmpty()) {
                String phone_number = save_item.phone;
                int card_slot = save_item.card;
                send_sms_next_status = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS;
                Paper.book("send_temp").write("slot", card_slot);
                Paper.book("send_temp").write("to", phone_number);
                Paper.book("send_temp").write("content", request_msg);
            }
        }
        boolean has_command = false;
        if (message_obj.has("entities")) {
            String temp_command;
            String temp_command_lowercase;
            JsonArray entities_arr = message_obj.get("entities").getAsJsonArray();
            JsonObject entities_obj_command = entities_arr.get(0).getAsJsonObject();
            if (entities_obj_command.get("type").getAsString().equals("bot_command")) {
                has_command = true;
                int command_offset = entities_obj_command.get("offset").getAsInt();
                int command_end_offset = command_offset + entities_obj_command.get("length").getAsInt();
                temp_command = request_msg.substring(command_offset, command_end_offset).trim();
                temp_command_lowercase = temp_command.toLowerCase().replace("_", "");
                command = temp_command_lowercase;
                if (temp_command_lowercase.contains("@")) {
                    int command_at_location = temp_command_lowercase.indexOf("@");
                    command = temp_command_lowercase.substring(0, command_at_location);
                    command_bot_username = temp_command.substring(command_at_location + 1);
                }
            }
        }

        if (!message_type_is_private && privacy_mode && !command_bot_username.equals(bot_username)) {
            android.util.Log.i(TAG, "receive_handle: Privacy mode, no username found.");
            return;
        }

        android.util.Log.d(TAG, "Command: " + command);

        switch (command) {
            case "/help":
            case "/start":
            case "/commandlist":
                String sms_command = "\n" + getString(R.string.sendsms);
                if (other.getActiveCard(context) == 2) {
                    sms_command = "\n" + getString(R.string.sendsms_dual);
                }
                sms_command += "\n" + getString(R.string.get_spam_sms);

                String ussd_command = "";
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    ussd_command = "\n" + getString(R.string.send_ussd_command);
                    if (other.getActiveCard(context) == 2) {
                        ussd_command = "\n" + getString(R.string.send_ussd_dual_command);
                    }
                }
                String switch_ap = "";
                if (Settings.System.canWrite(context)) {
                    switch_ap += "\n" + getString(R.string.switch_ap_message);
                }
                if (sharedPreferences.getBoolean("root", false)) {
                    if (remote_control.isVPNHotspotExist(context)) {
                        switch_ap += "\n" + getString(R.string.switch_ap_message).replace("/hotspot", "/vpnhotspot");
                    }
                    switch_ap += "\n" + getString(R.string.switch_data_message);
                }
                if (command.equals("/commandlist")) {
                    request_body.text = (getString(R.string.available_command) + sms_command + ussd_command + switch_ap).replace("/", "");
                    break;
                }

                String result_string = getString(R.string.system_message_head) + "\n" + getString(R.string.available_command) + sms_command + ussd_command + switch_ap;
                if (!message_type_is_private && privacy_mode && !bot_username.isEmpty()) {
                    result_string = result_string.replace(" -", "@" + bot_username + " -");
                }
                request_body.text = result_string;
                break;
            case "/ping":
            case "/getinfo":
                String cardInfo = "";
                TelephonyManager telephonyManager = (TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE);
                assert telephonyManager != null;
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    cardInfo = "\nSIM: " + other.getSimDisplayName(context, 0);
                    if (other.getActiveCard(context) == 2) {
                        cardInfo = "\n" + getString(R.string.current_data_card) + ": SIM" + other.getDataSimId(context) + "\nSIM1: " + other.getSimDisplayName(context, 0) + "\nSIM2: " + other.getSimDisplayName(context, 1);
                    }
                }
                String spamCount = "";
                ArrayList<String> spam_list = Paper.book().read("spam_sms_list", new ArrayList<>());
                assert spam_list != null;
                if (!spam_list.isEmpty()) {
                    spamCount = "\n" + getString(R.string.spam_count_title) + spam_list.size();
                }
                String isHotspotRunning = "";
                if (Settings.System.canWrite(context)) {
                    isHotspotRunning += "\n" + getString(R.string.hotspot_status);
                    if (remote_control.isHotspotActive(context)) {
                        isHotspotRunning += getString(R.string.enable);
                    } else {
                        isHotspotRunning += getString(R.string.disable);
                    }
                }
                if (sharedPreferences.getBoolean("root", false) && remote_control.isVPNHotspotExist(context)) {
                    isHotspotRunning += "\nVPN " + getString(R.string.hotspot_status);
                    if (com.qwe7002.telegram_rc.root_kit.activity_manage.checkServiceIsRunning("be.mygod.vpnhotspot", ".RepeaterService")) {
                        isHotspotRunning += getString(R.string.enable);
                    } else {
                        isHotspotRunning += getString(R.string.disable);
                    }
                }
                String beaconStatus = "\n" + getString(R.string.beacon_monitoring_status);
                if (Boolean.FALSE.equals(Paper.book().read("disable_beacon", false))) {
                    beaconStatus += getString(R.string.enable);
                } else {
                    beaconStatus += getString(R.string.disable);
                }
                request_body.text = getString(R.string.system_message_head) + "\n" + context.getString(R.string.current_battery_level) + getBatteryInfo(context) + "\n" + getString(R.string.current_network_connection_status) + getNetworkType(context, sharedPreferences) + isHotspotRunning + beaconStatus + spamCount + cardInfo;
                android.util.Log.d(TAG, "receive_handle: " + request_body.text);
                break;
            case "/log":
                request_body.text = getString(R.string.system_message_head) + log.readLog(context, 10);
                break;
            case "/wifi":
                if (!sharedPreferences.getBoolean("root", false)) {
                    request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.no_permission);
                    break;
                }
                WifiManager wifimanager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                assert wifimanager != null;
                com.qwe7002.telegram_rc.root_kit.network.setWifi(!wifimanager.isWifiEnabled());
                request_body.text = getString(R.string.system_message_head) + "\n" + "Done";
                break;
            case "/hotspot":
                if (Settings.System.canWrite(context)) {
                    boolean ap_status = remote_control.isHotspotActive(context);
                    String result_ap;
                    if (!ap_status) {
                        result_ap = getString(R.string.enable_wifi) + context.getString(R.string.action_success);
                        String[] command_list_data = request_msg.split(" ");
                        int tether_mode = TetherManager.TetherMode.TETHERING_WIFI;
                        if (command_list_data.length == 2) {
                            tether_mode = switch (command_list_data[1].toLowerCase()) {
                                case "bluetooth" -> TetherManager.TetherMode.TETHERING_BLUETOOTH;
                                case "ncm" -> TetherManager.TetherMode.TETHERING_NCM;
                                case "usb" -> TetherManager.TetherMode.TETHERING_USB;
                                case "nic" -> TetherManager.TetherMode.TETHERING_ETHERNET;
                                case "wigig" -> TetherManager.TetherMode.TETHERING_WIGIG;
                                default -> tether_mode;
                            };
                        }
                        Paper.book("temp").write("tether_mode", tether_mode);
                        remote_control.enableHotspot(context, tether_mode);
                    } else {
                        Paper.book("temp").write("tether_open", false);
                        result_ap = getString(R.string.disable_wifi) + context.getString(R.string.action_success);
                    }
                    result_ap += "\n" + context.getString(R.string.current_battery_level) + getBatteryInfo(context) + "\n" + getString(R.string.current_network_connection_status) + getNetworkType(context, sharedPreferences);
                    request_body.text = getString(R.string.system_message_head) + "\n" + result_ap;
                    break;
                } else {
                    command = "/vpnhotspot";
                }
            case "/vpnhotspot":
                if (!sharedPreferences.getBoolean("root", false) || !remote_control.isVPNHotspotExist(context)) {
                    request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.no_permission);
                    break;
                }
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                assert wifiManager != null;
                boolean wifi_open = Paper.book("temp").read("wifi_open", wifiManager.isWifiEnabled());
                String result_vpn_ap;
                if (!wifi_open) {
                    result_vpn_ap = getString(R.string.enable_wifi) + context.getString(R.string.action_success);
                    new Thread(() -> remote_control.enableVPNHotspot(wifiManager)).start();
                } else {
                    Paper.book("temp").write("wifi_open", false);
                    result_vpn_ap = getString(R.string.disable_wifi) + context.getString(R.string.action_success);
                }
                result_vpn_ap += "\n" + context.getString(R.string.current_battery_level) + getBatteryInfo(context) + "\n" + getString(R.string.current_network_connection_status) + getNetworkType(context, sharedPreferences);

                request_body.text = getString(R.string.system_message_head) + "\n" + result_vpn_ap;
                break;
            case "/mobiledata":
                if (!sharedPreferences.getBoolean("root", false)) {
                    request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.no_permission);
                    break;
                }
                String result_data = context.getString(R.string.switch_data);

                request_body.text = getString(R.string.system_message_head) + "\n" + result_data;
                break;
            case "/sendussd":
            case "/sendussd1":
            case "/sendussd2":
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    int sub_id = -1;
                    if (other.getActiveCard(context) == 2) {
                        if (command.equals("/sendussd2")) {
                            sub_id = other.getSubId(context, 1);
                        }
                    }
                    String[] command_list = request_msg.split(" ");
                    if (command_list.length == 2) {
                        ussd.send_ussd(context, command_list[1], sub_id);
                        return;
                    } else {
                        request_body.text = "Error";
                    }
                } else {
                    android.util.Log.i(TAG, "send_ussd: No permission.");
                }
                request_body.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.unknown_command);
                break;
            case "/getspamsms":
                ArrayList<String> spam_sms_list = Paper.book().read("spam_sms_list", new ArrayList<>());
                if (spam_sms_list.isEmpty()) {
                    request_body.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.no_spam_history);
                    break;
                }
                new Thread(() -> {
                    if (network.checkNetworkStatus(context)) {
                        OkHttpClient okhttp_client = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true));
                        for (String item : spam_sms_list) {
                            request_message send_sms_request_body = new request_message();
                            send_sms_request_body.chat_id = chat_id;
                            send_sms_request_body.text = item;
                            String request_uri = network.getUrl(bot_token, "sendMessage");
                            String request_body_json = new Gson().toJson(send_sms_request_body);
                            RequestBody body = RequestBody.create(request_body_json, CONST.JSON);
                            Request request_obj = new Request.Builder().url(request_uri).method("POST", body).build();
                            Call call = okhttp_client.newCall(request_obj);
                            call.enqueue(new Callback() {
                                @Override
                                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                                    e.printStackTrace();
                                }

                                @Override
                                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                                    android.util.Log.d(TAG, "onResponse: " + Objects.requireNonNull(response.body()).string());
                                }
                            });
                            ArrayList<String> resend_list_local = Paper.book().read("spam_sms_list", new ArrayList<>());
                            resend_list_local.remove(item);
                            Paper.book().write("spam_sms_list", resend_list_local);
                        }
                    }
                    log.writeLog(context, "Send spam message is complete.");
                }).start();
                return;
            case "/autoswitch":
                boolean state = !Paper.book().read("disable_beacon", false);
                Paper.book().write("disable_beacon", state);
                request_body.text = context.getString(R.string.system_message_head) + "\n" + "Beacon monitoring status: " + !state;
                break;
            case "/setdummy":
                if (!sharedPreferences.getBoolean("root", false)) {
                    request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.no_permission);
                    break;
                }
                String[] command_list = request_msg.split(" ");
                if (command_list.length == 2) {
                    Paper.book("system_config").write("dummy_ip_addr", command_list[1]);
                    com.qwe7002.telegram_rc.root_kit.network.addDummyDevice(command_list[1]);
                } else {
                    if (Paper.book("system_config").contains("dummy_ip_addr")) {
                        String dummy_ip_addr = Paper.book("system_config").read("dummy_ip_addr");
                        com.qwe7002.telegram_rc.root_kit.network.addDummyDevice(dummy_ip_addr);
                    }
                }
                request_body.text = context.getString(R.string.system_message_head) + "\n" + "Done";
                break;
            case "/deldummy":
                if (!sharedPreferences.getBoolean("root", false)) {
                    request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.no_permission);
                    break;
                }
                com.qwe7002.telegram_rc.root_kit.network.delDummyDevice();
                request_body.text = context.getString(R.string.system_message_head) + "\n" + "Done";
                break;
            case "/sendsms":
            case "/sendsms1":
            case "/sendsms2":
                String[] msg_send_list = request_msg.split("\n");
                if (msg_send_list.length > 2) {
                    String msg_send_to = other.getSendPhoneNumber(msg_send_list[1]);
                    if (other.isPhoneNumber(msg_send_to)) {
                        StringBuilder msg_send_content = new StringBuilder();
                        for (int i = 2; i < msg_send_list.length; ++i) {
                            if (msg_send_list.length != 3 && i != 2) {
                                msg_send_content.append("\n");
                            }
                            msg_send_content.append(msg_send_list[i]);
                        }
                        if (other.getActiveCard(context) == 1) {
                            sms.sendSMS(context, msg_send_to, msg_send_content.toString(), -1, -1);
                            return;
                        }
                        int send_slot = -1;
                        if (other.getActiveCard(context) > 1) {
                            send_slot = 0;
                            if (command.equals("/sendsms2")) {
                                send_slot = 1;
                            }
                        }
                        int sub_id = other.getSubId(context, send_slot);
                        if (sub_id != -1) {
                            sms.sendSMS(context, msg_send_to, msg_send_content.toString(), send_slot, sub_id);
                            return;
                        }
                    }
                } else {
                    has_command = false;
                    send_sms_next_status = SEND_SMS_STATUS.PHONE_INPUT_STATUS;
                    int send_slot = -1;
                    if (other.getActiveCard(context) > 1) {
                        send_slot = 0;
                        if (command.equals("/sendsms2")) {
                            send_slot = 1;
                        }
                    }
                    Paper.book("send_temp").write("slot", send_slot);
                }
                request_body.text = "[" + context.getString(R.string.send_sms_head) + "]" + "\n" + getString(R.string.failed_to_get_information);
                break;
            default:
                if (!message_type_is_private && send_sms_next_status == -1) {
                    if (!message_type.equals("supergroup") || message_thread_id.isEmpty()) {
                        android.util.Log.i(TAG, "receive_handle: The conversation is not Private and does not prompt an error.");
                        return;
                    }
                }

                request_body.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.unknown_command);
                break;
        }
        if (has_command) {
            set_sms_send_status_standby();
        }
        if (!has_command && send_sms_next_status != -1) {
            android.util.Log.i(TAG, "receive_handle: Enter the interactive SMS sending mode.");
            String dual_sim = "";
            int send_slot_temp = Paper.book("send_temp").read("slot", -1);
            if (send_slot_temp != -1) {
                dual_sim = "SIM" + (send_slot_temp + 1) + " ";
            }
            String head = "[" + dual_sim + context.getString(R.string.send_sms_head) + "]";
            String result_send = getString(R.string.failed_to_get_information);
            android.util.Log.d(TAG, "Sending mode status: " + send_sms_next_status);

            switch (send_sms_next_status) {
                case SEND_SMS_STATUS.PHONE_INPUT_STATUS:
                    send_sms_next_status = SEND_SMS_STATUS.MESSAGE_INPUT_STATUS;
                    result_send = getString(R.string.enter_number);
                    break;
                case SEND_SMS_STATUS.MESSAGE_INPUT_STATUS:
                    String temp_to = other.getSendPhoneNumber(request_msg);
                    if (other.isPhoneNumber(temp_to)) {
                        Paper.book("send_temp").write("to", temp_to);
                        result_send = getString(R.string.enter_content);
                        send_sms_next_status = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS;
                    } else {
                        set_sms_send_status_standby();
                        result_send = getString(R.string.unable_get_phone_number);
                    }
                    break;
                case SEND_SMS_STATUS.WAITING_TO_SEND_STATUS:
                    Paper.book("send_temp").write("content", request_msg);
                    reply_markup_keyboard.keyboard_markup keyboardMarkup = new reply_markup_keyboard.keyboard_markup();
                    ArrayList<ArrayList<reply_markup_keyboard.InlineKeyboardButton>> inlineKeyboardButtons = new ArrayList<>();
                    inlineKeyboardButtons.add(reply_markup_keyboard.get_inline_keyboard_obj(context.getString(R.string.send_button), CALLBACK_DATA_VALUE.SEND));
                    inlineKeyboardButtons.add(reply_markup_keyboard.get_inline_keyboard_obj(context.getString(R.string.cancel_button), CALLBACK_DATA_VALUE.CANCEL));
                    keyboardMarkup.inline_keyboard = inlineKeyboardButtons;
                    request_body.reply_markup = keyboardMarkup;
                    result_send = context.getString(R.string.to) + Paper.book("send_temp").read("to") + "\n" + context.getString(R.string.content) + Paper.book("send_temp").read("content", "");
                    send_sms_next_status = SEND_SMS_STATUS.SEND_STATUS;
                    break;
            }
            request_body.text = head + "\n" + result_send;
        }

        String request_uri = network.getUrl(bot_token, "sendMessage");
        RequestBody body = RequestBody.create(new Gson().toJson(request_body), CONST.JSON);
        android.util.Log.d(TAG, "receive_handle: " + new Gson().toJson(request_body));
        Request send_request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(send_request);
        final String error_head = "Send reply failed:";
        final boolean final_has_command = has_command;
        final String final_command = command;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                log.writeLog(context, error_head + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                assert response.body() != null;
                String response_string = Objects.requireNonNull(response.body()).string();
                if (response.code() != 200) {
                    log.writeLog(context, error_head + response.code() + " " + response_string);
                    return;
                }
                String splite_command_value = final_command.replace("_", "");
                if (!final_has_command && send_sms_next_status == SEND_SMS_STATUS.SEND_STATUS) {
                    Paper.book("send_temp").write("message_id", other.getMessageId(response_string));
                }

                if (splite_command_value.equals("/hotspot") || splite_command_value.equals("/vpnhotspot")) {
                    if (!Paper.book("temp").read("tether_open", false)) {
                        remote_control.disableHotspot(context, Paper.book("temp").read("tether_mode", TetherManager.TetherMode.TETHERING_WIFI));
                        Paper.book("temp").delete("tether_mode");
                    }
                }
                if (final_has_command && sharedPreferences.getBoolean("root", false)) {
                    switch (splite_command_value) {
                        case "/vpnhotspot":
                            if (!Paper.book("temp").read("wifi_open", false)) {
                                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                                assert wifiManager != null;
                                remote_control.disableVPNHotspot(wifiManager);
                            }
                            break;
                        case "/mobiledata":
                            com.qwe7002.telegram_rc.root_kit.network.setData(!network.getDataEnable(context));
                            break;
                    }
                }
            }
        });
    }


    static class CALLBACK_DATA_VALUE {
        final static String SEND = "send";
        final static String CANCEL = "cancel";
    }

    private void set_sms_send_status_standby() {
        send_sms_next_status = SEND_SMS_STATUS.STANDBY_STATUS;
        Paper.book("send_temp").destroy();
    }

    @SuppressLint({"InvalidWakeLockTag", "WakelockTimeout", "UnspecifiedRegisterReceiverFlag"})
    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        connectivity_manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Paper.init(context);
        set_sms_send_status_standby();
        sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);

        chat_id = sharedPreferences.getString("chat_id", "");
        bot_token = sharedPreferences.getString("bot_token", "");
        message_thread_id = sharedPreferences.getString("message_thread_id", "");
        okhttp_client = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true));
        privacy_mode = sharedPreferences.getBoolean("privacy_mode", false);
        wifilock = ((WifiManager) Objects.requireNonNull(context.getApplicationContext().getSystemService(Context.WIFI_SERVICE))).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "bot_command_polling_wifi");
        wakelock = ((PowerManager) Objects.requireNonNull(context.getSystemService(Context.POWER_SERVICE))).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "bot_command_polling");
        wifilock.setReferenceCounted(false);
        wakelock.setReferenceCounted(false);
        if (!wifilock.isHeld()) {
            wifilock.acquire();
        }
        if (!wakelock.isHeld()) {
            wakelock.acquire();
        }
        thread_main = new Thread(new thread_main_runnable());
        thread_main.start();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CONST.BROADCAST_STOP_SERVICE);
        NetworkRequest network_request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build();
        callback = new network_callback();
        connectivity_manager.registerNetworkCallback(network_request, callback);
        broadcast_receiver = new broadcast_receiver();
        registerReceiver(broadcast_receiver, intentFilter);
    }

    private void when_network_change() {
        if (network.checkNetworkStatus(context)) {
            if (!thread_main.isAlive()) {
                log.writeLog(context, "Network connections has been restored.");
                thread_main = new Thread(new thread_main_runnable());
                thread_main.start();
            }
        }
    }

    private static class SEND_SMS_STATUS {
        public static final int STANDBY_STATUS = -1;
        public static final int PHONE_INPUT_STATUS = 0;
        public static final int MESSAGE_INPUT_STATUS = 1;
        public static final int WAITING_TO_SEND_STATUS = 2;
        public static final int SEND_STATUS = 3;
    }

    private class broadcast_receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, @NotNull Intent intent) {
            android.util.Log.d(TAG, "onReceive: " + intent.getAction());
            assert intent.getAction() != null;
            if (CONST.BROADCAST_STOP_SERVICE.equals(intent.getAction())) {
                android.util.Log.i(TAG, "Received stop signal, quitting now...");
                stopSelf();
                Process.killProcess(Process.myPid());
            }
        }
    }

    private class network_callback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(@NotNull android.net.Network network) {
            android.util.Log.d(TAG, "onAvailable");
            when_network_change();
        }
    }

    class thread_main_runnable implements Runnable {
        @Override
        public void run() {
            android.util.Log.d(TAG, "run: thread main start");
            if (other.parseStringToLong(chat_id) < 0) {
                bot_username = Paper.book().read("bot_username", null);
                if (bot_username == null) {
                    while (!getMe()) {
                        log.writeLog(context, "Failed to get bot Username, Wait 5 seconds and try again.");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                android.util.Log.i(TAG, "run: The Bot Username is loaded. The Bot Username is: " + bot_username);
            }
            while (true) {
                int timeout = 60;
                int http_timeout = 65;
                OkHttpClient okhttp_client_new = okhttp_client.newBuilder()
                        .readTimeout(http_timeout, TimeUnit.SECONDS)
                        .writeTimeout(http_timeout, TimeUnit.SECONDS)
                        .build();
                String request_uri = network.getUrl(bot_token, "getUpdates");
                polling_json request_body = new polling_json();
                request_body.offset = offset;
                request_body.timeout = timeout;
                if (first_request) {
                    request_body.timeout = 0;
                    android.util.Log.d(TAG, "run: first_request");
                }
                RequestBody body = RequestBody.create(new Gson().toJson(request_body), CONST.JSON);
                Request request = new Request.Builder().url(request_uri).method("POST", body).build();
                Call call = okhttp_client_new.newCall(request);
                Response response;
                try {
                    response = call.execute();
                } catch (IOException e) {
                    e.printStackTrace();
                    if (!network.checkNetworkStatus(context)) {
                        log.writeLog(context, "No network connections available. ");
                        break;
                    }
                    int sleep_time = 5;
                    log.writeLog(context, "Connection to the Telegram API service failed, try again after " + sleep_time + " seconds.");
                    try {
                        Thread.sleep(sleep_time * 1000L);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    continue;
                }
                if (response.code() == 200) {
                    assert response.body() != null;
                    String result;
                    try {
                        result = Objects.requireNonNull(response.body()).string();
                    } catch (IOException e) {
                        e.printStackTrace();
                        continue;
                    }
                    android.util.Log.d(TAG, "run: " + result);
                    JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject();
                    if (result_obj.get("ok").getAsBoolean()) {
                        JsonArray result_array = result_obj.get("result").getAsJsonArray();
                        for (JsonElement item : result_array) {
                            receiveHandle(item.getAsJsonObject(), first_request);
                        }
                        first_request = false;
                    }
                } else {
                    log.writeLog(context, "response code:" + response.code());
                    if (response.code() == 401) {
                        assert response.body() != null;
                        String result;
                        try {
                            result = Objects.requireNonNull(response.body()).string();
                        } catch (IOException e) {
                            e.printStackTrace();
                            continue;
                        }
                        JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject();
                        String result_message = getString(R.string.system_message_head) + "\n" + getString(R.string.error_stop_message) + "\n" + getString(R.string.error_message_head) + result_obj.get("description").getAsString() + "\n" + "Code: " + response.code();
                        sms.sendFallbackSMS(context, result_message, -1);
                        service.stopAllService(context);
                        break;
                    }
                }
            }
        }
    }

}

