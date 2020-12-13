package com.qwe7002.telegram_rc;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.qwe7002.telegram_rc.config.proxy;
import com.qwe7002.telegram_rc.data_structure.request_message;
import com.qwe7002.telegram_rc.static_class.const_value;
import com.qwe7002.telegram_rc.static_class.log_func;
import com.qwe7002.telegram_rc.static_class.network_func;
import com.qwe7002.telegram_rc.static_class.other_func;
import com.qwe7002.telegram_rc.static_class.resend_func;

import java.io.IOException;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class wifi_connect_status_service extends Service {
    private String chat_id;
    private String request_uri;
    private boolean doh_switch;
    private static final String TAG = "wifi_status_change_receiver";
    private Context context;
    private wifi_status_change_receiver wifi_status_change_receiver = null;
    private static NetworkInfo.State last_connect_status = NetworkInfo.State.DISCONNECTED;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = other_func.get_notification_obj(context, getString(R.string.wifi_status));
        startForeground(notify_id.WIFI_CONNECT_STATUS, notification);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        Paper.init(context);
        wifi_status_change_receiver = new wifi_status_change_receiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(const_value.BROADCAST_STOP_SERVICE);
        registerReceiver(wifi_status_change_receiver, filter);
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, wifi status receiver is deactivated.");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        chat_id = sharedPreferences.getString("chat_id", "");
        request_uri = network_func.get_url(bot_token, "sendMessage");
        doh_switch = sharedPreferences.getBoolean("doh_switch", true);

    }

    @Override
    public void onDestroy() {
        unregisterReceiver(wifi_status_change_receiver);
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    class wifi_status_change_receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(const_value.BROADCAST_STOP_SERVICE)) {
                Log.i(TAG, "Received stop signal, quitting now...");
                stopSelf();
                android.os.Process.killProcess(android.os.Process.myPid());
                return;
            }
            Log.d(TAG, "Receive action: " + intent.getAction());
            NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                if (last_connect_status == NetworkInfo.State.CONNECTED) {
                    Log.d(TAG, "onReceive: Repeat broadcast");
                    return;
                }
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String message = context.getString(R.string.system_message_head) + "\n" + getString(R.string.connect_to_the_network) + wifiInfo.getSSID();
                request_message request_body = new request_message();
                request_body.chat_id = chat_id;
                request_body.text = message;
                String request_body_json = new Gson().toJson(request_body);
                RequestBody body = RequestBody.create(request_body_json, const_value.JSON);
                OkHttpClient okhttp_client = network_func.get_okhttp_obj(doh_switch, Paper.book("system_config").read("proxy_config", new proxy()));
                Request request = new Request.Builder().url(request_uri).method("POST", body).build();
                Call call = okhttp_client.newCall(request);
                final String error_head = "Send wifi status failed:";
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        e.printStackTrace();
                        log_func.write_log(context, error_head + e.getMessage());
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
            last_connect_status = info.getState();
        }
    }


}

