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
import com.qwe7002.telegram_rc.data_structure.request_message;
import com.qwe7002.telegram_rc.static_class.CONST;
import com.qwe7002.telegram_rc.static_class.log;
import com.qwe7002.telegram_rc.static_class.network;
import com.qwe7002.telegram_rc.static_class.other;
import com.qwe7002.telegram_rc.static_class.Resend;
import com.qwe7002.telegram_rc.static_class.service;
import com.qwe7002.telegram_rc.static_class.sms;
import com.qwe7002.telegram_rc.static_class.ussd;

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
        android.util.Log.d(TAG, "Receive action: " + intent.getAction());
        Bundle extras = intent.getExtras();
        assert extras != null;
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            android.util.Log.i(TAG, "Uninitialized, SMS receiver is deactivated.");
            return;
        }
        assert intent.getAction() != null;
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = network.getUrl(bot_token, "sendMessage");

        int intentSlot = extras.getInt("slot", -1);
        final int subId = extras.getInt("subscription", -1);
        if (other.getActiveCard(context) >= 2 && intentSlot == -1) {
            SubscriptionManager manager = SubscriptionManager.from(context);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                SubscriptionInfo info = manager.getActiveSubscriptionInfo(subId);
                intentSlot = info.getSimSlotIndex();
            }
        }
        final int slot = intentSlot;
        String dual_sim = other.getDualSimCardDisplay(context, intentSlot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));

        Object[] pdus = (Object[]) extras.get("pdus");
        assert pdus != null;
        final SmsMessage[] messages = new SmsMessage[pdus.length];
        for (int i = 0; i < pdus.length; ++i) {
            String format = extras.getString("format");
            android.util.Log.d(TAG, "format: " + format);
            assert format != null;
            messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i], format);
        }
        if (messages.length == 0) {
            log.writeLog(context, "Message length is equal to 0.");
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
        if (trusted_phone_number != null && !trusted_phone_number.isEmpty()) {
            is_trusted_phone = message_address.contains(trusted_phone_number);
        }
        android.util.Log.d(TAG, "onReceive: " + is_trusted_phone);
        final request_message request_body = new request_message();
        request_body.chat_id = chat_id;
        request_body.message_thread_id = sharedPreferences.getString("message_thread_id", "");
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
                log.writeLog(context, "SMS exceeds 140 characters, no verification code is recognized.");
            }

        }
        request_body.text = message_head + message_body_html;
        final boolean data_enable = network.getDataEnable(context);
        if (is_trusted_phone) {
            String message_command = message_body.toLowerCase().replace("_", "");
            String[] command_list = message_command.split("\n");
            if (command_list.length > 0) {
                String[] message_list = message_body.split("\n");
                switch (command_list[0]) {
                    case "/restartservice":
                        new Thread(() -> {
                            service.stopAllService(context);
                            service.startService(context, sharedPreferences.getBoolean("battery_monitoring_switch", false), sharedPreferences.getBoolean("chat_command", false));
                        }).start();
                        raw_request_body_text = context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.restart_service);
                        request_body.text = raw_request_body_text;
                        break;
                    case "/switchdata":
                        if (!data_enable) {
                            openData(context);
                        }
                        raw_request_body_text = context.getString(R.string.system_message_head) + "\n" + context.getString(R.string.switch_data);
                        request_body.text = raw_request_body_text;
                        break;
                    case "/sendussd":
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                            if (command_list.length == 2) {
                                ussd.send_ussd(context, message_list[1], subId);
                                return;
                            }
                        }
                        break;
                    case "/sendsms":
                    case "/sendsms1":
                    case "/sendsms2":
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                            android.util.Log.i(TAG, "No SMS permission.");
                            break;
                        }
                        String msg_send_to = other.getSendPhoneNumber(command_list[1]);
                        if (other.isPhoneNumber(msg_send_to) && message_list.length > 2) {
                            StringBuilder msg_send_content = new StringBuilder();
                            for (int i = 2; i < message_list.length; ++i) {
                                if (i != 2) {
                                    msg_send_content.append("\n");
                                }
                                msg_send_content.append(message_list[i]);
                            }
                            int send_slot = intentSlot;
                            if (other.getActiveCard(context) > 1) {
                                send_slot = switch (command_list[0].trim()) {
                                    case "/sendsms1" -> 0;
                                    case "/sendsms2" -> 1;
                                    default -> send_slot;
                                };
                            }
                            final int final_send_slot = send_slot;
                            final int final_send_sub_id = other.getSubId(context, final_send_slot);
                            new Thread(() -> sms.sendSMS(context, msg_send_to, msg_send_content.toString(), final_send_slot, final_send_sub_id)).start();
                            return;
                        }
                        break;

                }
            }
        }
        android.util.Log.d(TAG, "onReceive: " + is_verification_code);
        if (!is_verification_code && !is_trusted_phone) {
            android.util.Log.d(TAG, "onReceive: ");
            ArrayList<String> black_list_array = Paper.book("system_config").read("block_keyword_list", new ArrayList<>());
            assert black_list_array != null;
            for (String block_list_item : black_list_array) {
                if (block_list_item.isEmpty()) {
                    continue;
                }
                if (message_body.contains(block_list_item)) {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(context.getString(R.string.time_format), Locale.UK);
                    String write_message = request_body.text + "\n" + context.getString(R.string.time) + simpleDateFormat.format(new Date(System.currentTimeMillis()));
                    ArrayList<String> spamSmsList;
                    Paper.init(context);
                    spamSmsList = Paper.book().read("spam_sms_list", new ArrayList<>());
                    assert spamSmsList != null;
                    if (spamSmsList.size() >= 5) {
                        spamSmsList.remove(0);
                    }
                    spamSmsList.add(write_message);
                    Paper.book().write("spam_sms_list", spamSmsList);
                    android.util.Log.i(TAG, "Detected message contains blacklist keywords, add spam list");
                    return;
                }
            }
        }


        RequestBody body = RequestBody.create(new Gson().toJson(request_body), CONST.JSON);
        OkHttpClient okhttp_client = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        final String error_head = "Send SMS forward failed:";
        final String final_raw_request_body_text = raw_request_body_text;
        boolean final_is_flash = (messages[0].getMessageClass() == SmsMessage.MessageClass.CLASS_0);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.d(TAG, String.valueOf(e));
                log.writeLog(context, error_head + e.getMessage());
                sms.sendFallbackSMS(context, final_raw_request_body_text, subId);
                Resend.addResendLoop(request_body.text);
                commandHandle(sharedPreferences, message_body, data_enable);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String result = Objects.requireNonNull(response.body()).string();
                if (response.code() != 200) {
                    log.writeLog(context, error_head + response.code() + " " + result);
                    if (!final_is_flash) {
                        sms.sendFallbackSMS(context, final_raw_request_body_text, subId);
                    }
                    Resend.addResendLoop(request_body.text);
                } else {
                    if (!other.isPhoneNumber(message_address)) {
                        log.writeLog(context, "[" + message_address + "] Not a regular phone number.");
                        return;
                    }
                    other.addMessageList(other.getMessageId(result), message_address, slot);
                }
                commandHandle(sharedPreferences, message_body, data_enable);
            }
        });
    }

    private void commandHandle(@NotNull SharedPreferences sharedPreferences, String message_body, boolean data_enable) {
        if (sharedPreferences.getBoolean("root", false)) {
            if (message_body.toLowerCase().replace("_", "").equals("/data")) {
                if (data_enable) {
                    com.qwe7002.telegram_rc.root_kit.Networks.setData(false);
                }
            }
        }
    }

    void openData(Context context) {
        com.qwe7002.telegram_rc.root_kit.Networks.setData(true);
        int loopCount = 0;
        while (!network.checkNetworkStatus(context)) {
            if (loopCount >= 100) {
                break;
            }
            try {
                //noinspection BusyWait
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Log.d("openData", String.valueOf(e));
            }
            ++loopCount;
        }
    }
}


