package com.qwe7002.telegram_rc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class wifi_status_change_receiver extends BroadcastReceiver {
    private static final String TAG = "wifi_status_change_receiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Paper.init(context);
        Log.d(TAG, "Receive action: " + intent.getAction());
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, wifi status receiver is deactivated.");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = public_func.get_url(bot_token, "sendMessage");
        NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            Log.i(TAG, "Connect to the network: " + wifiInfo.getSSID());
            String message = context.getString(R.string.system_message_head) + "\n" + "Connect to the network: " + wifiInfo.getSSID();
            message_json request_body = new message_json();
            request_body.chat_id = chat_id;
            request_body.text = message;
            String request_body_json = new Gson().toJson(request_body);
            RequestBody body = RequestBody.create(request_body_json, public_func.JSON);
            OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book("system_config").read("proxy_config", new proxy_config()));
            Request request = new Request.Builder().url(request_uri).method("POST", body).build();
            Call call = okhttp_client.newCall(request);
            final String error_head = "Send wifi status failed:";
            call.enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    e.printStackTrace();
                    public_func.write_log(context, error_head + e.getMessage());
                    public_func.add_resend_loop(context, request_body.text);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.code() != 200) {
                        assert response.body() != null;
                        public_func.write_log(context, error_head + response.code() + " " + Objects.requireNonNull(response.body()).string());
                        public_func.add_resend_loop(context, request_body.text);
                    }
                }
            });
        }
    }
}
