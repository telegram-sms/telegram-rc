package com.qwe7002.telegram_rc;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsManager;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.qwe7002.telegram_rc.data_structure.request_message;
import com.qwe7002.telegram_rc.static_class.CONST;
import com.qwe7002.telegram_rc.static_class.log;
import com.qwe7002.telegram_rc.static_class.network;
import com.qwe7002.telegram_rc.static_class.resend;
import com.qwe7002.telegram_rc.static_class.sms;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class sms_send_receiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, @NotNull Intent intent) {
        Paper.init(context);
        final String TAG = "sms_send_receiver";
        android.util.Log.d(TAG, "Receive action: " + intent.getAction());
        Bundle extras = intent.getExtras();
        assert extras != null;
        int sub = extras.getInt("sub_id");
        context.getApplicationContext().unregisterReceiver(this);
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            android.util.Log.i(TAG, "Uninitialized, SMS send receiver is deactivated.");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        final request_message request_body = new request_message();
        request_body.chat_id = chat_id;
        request_body.message_thread_id = sharedPreferences.getString("message_thread_id", "");
        String request_uri = network.getUrl(bot_token, "sendMessage");
        long message_id = extras.getLong("message_id");
        if (message_id != -1) {
            android.util.Log.d(TAG, "Find the message_id and switch to edit mode.");
            request_uri = network.getUrl(bot_token, "editMessageText");
            request_body.message_id = message_id;
        }
        String result_status = "Unknown";
        switch (getResultCode()) {
            case Activity.RESULT_OK:
                result_status = context.getString(R.string.success);
                break;
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                result_status = context.getString(R.string.send_failed);
                break;
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                result_status = context.getString(R.string.airplane_mode);
                break;
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                result_status = context.getString(R.string.no_network);
                break;
        }
        request_body.text = extras.getString("message_text") + "\n" + context.getString(R.string.status) + result_status;
        String request_body_raw = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(request_body_raw, CONST.JSON);
        OkHttpClient okhttp_client = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        final String error_head = "Send SMS status failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                log.writeLog(context, error_head + e.getMessage());
                sms.sendFallbackSMS(context, request_body.text, sub);
                resend.addResendLoop(context, request_body.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    assert response.body() != null;
                    log.writeLog(context, error_head + response.code() + " " + Objects.requireNonNull(response.body()).string());
                    resend.addResendLoop(context, request_body.text);
                }
            }
        });
    }
}
