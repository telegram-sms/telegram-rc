package com.qwe7002.telegram_rc;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class notification_listener_service extends NotificationListenerService {
    final String TAG = "notification_receiver";
    stop_receiver receiver;

    @Override
    public void onCreate() {
        super.onCreate();
        receiver = new stop_receiver();
        registerReceiver(receiver, new IntentFilter(public_func.broadcast_stop_service));

    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);
        super.onDestroy();
    }
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String package_name = sbn.getPackageName();
        Log.d(TAG, "onNotificationPosted: " + package_name);
        Context context = getApplicationContext();
        final SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, SMS receiver is deactivated.");
            return;
        }
        Paper.init(context);
        List<String> listen_list = Paper.book().read("notify_listen_list", new ArrayList<>());
        if (!listen_list.contains(sbn.getPackageName())) {
            Log.i(TAG, "[" + package_name + "] Not in the list of listening packages.");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = public_func.get_url(bot_token, "sendMessage");
        Bundle extras = sbn.getNotification().extras;
        assert extras != null;
        String title = extras.getString(Notification.EXTRA_TITLE, "None");
        String app_name = "unknown";
        final PackageManager pm = getApplicationContext().getPackageManager();
        try {
            ApplicationInfo application_info = pm.getApplicationInfo(sbn.getPackageName(), 0);
            app_name = (String) pm.getApplicationLabel(application_info);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        String content = extras.getString(Notification.EXTRA_TEXT, "None");
        message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        request_body.text = "[Receive Notification]" + "\n" + "App: " + app_name + "\n" + "Title: " + title + "\n" + getString(R.string.content) + content;
        RequestBody body = RequestBody.create(new Gson().toJson(request_body), public_func.JSON);
        OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        final String error_head = "Send notification failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                public_func.write_log(context, error_head + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                assert response.body() != null;
                String result = Objects.requireNonNull(response.body()).string();
                if (response.code() != 200) {
                    public_func.write_log(context, error_head + response.code() + " " + result);
                }
            }
        });
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "onNotificationRemoved: " + sbn.getPackageName());
    }

    class stop_receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("notification_listener", "Received stop signal, quitting now...");
            stopSelf();
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
