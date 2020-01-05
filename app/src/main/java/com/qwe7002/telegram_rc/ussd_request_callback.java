package com.qwe7002.telegram_rc;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@RequiresApi(api = Build.VERSION_CODES.O)
class ussd_request_callback extends TelephonyManager.UssdResponseCallback {
    private String TAG = "ussd_request_callback";
    private String chat_id;
    private Context context;
    private boolean doh_switch;
    private String request_uri;

    ussd_request_callback(Context context, String token, String chat_id, Boolean doh_switch) {
        this.context = context;
        this.chat_id = chat_id;
        this.doh_switch = doh_switch;
        request_uri = public_func.get_url(token, "SendMessage");
    }

    @Override
    public void onReceiveUssdResponse(TelephonyManager telephonyManager, String request, CharSequence response) {
        super.onReceiveUssdResponse(telephonyManager, request, response);
        Log.d(TAG, "onReceiveUssdResponse: " + request);
        Log.d(TAG, "onReceiveUssdResponse: " + response.toString());

        String message = context.getString(R.string.system_message_head) + "\n" + response.toString();
        message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        request_body.text = message;
        String request_body_json = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(request_body_json, public_func.JSON);
        OkHttpClient okhttp_client = public_func.get_okhttp_obj(doh_switch);
        Request request_obj = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request_obj);
        final String error_head = "Send USSD failed:";
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
            }
        });
    }

    @Override
    public void onReceiveUssdResponseFailed(TelephonyManager telephonyManager, String request, int failureCode) {
        super.onReceiveUssdResponseFailed(telephonyManager, request, failureCode);
        Log.d(TAG, "onReceiveUssdResponseFailed: " + request);
        Log.d(TAG, "onReceiveUssdResponseFailed: " + failureCode);
    }
}