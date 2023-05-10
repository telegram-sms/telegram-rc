package com.qwe7002.telegram_rc;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.util.Log;

import com.fitc.wifihotspot.TetherManager;
import com.google.gson.Gson;
import com.qwe7002.telegram_rc.config.proxy;
import com.qwe7002.telegram_rc.data_structure.request_message;
import com.qwe7002.telegram_rc.static_class.const_value;
import com.qwe7002.telegram_rc.static_class.log_func;
import com.qwe7002.telegram_rc.static_class.network_func;
import com.qwe7002.telegram_rc.static_class.other_func;
import com.qwe7002.telegram_rc.static_class.remote_control_func;
import com.qwe7002.telegram_rc.static_class.sms_func;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class battery_service extends Service {
    private static String bot_token;
    private static String chat_id;
    static String message_thread_id;
    private static boolean doh_switch;
    private Context context;
    private battery_receiver battery_receiver = null;
    private static long last_receive_time = 0;
    private static long last_receive_message_id = -1;
    private static ArrayList<send_obj> send_loop_list;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = other_func.get_notification_obj(context, getString(R.string.battery_monitoring_notify));
        startForeground(com.qwe7002.telegram_rc.notify_id.BATTERY, notification);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        Paper.init(context);
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        chat_id = sharedPreferences.getString("chat_id", "");
        bot_token = sharedPreferences.getString("bot_token", "");
        doh_switch = sharedPreferences.getBoolean("doh_switch", true);
        message_thread_id = sharedPreferences.getString("message_thread_id", "");
        boolean charger_status = sharedPreferences.getBoolean("charger_status", false);
        battery_receiver = new battery_receiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        if (charger_status) {
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        }
        filter.addAction(const_value.BROADCAST_STOP_SERVICE);
        registerReceiver(battery_receiver, filter);
        send_loop_list = new ArrayList<>();
        new Thread(() -> {
            ArrayList<send_obj> need_remove = new ArrayList<>();
            while (true) {
                for (send_obj item : send_loop_list) {
                    network_handle(item);
                    need_remove.add(item);
                }
                send_loop_list.removeAll(need_remove);
                need_remove.clear();
                if (send_loop_list.size() == 0) {
                    //Only enter sleep mode when there are no messages
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private void network_handle(send_obj obj) {
        String TAG = "network_handle";
        final request_message request_body = new request_message();
        request_body.chat_id = battery_service.chat_id;
        request_body.text = obj.content;
        request_body.message_thread_id = message_thread_id;
        String request_uri = network_func.get_url(battery_service.bot_token, "sendMessage");
        if ((System.currentTimeMillis() - last_receive_time) <= 10000L && last_receive_message_id != -1) {
            request_uri = network_func.get_url(bot_token, "editMessageText");
            request_body.message_id = last_receive_message_id;
            Log.d(TAG, "onReceive: edit_mode");
        }
        last_receive_time = System.currentTimeMillis();
        OkHttpClient okhttp_client = network_func.get_okhttp_obj(battery_service.doh_switch, Paper.book("system_config").read("proxy_config", new proxy()));
        String request_body_raw = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(request_body_raw, const_value.JSON);
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        final String error_head = "Send battery info failed:";
        try {
            Response response = call.execute();
            if (response.code() == 200) {
                last_receive_message_id = other_func.get_message_id(Objects.requireNonNull(response.body()).string());
            } else {
                assert response.body() != null;
                last_receive_message_id = -1;
                if (obj.action.equals(Intent.ACTION_BATTERY_LOW)) {
                    sms_func.send_fallback_sms(context, request_body.text, -1);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            log_func.write_log(context, error_head + e.getMessage());
            if (obj.action.equals(Intent.ACTION_BATTERY_LOW)) {
                sms_func.send_fallback_sms(context, request_body.text, -1);
            }
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(battery_receiver);
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class send_obj {
        public String content;
        public String action;
    }

    class battery_receiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, @NotNull final Intent intent) {

            String TAG = "battery_receiver";
            assert intent.getAction() != null;
            Log.d(TAG, "Receive action: " + intent.getAction());
            if (intent.getAction().equals(const_value.BROADCAST_STOP_SERVICE)) {
                Log.i(TAG, "Received stop signal, quitting now...");
                stopSelf();
                android.os.Process.killProcess(android.os.Process.myPid());
                return;
            }
            StringBuilder prebody = new StringBuilder(context.getString(R.string.system_message_head) + "\n");
            final String action = intent.getAction();
            BatteryManager batteryManager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
            switch (Objects.requireNonNull(action)) {
                case Intent.ACTION_BATTERY_OKAY:
                    prebody.append(context.getString(R.string.low_battery_status_end));
                    break;
                case Intent.ACTION_BATTERY_LOW:
                    prebody.append(context.getString(R.string.battery_low));
                    if (remote_control_func.is_tether_active(context)) {
                        remote_control_func.disable_tether(context, TetherManager.TetherMode.TETHERING_WIFI);
                        prebody.append("\n").append(getString(R.string.disable_wifi)).append(context.getString(R.string.action_success));
                    }
                    if (Paper.book("temp").read("wifi_open", false)) {
                        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        assert wifiManager != null;
                        remote_control_func.disable_vpn_ap(wifiManager);
                        prebody.append("\n").append(getString(R.string.disable_wifi)).append(context.getString(R.string.action_success));
                    }
                    break;
                case Intent.ACTION_POWER_CONNECTED:
                    prebody.append(context.getString(R.string.charger_connect));

                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    prebody.append(context.getString(R.string.charger_disconnect));
                    break;
            }
            assert batteryManager != null;
            int battery_level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (battery_level > 100) {
                Log.d(TAG, "The previous battery is over 100%, and the correction is 100%.");
                battery_level = 100;
            }
            String result = prebody.append("\n").append(context.getString(R.string.current_battery_level)).append(battery_level).append("%").toString();
            send_obj obj = new send_obj();
            obj.action = action;
            obj.content = result;
            send_loop_list.add(obj);
        }
    }
}

