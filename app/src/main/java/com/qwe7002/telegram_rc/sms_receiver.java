package com.qwe7002.telegram_rc;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.github.sumimakito.codeauxlib.CodeauxLibPortable;
import com.google.gson.Gson;
import com.qwe7002.telegram_rc.config.proxy;
import com.qwe7002.telegram_rc.data_structure.request_message;
import com.qwe7002.telegram_rc.static_class.const_value;
import com.qwe7002.telegram_rc.static_class.log_func;
import com.qwe7002.telegram_rc.static_class.network_func;
import com.qwe7002.telegram_rc.static_class.other_func;
import com.qwe7002.telegram_rc.static_class.resend_func;
import com.qwe7002.telegram_rc.static_class.service_func;
import com.qwe7002.telegram_rc.static_class.sms_func;
import com.qwe7002.telegram_rc.static_class.ussd_func;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class sms_receiver extends BroadcastReceiver {
    final static CodeauxLibPortable code_aux_lib = new CodeauxLibPortable();

    public void onReceive(final Context context, @NotNull Intent intent) {
        Paper.init(context);
        final String TAG = "sms_receiver";
        Log.d(TAG, "Receive action: " + intent.getAction());
        Bundle extras = intent.getExtras();
        assert extras != null;
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, SMS receiver is deactivated.");
            return;
        }
        assert intent.getAction() != null;
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = network_func.get_url(bot_token, "sendMessage");

        int slot = extras.getInt("slot", -1);
        final int sub_id = extras.getInt("subscription", -1);
        if (other_func.get_active_card(context) >= 2 && slot == -1) {
            SubscriptionManager manager = SubscriptionManager.from(context);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                SubscriptionInfo info = manager.getActiveSubscriptionInfo(sub_id);
                slot = info.getSimSlotIndex();
            }
        }
        Log.d(TAG, "onReceive: " + slot);
        final int final_slot = slot;
        String dual_sim = other_func.get_dual_sim_card_display(context, slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));

        Object[] pdus = (Object[]) extras.get("pdus");
        assert pdus != null;
        final SmsMessage[] messages = new SmsMessage[pdus.length];
        for (int i = 0; i < pdus.length; ++i) {
            String format = extras.getString("format");
            Log.d(TAG, "format: " + format);
            assert format != null;
            messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
        }
        if (messages.length == 0) {
            log_func.write_log(context, "Message length is equal to 0.");
            return;
        }

        StringBuilder message_body_builder = new StringBuilder();
        for (SmsMessage item : messages) {
            message_body_builder.append(item.getMessageBody());
        }

        final String message_body = message_body_builder.toString();
        final String message_address = messages[0].getOriginatingAddress();
        assert message_address != null;

        String trusted_phone_number = sharedPreferences.getString("trusted_phone_number", null);
        boolean is_trusted_phone = false;
        if (trusted_phone_number != null && trusted_phone_number.length() != 0) {
            is_trusted_phone = message_address.contains(trusted_phone_number);
        }
        Log.d(TAG, "onReceive: " + is_trusted_phone);
        final request_message request_body = new request_message();
        request_body.chat_id = chat_id;

        String message_body_html = message_body;
        String flash_sms_string = "";
        if (messages[0].getMessageClass() == SmsMessage.MessageClass.CLASS_0) {
            flash_sms_string = "\nType: Class 0";
        }
        final String message_head = "[" + dual_sim + context.getString(R.string.receive_sms_head) + "]" + flash_sms_string + "\n" + context.getString(R.string.from) + message_address + "\n" + context.getString(R.string.content);
        String raw_request_body_text = message_head + message_body;
        boolean is_verification_code = false;
        if (sharedPreferences.getBoolean("verification_code", false) && !is_trusted_phone) {
            if (message_body.length() <= 140) {
                String verification = code_aux_lib.find(message_body);
                if (verification != null) {
                    request_body.parse_mode = "html";
                    message_body_html = message_body
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("&", "&amp;")
                            .replace(verification, "<code>" + verification + "</code>");
                    is_verification_code = true;
                }
            } else {
                log_func.write_log(context, "SMS exceeds 140 characters, no verification code is recognized.");
            }

        }
        request_body.text = message_head + message_body_html;
        final boolean data_enable = network_func.get_data_enable(context);
        if (is_trusted_phone) {
            String message_command = message_body.toLowerCase().replace("_", "");
            String[] message_command_list = message_command.split("\n");
            if (message_command_list.length > 0) {
                switch (message_command_list[0]) {
                    case "/restartservice":
                        new Thread(() -> {
                            service_func.stop_all_service(context);
                            service_func.start_service(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false), sharedPreferences.getBoolean("wifi_monitor_switch", false));
                        }).start();
                        raw_request_body_text = context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.restart_service);
                        request_body.text = raw_request_body_text;
                        break;
                    case "/switchdata":
                        if (!data_enable) {
                            open_data(context);
                        }
                        raw_request_body_text = context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.switch_data);
                        request_body.text = raw_request_body_text;
                        break;
                    case "/restartnetwork":
                        raw_request_body_text = context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.restart_network);
                        request_body.text = raw_request_body_text;
                        break;
                    case "/sendussd":
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                if (message_command_list.length == 2) {
                                    ussd_func.send_ussd(context, message_command_list[1], sub_id);
                                    return;
                                }
                            }
                        } else {
                            Log.i(TAG, "send_ussd: No permission.");
                        }
                        break;
                    case "/setdatacard":
                        if (!sharedPreferences.getBoolean("root", false)) {
                            request_body.text = context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.no_permission);
                            break;
                        }
                        if (other_func.get_active_card(context) == 2) {
                            Paper.book("temp").write("sub_id", sub_id);
                            request_body.text = context.getString(R.string.system_message_head) + "\n" + "Current data card: SIM" + other_func.get_data_sim_id(context);
                        }
                        break;
                    case "/sendsms":
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                            Log.i(TAG, "No SMS permission.");
                            break;
                        }
                        String msg_send_to = other_func.get_send_phone_number(message_command_list[1]);
                        if (other_func.is_phone_number(msg_send_to) && message_command_list.length > 2) {
                            StringBuilder msg_send_content = new StringBuilder();
                            for (int i = 2; i < message_command_list.length; ++i) {
                                if (i != 2) {
                                    msg_send_content.append("\n");
                                }
                                msg_send_content.append(message_command_list[i]);
                            }
                            new Thread(() -> sms_func.send_sms(context, msg_send_to, msg_send_content.toString(), final_slot, sub_id)).start();
                            return;
                        }
                        break;

                }
            }
        }
        Log.d(TAG, "onReceive: " + is_verification_code);
        if (!is_verification_code && !is_trusted_phone) {
            Log.d(TAG, "onReceive: ");
            ArrayList<String> black_list_array = Paper.book("system_config").read("block_keyword_list", new ArrayList<>());
            for (String block_list_item : black_list_array) {
                if (block_list_item.isEmpty()) {
                    continue;
                }
                if (message_body.contains(block_list_item)) {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(context.getString(R.string.time_format), Locale.UK);
                    String write_message = request_body.text + "\n" + context.getString(R.string.time) + simpleDateFormat.format(new Date(System.currentTimeMillis()));
                    ArrayList<String> spam_sms_list;
                    Paper.init(context);
                    spam_sms_list = Paper.book().read("spam_sms_list", new ArrayList<>());
                    if (spam_sms_list.size() >= 5) {
                        spam_sms_list.remove(0);
                    }
                    spam_sms_list.add(write_message);
                    Paper.book().write("spam_sms_list", spam_sms_list);
                    Log.i(TAG, "Detected message contains blacklist keywords, add spam list");
                    return;
                }
            }
        }


        RequestBody body = RequestBody.create(new Gson().toJson(request_body), const_value.JSON);
        OkHttpClient okhttp_client = network_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        final String error_head = "Send SMS forward failed:";
        final String final_raw_request_body_text = raw_request_body_text;
        boolean final_is_flash = (messages[0].getMessageClass() == SmsMessage.MessageClass.CLASS_0);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                log_func.write_log(context, error_head + e.getMessage());
                sms_func.send_fallback_sms(context, final_raw_request_body_text, sub_id);
                resend_func.add_resend_loop(context, request_body.text);
                command_handle(sharedPreferences, message_body, data_enable);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                assert response.body() != null;
                String result = Objects.requireNonNull(response.body()).string();
                if (response.code() != 200) {
                    log_func.write_log(context, error_head + response.code() + " " + result);
                    if (!final_is_flash) {
                        sms_func.send_fallback_sms(context, final_raw_request_body_text, sub_id);
                    }
                    resend_func.add_resend_loop(context, request_body.text);
                } else {
                    if (!other_func.is_phone_number(message_address)) {
                        log_func.write_log(context, "[" + message_address + "] Not a regular phone number.");
                        return;
                    }
                    other_func.add_message_list(other_func.get_message_id(result), message_address, final_slot);
                }
                command_handle(sharedPreferences, message_body, data_enable);
            }
        });
    }

    private void command_handle(@NotNull SharedPreferences sharedPreferences, String message_body, boolean data_enable) {
        if (sharedPreferences.getBoolean("root", false)) {
            switch (message_body.toLowerCase().replace("_", "")) {
                case "/switchdata":
                    if (data_enable) {
                        com.qwe7002.telegram_rc.root_kit.network.data_set_enable(false);
                    }
                    break;
                case "/restartnetwork":
                    com.qwe7002.telegram_rc.root_kit.network.restart_network();
                    break;
                case "/setdatacard":
                    if (Paper.book("temp").contains("sub_id")) {
                        com.qwe7002.telegram_rc.root_kit.network.set_data_sim(Paper.book("temp").read("sub_id"));
                        Paper.book("temp").destroy();
                    }
                    break;
            }
        }
    }

    void open_data(Context context) {
        com.qwe7002.telegram_rc.root_kit.network.data_set_enable(true);
        int loop_count = 0;
        while (!network_func.check_network_status(context)) {
            if (loop_count >= 100) {
                break;
            }
            try {
                //noinspection BusyWait
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ++loop_count;
        }
    }
}


