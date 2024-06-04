package com.qwe7002.telegram_rc;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.qwe7002.telegram_rc.data_structure.request_message;
import com.qwe7002.telegram_rc.static_class.CONST;
import com.qwe7002.telegram_rc.static_class.log;
import com.qwe7002.telegram_rc.static_class.network;
import com.qwe7002.telegram_rc.static_class.notify;
import com.qwe7002.telegram_rc.static_class.other;
import com.qwe7002.telegram_rc.static_class.resend;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    static Map<String, String> app_name_list = new HashMap<>();
    static String lastPackage;
    static String lastMessage;
    static long lastSendTime;
    Context context;
    SharedPreferences sharedPreferences;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        Paper.init(context);
        sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        Notification notification = other.getNotificationObj(getApplicationContext(), getString(R.string.Notification_Listener_title));
        startForeground(notify.NOTIFICATION_LISTENER_SERVICE, notification);

    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(@NotNull StatusBarNotification sbn) {
        final String package_name = sbn.getPackageName();
        android.util.Log.d(TAG, "onNotificationPosted: " + package_name);

        if (!sharedPreferences.getBoolean("initialized", false)) {
            android.util.Log.i(TAG, "Uninitialized, Notification receiver is deactivated.");
            return;
        }

        List<String> listen_list = Paper.book("system_config").read("notify_listen_list", new ArrayList<>());
        if (!listen_list.contains(package_name)) {
            android.util.Log.i(TAG, "[" + package_name + "] Not in the list of listening packages.");
            return;
        }
        Bundle extras = sbn.getNotification().extras;
        assert extras != null;
        String app_name = "unknown";
        android.util.Log.d(TAG, "onNotificationPosted: " + app_name_list);
        if (app_name_list.containsKey(package_name)) {
            app_name = app_name_list.get(package_name);
        } else {
            final PackageManager pm = getApplicationContext().getPackageManager();
            try {
                ApplicationInfo application_info = pm.getApplicationInfo(sbn.getPackageName(), 0);
                app_name = (String) pm.getApplicationLabel(application_info);
                app_name_list.put(package_name, app_name);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        String title = extras.getString(Notification.EXTRA_TITLE, "None");
        String content = extras.getString(Notification.EXTRA_TEXT, "None");
        if (Objects.equals(title, "None") && Objects.equals(content, "None")) {
            log.writeLog(context, "无法获得标题和信息");
            return;
        }
        String bot_token = sharedPreferences.getString("bot_token", "");
        String chat_id = sharedPreferences.getString("chat_id", "");
        String request_uri = network.getUrl(bot_token, "sendMessage");
        request_message request_body = new request_message();
        if ((System.currentTimeMillis() - lastSendTime) <= 1000L && Objects.equals(lastPackage, package_name)) {
            if (Objects.equals(lastMessage, title + content)) {
                return;
            }
        }
        request_body.chat_id = chat_id;
        request_body.message_thread_id = sharedPreferences.getString("message_thread_id", "");
        request_body.text = getString(R.string.receive_notification_title) + "\n" + getString(R.string.app_name_title) + app_name + "\n" + getString(R.string.title) + title + "\n" + getString(R.string.content) + content;
        RequestBody body = RequestBody.create(new Gson().toJson(request_body), CONST.JSON);
        OkHttpClient okhttp_client = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true));
        Request request = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        lastPackage = package_name;
        lastMessage = title + content;
        lastSendTime = System.currentTimeMillis();
        final String error_head = "Send notification failed:";
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                log.writeLog(context, error_head + e.getMessage());
                resend.addResendLoop(context, request_body.text);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String result = Objects.requireNonNull(response.body()).string();
                if (response.code() != 200) {
                    log.writeLog(context, error_head + response.code() + " " + result);
                    resend.addResendLoop(context, request_body.text);
                }
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }


}
