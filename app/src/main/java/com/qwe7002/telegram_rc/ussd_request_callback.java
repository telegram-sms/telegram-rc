package com.qwe7002.telegram_rc;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.qwe7002.telegram_rc.config.proxy;
import com.qwe7002.telegram_rc.data_structure.request_message;
import com.qwe7002.telegram_rc.static_class.const_value;
import com.qwe7002.telegram_rc.static_class.log_func;
import com.qwe7002.telegram_rc.static_class.network_func;
import com.qwe7002.telegram_rc.static_class.resend_func;
import com.qwe7002.telegram_rc.static_class.sms_func;

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

public class ussd_request_callback extends TelephonyManager.UssdResponseCallback {

    private final Context context;
    private final boolean doh_switch;
    private String request_uri;
    private final String message_header;
    private final request_message request_body;

    public ussd_request_callback(Context context, @NotNull SharedPreferences sharedPreferences, long message_id) {
        this.context = context;
        Paper.init(context);
        String chat_id = sharedPreferences.getString("chat_id", "");
        this.doh_switch = sharedPreferences.getBoolean("doh_switch", true);
        this.request_body = new request_message();
        this.request_body.chat_id = chat_id;
        this.request_body.message_thread_id = sharedPreferences.getString("message_thread_id", "");
        String bot_token = sharedPreferences.getString("bot_token", "");
        this.request_uri = network_func.get_url(bot_token, "SendMessage");
        if (message_id != -1) {
            this.request_uri = network_func.get_url(bot_token, "editMessageText");
            this.request_body.message_id = message_id;
        }
        this.message_header = context.getString(R.string.send_ussd_head);
    }

    @Override
    public void onReceiveUssdResponse(TelephonyManager telephonyManager, String request, CharSequence response) {
        super.onReceiveUssdResponse(telephonyManager, request, response);
        String message = message_header + "\n" + context.getString(R.string.request) + request + "\n" + context.getString(R.string.content) + response.toString();
        network_progress_handle(message);
    }

    @Override
    public void onReceiveUssdResponseFailed(TelephonyManager telephonyManager, String request, int failureCode) {
        super.onReceiveUssdResponseFailed(telephonyManager, request, failureCode);
        String message = message_header + "\n" + context.getString(R.string.request) + request + "\n" + context.getString(R.string.error_message) + get_error_code_string(failureCode);
        network_progress_handle(message);
    }

    private void network_progress_handle(String message) {
        request_body.text = message;
        String request_body_json = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(request_body_json, const_value.JSON);
        OkHttpClient okhttp_client = network_func.get_okhttp_obj(doh_switch, Paper.book("system_config").read("proxy_config", new proxy()));
        Request request_obj = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request_obj);
        final String error_head = "Send USSD failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                log_func.write_log(context, error_head + e.getMessage());
                sms_func.send_fallback_sms(context, request_body.text, -1);
                resend_func.add_resend_loop(context, request_body.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() != 200) {
                    assert response.body() != null;
                    log_func.write_log(context, error_head + response.code() + " " + Objects.requireNonNull(response.body()).string());
                    sms_func.send_fallback_sms(context, request_body.text, -1);
                    resend_func.add_resend_loop(context, request_body.text);
                }
            }
        });
    }

    private String get_error_code_string(int error_code) {
        String result;
        switch (error_code) {
            case -1:
                result = "Connection problem or invalid MMI code.";
                break;
            case -2:
                result = "No service.";
                break;
            default:
                result = "An unknown error occurred (" + error_code + ")";
                break;
        }
        return result;
    }
}
