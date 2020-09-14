package com.qwe7002.telegram_rc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@SuppressWarnings("BusyWait")
public class chat_command_service extends Service {
    private static final String TAG = "chat_command";
    //Global counter
    private static long offset = 0;
    private static int magnification = 1;
    private static int error_magnification = 1;
    private static int send_sms_next_status = SEND_SMS_STATUS.STANDBY_STATUS;
    // global object
    private OkHttpClient okhttp_client;
    private broadcast_receiver broadcast_receiver;
    private PowerManager.WakeLock wakelock;
    private WifiManager.WifiLock wifilock;
    private Context context;
    private SharedPreferences sharedPreferences;
    private ConnectivityManager connectivity_manager;
    private network_callback callback;

    private void receive_handle(@NotNull JsonObject result_obj) {
        String message_type = "";
        long update_id = result_obj.get("update_id").getAsLong();
        offset = update_id + 1;
        final message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        JsonObject message_obj = null;
        if (result_obj.has("message")) {
            message_obj = result_obj.get("message").getAsJsonObject();
            message_type = message_obj.get("chat").getAsJsonObject().get("type").getAsString();
        }
        if (result_obj.has("channel_post")) {
            message_type = "channel";
            message_obj = result_obj.get("channel_post").getAsJsonObject();
        }
        if (message_obj == null) {
            public_func.write_log(context, "Request type is not allowed by security policy.");
            return;
        }
        JsonObject from_obj = null;
        final boolean message_type_is_private = message_type.equals("private");
        if (message_obj.has("from")) {
            from_obj = message_obj.get("from").getAsJsonObject();
            if (!message_type_is_private && from_obj.get("is_bot").getAsBoolean()) {
                Log.i(TAG, "receive_handle: receive from bot.");
                return;
            }
        }
        if (message_obj.has("chat")) {
            from_obj = message_obj.get("chat").getAsJsonObject();
        }

        assert from_obj != null;
        String from_id = from_obj.get("id").getAsString();
        if (!Objects.equals(chat_id, from_id)) {
            public_func.write_log(context, "Chat ID[" + from_id + "] not allow");
            return;
        }

        String command = "";
        String command_bot_username = "";
        String request_msg = "";
        if (message_obj.has("text")) {
            request_msg = message_obj.get("text").getAsString();
        }
        if (message_obj.has("reply_to_message")) {
            message_item save_item = Paper.book().read(message_obj.get("reply_to_message").getAsJsonObject().get("message_id").getAsString(), null);
            if (save_item != null) {
                String phone_number = save_item.phone;
                int card_slot = save_item.card;
                int sub_id = save_item.sub_id;
                if (card_slot != -1 && sub_id == -1) {
                    sub_id = public_func.get_sub_id(context, card_slot);
                }
                public_func.send_sms(context, phone_number, request_msg, card_slot, sub_id);
                return;
            }
            if (!message_type_is_private) {
                Log.i(TAG, "receive_handle: The message id could not be found, ignored.");
                return;
            }
        }
        if (message_obj.has("entities")) {
            String temp_command;
            String temp_command_lowercase;
            JsonArray entities_arr = message_obj.get("entities").getAsJsonArray();
            JsonObject entities_obj_command = entities_arr.get(0).getAsJsonObject();
            if (entities_obj_command.get("type").getAsString().equals("bot_command")) {
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
            Log.i(TAG, "receive_handle: Privacy mode, no username found.");
            return;
        }

        Log.d(TAG, "Command: " + command);
        boolean has_command = false;
        switch (command) {
            case "/help":
            case "/start":
            case "/commandlist":
                String sms_command = "\n" + getString(R.string.sendsms);
                String data_switch_command = "";
                if (public_func.get_active_card(context) == 2) {
                    sms_command = "\n" + getString(R.string.sendsms_dual);
                    if (sharedPreferences.getBoolean("root", false)) {
                        data_switch_command = "\n" + getString(R.string.set_data_card);
                    }
                }
                sms_command += "\n" + getString(R.string.get_spam_sms);

                String ussd_command = "";
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        ussd_command = "\n" + getString(R.string.send_ussd_command);
                        if (public_func.get_active_card(context) == 2) {
                            ussd_command = "\n" + getString(R.string.send_ussd_dual_command);
                        }
                    }
                }
                String config_adb = "";
                String switch_ap = "";
                if (sharedPreferences.getBoolean("root", false)) {
                    if (is_vpn_hotsport_exist()) {
                        switch_ap = "\n" + getString(R.string.switch_ap_message);
                    }
                    config_adb = "\n" + context.getString(R.string.config_adb_message);
                }
                if (command.equals("/commandlist")) {
                    request_body.text = (getString(R.string.available_command) + sms_command + ussd_command + switch_ap + config_adb).replace("/", "");
                    break;
                }

                String result_string = getString(R.string.system_message_head) + "\n" + getString(R.string.available_command) + sms_command + ussd_command + switch_ap + data_switch_command + config_adb;
                if (!message_type_is_private && privacy_mode && !bot_username.equals("")) {
                    result_string = result_string.replace(" -", "@" + bot_username + " -");
                }
                request_body.text = result_string;
                has_command = true;
                break;
            case "/ping":
            case "/getinfo":
                String card_info = "";
                String network_stats = "";
                TelephonyManager telephonyManager = (TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE);
                assert telephonyManager != null;
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    if (public_func.is_data_usage(context)) {
                        int data_flush_day = sharedPreferences.getInt("data_flush_day", 1);
                        NetworkStatsManager service = context.getSystemService(NetworkStatsManager.class);
                        Calendar c = Calendar.getInstance();
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(new Date());
                        if (cal.get(Calendar.DAY_OF_MONTH) >= data_flush_day) {//Set the month of acquisition
                            c.add(Calendar.MONTH, 0);
                        } else {
                            c.add(Calendar.MONTH, -1);
                        }
                        c.set(Calendar.DAY_OF_MONTH, data_flush_day);
                        c.set(Calendar.HOUR_OF_DAY, 0);
                        c.set(Calendar.MINUTE, 0);
                        c.set(Calendar.SECOND, 0);
                        long from = c.getTimeInMillis();
                        try {
                            //Since the IMSI of SIM2 cannot be obtained in Android 10, SIM2 traffic statistics are not implemented
                            NetworkStats.Bucket bucket =
                                    service.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, from, System.currentTimeMillis());
                            network_stats = getString(R.string.mobile_data_usage) + get_size(bucket.getTxBytes() + bucket.getRxBytes());
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    card_info = "\nSIM: " + public_func.get_sim_display_name(context, 0) + get_cell_info(context, telephonyManager, public_func.get_sub_id(context, 0));
                    if (public_func.get_active_card(context) == 2) {
                        network_stats = "(SIM1) " + network_stats;
                        card_info = "\n" + getString(R.string.current_data_card) + ": SIM" + public_func.get_data_sim_id(context) + "\nSIM1: " + public_func.get_sim_display_name(context, 0) + get_cell_info(context, telephonyManager, public_func.get_sub_id(context, 0)) + "\nSIM2: " + public_func.get_sim_display_name(context, 1) + get_cell_info(context, telephonyManager, public_func.get_sub_id(context, 1));
                    }
                }
                String spam_count = "";
                ArrayList<String> spam_list = Paper.book().read("spam_sms_list", new ArrayList<>());
                if (spam_list.size() != 0) {
                    spam_count = "\n" + getString(R.string.spam_count_title) + spam_list.size();
                }
                String is_hotspot_running = "";
                if (sharedPreferences.getBoolean("root", false)) {
                    is_hotspot_running = "\n" + getString(R.string.hotspot_status) + getString(R.string.disable);
                    if (com.qwe7002.root_kit.activity_manage.check_service_is_running(public_func.VPN_HOTSPOT_PACKAGE_NAME, ".RepeaterService")) {
                        is_hotspot_running = "\n" + getString(R.string.hotspot_status) + getString(R.string.enable);
                    }
                }
                request_body.text = getString(R.string.system_message_head) + "\n" + context.getString(R.string.current_battery_level) + get_battery_info(context) + "\n" + getString(R.string.current_network_connection_status) + get_network_type(context, false) + is_hotspot_running + spam_count + card_info + "\n" + network_stats;
                has_command = true;
                break;
            case "/log":
                request_body.text = getString(R.string.system_message_head) + public_func.read_log(context, 10);
                has_command = true;
                break;
            case "/setadbport":
                if (!sharedPreferences.getBoolean("root", false)) {
                    request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.not_getting_root);
                    break;
                }
                String port = "-1";
                String[] msg_list = request_msg.split(" ");
                if (msg_list.length == 2) {
                    boolean str_is_not_num = false;
                    try {
                        Integer.parseInt(msg_list[1]);
                    } catch (NumberFormatException e) {
                        str_is_not_num = true;
                        e.printStackTrace();
                    }
                    if (!str_is_not_num) {
                        port = msg_list[1];
                    }
                }
                Paper.book().write("adb_port", port);
                StringBuilder result = new StringBuilder();
                result.append(getString(R.string.system_message_head)).append("\n").append(getString(R.string.adb_config));
                if (com.qwe7002.root_kit.nadb.set_nadb(port)) {
                    result.append(getString(R.string.action_success));
                } else {
                    result.append(getString(R.string.action_failed));
                }
                result.append("\n");
                result.append("port: ");
                result.append(port);
                request_body.text = result.toString();
                has_command = true;
                break;
            case "/setadbkey":
                String run_result = "Error";
                String[] command_msg_list = request_msg.split("\n");
                if (command_msg_list.length == 2) {
                    if (com.qwe7002.root_kit.nadb.get_adb_auth(command_msg_list[1])) {
                        run_result = "Done";
                    }
                }
                request_body.text = getString(R.string.system_message_head) + "\n Status: " + run_result;
                break;
            case "/switchap":
                if (!sharedPreferences.getBoolean("root", false) || !is_vpn_hotsport_exist()) {
                    request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.not_getting_root);
                    break;
                }
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                assert wifiManager != null;
                boolean wifi_open = Paper.book().read("wifi_open", wifiManager.isWifiEnabled());
                String result_ap;
                if (!wifi_open) {
                    result_ap = getString(R.string.enable_wifi) + context.getString(R.string.action_success);
                    new Thread(() -> remote_control_public.enable_ap(wifiManager)).start();
                } else {
                    Paper.book().write("wifi_open", false);
                    result_ap = getString(R.string.disable_wifi) + context.getString(R.string.action_success);
                }
                result_ap += "\n" + context.getString(R.string.current_battery_level) + get_battery_info(context) + "\n" + getString(R.string.current_network_connection_status) + get_network_type(context, true);

                request_body.text = getString(R.string.system_message_head) + "\n" + result_ap;
                has_command = true;
                break;
            case "/setdatacard":
                if (!sharedPreferences.getBoolean("root", false)) {
                    request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.not_getting_root);
                    break;
                }
                if (public_func.get_active_card(context) == 2) {
                    String[] command_list_data = request_msg.split(" ");
                    int slot;
                    if (command_list_data.length == 2) {
                        slot = Integer.parseInt(command_list_data[1]) - 1;
                    } else {
                        slot = 0;
                        if (public_func.get_data_sim_id(context).equals("1")) {
                            slot = 1;
                        }
                    }
                    Paper.book("temp").write("sub_id", public_func.get_sub_id(context, slot));
                    request_body.text = getString(R.string.system_message_head) + "\nCurrent data card: SIM" + public_func.get_data_sim_id(context) + "\nSet data card: SIM" + (slot + 1);
                    has_command = true;
                }
                break;
            case "/switchdata":
            case "/restartnetwork":
                if (!sharedPreferences.getBoolean("root", false)) {
                    request_body.text = getString(R.string.system_message_head) + "\n" + getString(R.string.not_getting_root);
                    break;
                }
                String result_data;
                if ("/restartnetwork".equals(command)) {
                    result_data = context.getString(R.string.restart_network);
                } else {
                    result_data = context.getString(R.string.switch_data);
                }
                request_body.text = getString(R.string.system_message_head) + "\n" + result_data;
                has_command = true;
                break;
            case "/sendussd":
            case "/sendussd1":
            case "/sendussd2":
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        int sub_id = -1;
                        if (public_func.get_active_card(context) == 2) {
                            if (command.equals("/sendussd2")) {
                                sub_id = public_func.get_sub_id(context, 1);
                            }
                        }
                        String[] command_list = request_msg.split(" ");
                        if (command_list.length == 2) {
                            public_func.send_ussd(context, command_list[1], sub_id);
                            return;
                        }
                    } else {
                        Log.i(TAG, "send_ussd: No permission.");
                    }
                }
                request_body.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.unknown_command);
                break;
            case "/getspamsms":
                ArrayList<String> spam_sms_list = Paper.book().read("spam_sms_list", new ArrayList<>());
                if (spam_sms_list.size() == 0) {
                    request_body.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.no_spam_history);
                    break;
                }
                new Thread(() -> {
                    if (public_func.check_network_status(context)) {
                        OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book().read("proxy_config", new proxy_config()));
                        for (String item : spam_sms_list) {
                            message_json send_sms_request_body = new message_json();
                            send_sms_request_body.chat_id = chat_id;
                            send_sms_request_body.text = item;
                            String request_uri = public_func.get_url(bot_token, "sendMessage");
                            String request_body_json = new Gson().toJson(send_sms_request_body);
                            RequestBody body = RequestBody.create(request_body_json, public_func.JSON);
                            Request request_obj = new Request.Builder().url(request_uri).method("POST", body).build();
                            Call call = okhttp_client.newCall(request_obj);
                            call.enqueue(new Callback() {
                                @Override
                                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                                    e.printStackTrace();
                                }

                                @Override
                                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                                    Log.d(TAG, "onResponse: " + Objects.requireNonNull(response.body()).string());
                                }
                            });
                            ArrayList<String> resend_list_local = Paper.book().read("spam_sms_list", new ArrayList<>());
                            resend_list_local.remove(item);
                            Paper.book().write("spam_sms_list", resend_list_local);
                        }
                    }
                    public_func.write_log(context, "Send spam message is complete.");
                }).start();
                return;
            case "/disablebeacon":
                boolean state = !Paper.book().read("disable_beacon", false);
                Paper.book().write("disable_beacon", state);
                request_body.text = context.getString(R.string.system_message_head) + "\n" + "Beacon monitoring status: " + state;
                break;
            case "/sendsms":
            case "/sendsms1":
            case "/sendsms2":
                has_command = true;
                String[] msg_send_list = request_msg.split("\n");
                if (msg_send_list.length > 2) {
                    String msg_send_to = public_func.get_send_phone_number(msg_send_list[1]);
                    if (public_func.is_phone_number(msg_send_to)) {
                        StringBuilder msg_send_content = new StringBuilder();
                        for (int i = 2; i < msg_send_list.length; ++i) {
                            if (msg_send_list.length != 3 && i != 2) {
                                msg_send_content.append("\n");
                            }
                            msg_send_content.append(msg_send_list[i]);
                        }
                        if (public_func.get_active_card(context) == 1) {
                            public_func.send_sms(context, msg_send_to, msg_send_content.toString(), -1, -1);
                            return;
                        }
                        int slot = -1;
                        switch (command) {
                            case "/sendsms":
                            case "/sendsms1":
                                slot = 0;
                                break;
                            case "/sendsms2":
                                slot = 1;
                                break;
                        }
                        int sub_id = public_func.get_sub_id(context, slot);
                        if (sub_id != -1) {
                            public_func.send_sms(context, msg_send_to, msg_send_content.toString(), slot, sub_id);
                            return;
                        }
                    }
                } else if (message_type_is_private) {
                    has_command = false;
                    send_sms_next_status = SEND_SMS_STATUS.PHONE_INPUT_STATUS;
                    send_slot_temp = -1;
                    if (public_func.get_active_card(context) > 1) {
                        switch (command) {
                            case "/sendsms":
                            case "/sendsms1":
                                send_slot_temp = 0;
                                break;
                            case "/sendsms2":
                                send_slot_temp = 1;
                                break;
                        }
                    }
                }
                request_body.text = "[" + context.getString(R.string.send_sms_head) + "]" + "\n" + getString(R.string.failed_to_get_information);
                break;
            default:
                if (!message_type_is_private) {
                    Log.i(TAG, "receive_handle: The conversation is not Private and does not prompt an error.");
                    return;
                }
                request_body.text = context.getString(R.string.system_message_head) + "\n" + getString(R.string.unknown_command);
                break;
        }
        if (has_command) {
            send_sms_next_status = SEND_SMS_STATUS.STANDBY_STATUS;
            send_slot_temp = -1;
            send_to_temp = null;
        }
        if (!has_command && send_sms_next_status != -1) {
            Log.i(TAG, "receive_handle: Enter the interactive SMS sending mode.");
            String dual_sim = "";
            if (send_slot_temp != -1) {
                dual_sim = "SIM" + (send_slot_temp + 1) + " ";
            }
            String head = "[" + dual_sim + context.getString(R.string.send_sms_head) + "]";
            String result_send = getString(R.string.failed_to_get_information);
            Log.d(TAG, "Sending mode status: " + send_sms_next_status);
            switch (send_sms_next_status) {
                case SEND_SMS_STATUS.PHONE_INPUT_STATUS:
                    send_sms_next_status = SEND_SMS_STATUS.MESSAGE_INPUT_STATUS;
                    result_send = getString(R.string.enter_number);
                    break;
                case SEND_SMS_STATUS.MESSAGE_INPUT_STATUS:
                    String temp_to = public_func.get_send_phone_number(request_msg);
                    Log.d(TAG, "receive_handle: " + temp_to);
                    if (public_func.is_phone_number(temp_to)) {
                        send_to_temp = temp_to;
                        result_send = getString(R.string.enter_content);
                        send_sms_next_status = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS;
                    } else {
                        send_sms_next_status = SEND_SMS_STATUS.STANDBY_STATUS;
                        send_slot_temp = -1;
                        send_to_temp = null;
                        result_send = getString(R.string.unable_get_phone_number);
                    }
                    break;
                case SEND_SMS_STATUS.WAITING_TO_SEND_STATUS:
                    if (public_func.get_active_card(context) == 1) {
                        public_func.send_sms(context, send_to_temp, request_msg, -1, -1);
                        return;
                    }
                    int sub_id = public_func.get_sub_id(context, send_slot_temp);
                    if (sub_id != -1) {
                        public_func.send_sms(context, send_to_temp, request_msg, send_slot_temp, sub_id);
                        send_sms_next_status = SEND_SMS_STATUS.STANDBY_STATUS;
                        send_slot_temp = -1;
                        send_to_temp = null;
                        return;
                    }
                    break;
            }
            request_body.text = head + "\n" + result_send;
        }

        String request_uri = public_func.get_url(bot_token, "sendMessage");
        RequestBody body = RequestBody.create(new Gson().toJson(request_body), public_func.JSON);
        Request send_request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(send_request);
        final String error_head = "Send reply failed:";
        final boolean final_has_command = has_command;
        final String final_command = command;
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                public_func.write_log(context, error_head + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    assert response.body() != null;
                    public_func.write_log(context, error_head + response.code() + " " + Objects.requireNonNull(response.body()).string());
                }
                if (final_has_command && sharedPreferences.getBoolean("root", false)) {
                    switch (final_command.replace("_", "")) {
                        case "/switchap":
                            if (!Paper.book().read("wifi_open", false)) {
                                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                                assert wifiManager != null;
                                remote_control_public.disable_ap(wifiManager);
                            }
                            break;
                        case "/switchdata":
                            com.qwe7002.root_kit.network.data_set_enable(!public_func.get_data_enable(context));
                            break;
                        case "/restartnetwork":
                            com.qwe7002.root_kit.network.restart_network();
                            break;
                        case "/setdatacard":
                            if (Paper.book("temp").contains("sub_id")) {
                                com.qwe7002.root_kit.network.set_data_sim(Paper.book("temp").read("sub_id"));
                                Paper.book("temp").destroy();
                            }
                            break;
                    }
                }
            }
        });
    }

    private int send_slot_temp = -1;
    private String send_to_temp;
    private String bot_username = "";
    private boolean privacy_mode;
    private static Thread thread_main;
    private String chat_id;
    private String bot_token;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = public_func.get_notification_obj(context, getString(R.string.chat_command_service_name));
        startForeground(public_func.CHAT_COMMAND_NOTIFY_ID, notification);
        return START_STICKY;
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

    boolean is_vpn_hotsport_exist() {
        ApplicationInfo info;
        try {
            info = getPackageManager().getApplicationInfo(public_func.VPN_HOTSPOT_PACKAGE_NAME, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            info = null;
        }

        return info != null;
    }


    @NotNull
    public static String get_battery_info(@NotNull Context context) {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        assert batteryManager != null;
        int battery_level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (battery_level > 100) {
            Log.i("get_battery_info", "The previous battery is over 100%, and the correction is 100%.");
            battery_level = 100;
        }
        IntentFilter intentfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, intentfilter);
        assert batteryStatus != null;
        int charge_status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        StringBuilder battery_string_builder = new StringBuilder().append(battery_level).append("%");
        switch (charge_status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
            case BatteryManager.BATTERY_STATUS_FULL:
                battery_string_builder.append(" (").append(context.getString(R.string.charging)).append(")");
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                switch (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    case BatteryManager.BATTERY_PLUGGED_AC:
                    case BatteryManager.BATTERY_PLUGGED_USB:
                    case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                        battery_string_builder.append(" (").append(context.getString(R.string.not_charging)).append(")");
                        break;
                }
                break;
        }
        return battery_string_builder.toString();
    }

    private boolean get_me() {
        OkHttpClient okhttp_client_new = okhttp_client;
        String request_uri = public_func.get_url(bot_token, "getMe");
        Request request = new Request.Builder().url(request_uri).build();
        Call call = okhttp_client_new.newCall(request);
        Response response;
        try {
            response = call.execute();
        } catch (IOException e) {
            e.printStackTrace();
            public_func.write_log(context, "Get username failed:" + e.getMessage());
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
                public_func.write_log(context, "Get the bot username: " + bot_username);
            }
            return true;
        }
        return false;
    }

    public static String get_network_type(@NotNull Context context, boolean cell_info) {
        String net_type = "Unknown";
        ConnectivityManager connect_manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        assert connect_manager != null;
        TelephonyManager telephonyManager = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        assert telephonyManager != null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Network[] networks = connect_manager.getAllNetworks();
            if (networks.length != 0) {
                for (Network network : networks) {
                    NetworkCapabilities network_capabilities = connect_manager.getNetworkCapabilities(network);
                    assert network_capabilities != null;
                    if (!network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            net_type = "WIFI";
                            break;
                        }
                        if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            if (network_capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)) {
                                continue;
                            }
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                                Log.i("get_network_type", "No permission.");
                                return net_type;
                            }
                            net_type = check_cellular_network_type(telephonyManager.getDataNetworkType());
                            if (cell_info) {
                                net_type += get_cell_info(context, telephonyManager, -1);
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
            NetworkInfo network_info = connect_manager.getActiveNetworkInfo();
            if (network_info == null) {
                return net_type;
            }
            switch (network_info.getType()) {
                case ConnectivityManager.TYPE_WIFI:
                    net_type = "WIFI";
                    break;
                case ConnectivityManager.TYPE_MOBILE:
                    net_type = check_cellular_network_type(network_info.getSubtype());
                    if (cell_info) {
                        net_type += get_cell_info(context, telephonyManager, -1);
                    }
                    break;
            }
        }

        return net_type;
    }

    @SuppressLint({"InvalidWakeLockTag", "WakelockTimeout"})
    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        connectivity_manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Paper.init(context);

        sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);

        chat_id = sharedPreferences.getString("chat_id", "");
        bot_token = sharedPreferences.getString("bot_token", "");
        okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book().read("proxy_config", new proxy_config()));
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
        intentFilter.addAction(public_func.BROADCAST_STOP_SERVICE);
        NetworkRequest network_request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build();
        callback = new network_callback();
        connectivity_manager.registerNetworkCallback(network_request, callback);
        broadcast_receiver = new broadcast_receiver();
        registerReceiver(broadcast_receiver, intentFilter);
    }

    private static String get_cell_info(Context context, TelephonyManager telephonyManager, int sub_id) {
        final int[] signal_arfcn = {-1};
        final int[] signal_strength = {0};
        final boolean[] run_lock = {false};
        String TAG = "get_cell_info";
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "get_cell_info: No permission.");
            return "";
        }
        StringBuilder result_string = new StringBuilder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (sub_id == -1) {
                SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                assert subscriptionManager != null;
                sub_id = SubscriptionManager.getDefaultDataSubscriptionId();
            }
            telephonyManager = telephonyManager.createForSubscriptionId(sub_id);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                Log.d("get_data_sim_id", "No permission.");
            }

            telephonyManager.requestCellInfoUpdate(AsyncTask.SERIAL_EXECUTOR, new TelephonyManager.CellInfoCallback() {
                @Override
                public void onCellInfo(@NonNull List<CellInfo> cell_info_result) {
                    Log.d(TAG, "cellinfo_size: " + cell_info_result.size());
                    if (cell_info_result.size() == 0) {
                        return;
                    }
                    CellInfo info = cell_info_result.get(0);

                    if (info instanceof CellInfoNr) {
                        signal_strength[0] = ((CellInfoNr) info).getCellSignalStrength().getDbm();
                        CellIdentityNr cell_identity = (CellIdentityNr) ((CellInfoNr) info).getCellIdentity();
                        signal_arfcn[0] = cell_identity.getNrarfcn();
                    }
                    if (info instanceof CellInfoLte) {
                        signal_strength[0] = ((CellInfoLte) info).getCellSignalStrength().getDbm();
                        signal_arfcn[0] = ((CellInfoLte) info).getCellIdentity().getEarfcn();
                    }
                    if (info instanceof CellInfoWcdma) {
                        signal_strength[0] = ((CellInfoWcdma) info).getCellSignalStrength().getDbm();
                        signal_arfcn[0] = ((CellInfoWcdma) info).getCellIdentity().getUarfcn();
                    }
                    run_lock[0] = true;
                }
            });
            while (!run_lock[0]) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        } else {
            for (CellInfo cell : telephonyManager.getAllCellInfo()) {
                if (!cell.isRegistered()) {
                    continue;
                }
                if (cell instanceof CellInfoLte) {
                    signal_strength[0] = ((CellInfoLte) cell).getCellSignalStrength().getDbm();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        signal_arfcn[0] = ((CellInfoLte) cell).getCellIdentity().getEarfcn();
                    }
                    break;
                }
                if (cell instanceof CellInfoWcdma) {
                    signal_strength[0] = ((CellInfoWcdma) cell).getCellSignalStrength().getDbm();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        signal_arfcn[0] = ((CellInfoWcdma) cell).getCellIdentity().getUarfcn();
                    }
                }
                if (cell instanceof CellInfoGsm) {
                    signal_strength[0] = ((CellInfoGsm) cell).getCellSignalStrength().getDbm();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        signal_arfcn[0] = ((CellInfoGsm) cell).getCellIdentity().getArfcn();
                    }

                }
            }
        }
        Log.d(TAG, "signal_strength: " + signal_strength[0]);
        Log.d(TAG, "signal_arfcn: " + signal_arfcn[0]);
        result_string.append(" (");
        if (signal_strength[0] != 0) {
            result_string.append(signal_strength[0]);
            result_string.append(" dBm");
        }
        if (signal_arfcn[0] != -1) {
            result_string.append(", ");
            result_string.append("ARFCN: ");
            result_string.append(signal_arfcn[0]);
        }
        result_string.append(")");
        return result_string.toString();
    }

    private static class SEND_SMS_STATUS {
        public static final int STANDBY_STATUS = -1;
        public static final int PHONE_INPUT_STATUS = 0;
        public static final int MESSAGE_INPUT_STATUS = 1;
        public static final int WAITING_TO_SEND_STATUS = 2;
    }


    private static String check_cellular_network_type(int type) {
        String net_type = "Unknown";
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_NR:
                net_type = "5G";
                break;
            case TelephonyManager.NETWORK_TYPE_LTE:
                net_type = "LTE";
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

    private String get_size(long size) {
        final int GB = 1024 * 1024 * 1024;
        final int MB = 1024 * 1024;
        final int KB = 1024;
        DecimalFormat df = new DecimalFormat("0.00");
        String resultSize;
        if (size / GB >= 1) {
            resultSize = df.format(size / (float) GB) + "GB";
        } else if (size / MB >= 1) {
            resultSize = df.format(size / (float) MB) + "MB";
        } else if (size / KB >= 1) {
            resultSize = df.format(size / (float) KB) + "KB";
        } else {
            resultSize = size + "Byte";

        }
        return resultSize;
    }


    private void when_network_change() {
        if (public_func.check_network_status(context)) {
            if (!thread_main.isAlive()) {
                public_func.write_log(context, "Network connections has been restored.");
                thread_main = new Thread(new thread_main_runnable());
                thread_main.start();
            }
        }
    }

    private class broadcast_receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, @NotNull Intent intent) {
            Log.d(TAG, "onReceive: " + intent.getAction());
            assert intent.getAction() != null;
            if (public_func.BROADCAST_STOP_SERVICE.equals(intent.getAction())) {
                Log.i(TAG, "Received stop signal, quitting now...");
                stopSelf();
                Process.killProcess(Process.myPid());
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private class network_callback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(@NotNull Network network) {
            Log.d(TAG, "onAvailable");
            when_network_change();
        }
    }

    class thread_main_runnable implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "run: thread main start");
            if (public_func.parse_string_to_long(chat_id) < 0) {
                bot_username = Paper.book().read("bot_username", null);
                if (bot_username == null) {
                    while (!get_me()) {
                        public_func.write_log(context, "Failed to get bot Username, Wait 5 seconds and try again.");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                Log.i(TAG, "run: The Bot Username is loaded. The Bot Username is: " + bot_username);
            }
            while (true) {
                int timeout = 5 * magnification;
                int http_timeout = timeout + 5;
                OkHttpClient okhttp_client_new = okhttp_client.newBuilder()
                        .readTimeout(http_timeout, TimeUnit.SECONDS)
                        .writeTimeout(http_timeout, TimeUnit.SECONDS)
                        .build();
                //Log.d(TAG, "run: Current timeout:" + timeout);
                String request_uri = public_func.get_url(bot_token, "getUpdates");
                polling_json request_body = new polling_json();
                request_body.offset = offset;
                request_body.timeout = timeout;
                RequestBody body = RequestBody.create(new Gson().toJson(request_body), public_func.JSON);
                Request request = new Request.Builder().url(request_uri).method("POST", body).build();
                Call call = okhttp_client_new.newCall(request);
                Response response;
                try {
                    response = call.execute();
                    error_magnification = 1;
                } catch (IOException e) {
                    e.printStackTrace();
                    if (!public_func.check_network_status(context)) {
                        public_func.write_log(context, "No network connections available. ");
                        error_magnification = 1;
                        magnification = 1;
                        break;
                    }
                    int sleep_time = 5 * error_magnification;
                    if (sleep_time > 5) {
                        public_func.write_log(context, "Connection to the Telegram API service failed, try again after " + sleep_time + " seconds.");
                    } else {
                        Log.i(TAG, "run: Connection to the Telegram API service failed");
                    }
                    magnification = 1;
                    if (error_magnification <= 59) {
                        ++error_magnification;
                    }
                    try {
                        Thread.sleep(sleep_time * 1000);
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
                    JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject();
                    if (result_obj.get("ok").getAsBoolean()) {
                        JsonArray result_array = result_obj.get("result").getAsJsonArray();
                        for (JsonElement item : result_array) {
                            receive_handle(item.getAsJsonObject());
                        }
                    }
                    if (magnification <= 11) {
                        ++magnification;
                    }
                } else {
                    public_func.write_log(context, "response code:" + response.code());
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
                        public_func.send_fallback_sms(context, result_message, -1);
                        public_func.stop_all_service(context);
                        break;
                    }
                }
            }
        }
    }
}

