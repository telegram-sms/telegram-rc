package com.qwe7002.telegram_rc.static_class;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.telephony.SmsManager;

import androidx.core.content.PermissionChecker;

import com.google.gson.Gson;
import com.qwe7002.telegram_rc.R;
import com.qwe7002.telegram_rc.data_structure.request_message;
import com.qwe7002.telegram_rc.sms_send_receiver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class sms {
    public static void send_fallback_sms(Context context, String content, int sub_id) {
        final String TAG = "send_fallback_sms";
        if (androidx.core.content.PermissionChecker.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PermissionChecker.PERMISSION_GRANTED) {
            android.util.Log.d(TAG, ": No permission.");
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        String trust_number = sharedPreferences.getString("trusted_phone_number", null);
        if (trust_number == null) {
            android.util.Log.i(TAG, "The trusted number is empty.");
            return;
        }
        if (!sharedPreferences.getBoolean("fallback_sms", false)) {
            android.util.Log.i(TAG, "Did not open the SMS to fall back.");
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

    public static void send_sms(Context context, String send_to, String content, int slot, int sub_id) {
        send_sms(context, send_to, content, slot, sub_id, -1);
    }

    public static void send_sms(Context context, String send_to, String content, int slot, int sub_id, long message_id) {
        if (PermissionChecker.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PermissionChecker.PERMISSION_GRANTED) {
            android.util.Log.d("send_sms", "No permission.");
            return;
        }
        if (!other.isPhoneNumber(send_to)) {
            log.writeLog(context, "[" + send_to + "] is an illegal phone number");
            return;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = network.getUrl(bot_token, "sendMessage");
        if (message_id != -1) {
            android.util.Log.d("send_sms", "Find the message_id and switch to edit mode.");
            request_uri = network.getUrl(bot_token, "editMessageText");
        }
        request_message request_body = new request_message();
        request_body.chat_id = chat_id;
        SmsManager sms_manager;
        if (sub_id == -1) {
            sms_manager = SmsManager.getDefault();
        } else {
            sms_manager = SmsManager.getSmsManagerForSubscriptionId(sub_id);
        }
        String dual_sim = other.getDualSimCardDisplay(context, slot, sharedPreferences.getBoolean("display_dual_sim_display_name", false));
        String send_content = "[" + dual_sim + context.getString(R.string.send_sms_head) + "]" + "\n" + context.getString(R.string.to) + send_to + "\n" + context.getString(R.string.content) + content;
        request_body.text = send_content + "\n" + context.getString(R.string.status) + context.getString(R.string.sending);
        request_body.message_id = message_id;
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
            if (message_id == -1) {
                message_id = other.getMessageId(Objects.requireNonNull(response.body()).string());
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.writeLog(context, "failed to send message:" + e.getMessage());
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
}
