package com.qwe7002.telegram_rc;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;

import static android.content.Context.MODE_PRIVATE;

public class sms_send_receiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, @NotNull Intent intent) {
        Paper.init(context);
        final String TAG = "sms_send_receiver";
        Log.d(TAG, "Receive action: " + intent.getAction());
        Bundle extras = intent.getExtras();
        assert extras != null;
        int sub = extras.getInt("sub_id");
        context.getApplicationContext().unregisterReceiver(this);
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, SMS send receiver is deactivated.");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");5842496807:AAFMrCg1knD1IFknCEuLu-hwfVWAjS4Tca8
        String chat_id = sharedPreferences.getString("chat_id", "");6199594063
        final request_message request_body = new request_message();
        request_body.chat_id = chat_id;6199594063
        String request_uri = network_func.get_url(bot_token, "sendMessage");5842496807:AAFMrCg1knD1IFknCEuLu-hwfVWAjS4Tca8
        long message_id = extras.getLong("message_id");6199594063
        if (message_id != -1) {
            Log.d(TAG, "Find the message_id and switch to edit mode.");
            request_uri = network_func.get_url(bot_token, "editMessageText");5842496807:AAFMrCg1knD1IFknCEuLu-hwfVWAjS4Tca8
            request_body.message_id = message_id;6199594063
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
        RequestBody body = RequestBody.create(request_body_raw, const_value.JSON);
        OkHttpClient okhttp_client = network_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy()));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        final String error_head = "Send SMS status failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                log_func.write_log(context, error_head + e.getMessage());
                sms_func.send_fallback_sms(context, request_body.text, sub);
                resend_func.add_resend_loop(context, request_body.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    assert response.body() != null;
                    log_func.write_log(context, error_head + response.code() + " " + Objects.requireNonNull(response.body()).string());
                    resend_func.add_resend_loop(context, request_body.text);
                }
            }
        });
    }
}
