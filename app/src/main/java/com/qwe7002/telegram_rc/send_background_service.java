package com.qwe7002.telegram_rc;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class send_background_service extends Service {
    Context context;
    String request_uri;
    String send_list = "resend_list";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        context = getApplicationContext();
        Notification notification = public_func.get_notification_obj(context, getString(R.string.failed_resend));
        if (intent.getBooleanExtra("send_spam_mode", false)) {
            send_list = "spam_sms_list";
        }

        startForeground(5, notification);

        Paper.init(context);
        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        request_uri = public_func.get_url(sharedPreferences.getString("bot_token", ""), "SendMessage");

        final String final_send_list = send_list;
        new Thread(() -> {
            ArrayList<String> resend_list = Paper.book().read(final_send_list, new ArrayList<>());
            while (resend_list.size() != 0) {
                if (public_func.check_network_status(context)) {
                    resend_list = Paper.book().read(final_send_list, new ArrayList<>());
                    OkHttpClient okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true));
                    for (String item : resend_list) {
                        network_progress_handle(item, sharedPreferences.getString("chat_id", ""), okhttp_client);
                    }
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            public_func.write_log(context, "The resend failure message is complete.");
            stopSelf();
        }).start();

        return START_NOT_STICKY;
    }

    private void network_progress_handle(String message, String chat_id, OkHttpClient okhttp_client) {
        message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        request_body.text = message;
        if (message.contains("<code>") && message.contains("</code>")) {
            request_body.parse_mode = "html";
        }
        String request_body_json = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(request_body_json, public_func.JSON);
        Request request_obj = new Request.Builder().url(request_uri).method("POST", body).build();
        Call call = okhttp_client.newCall(request_obj);
        try {
            Response response = call.execute();
            if (response.code() == 200) {
                ArrayList<String> resend_list_local = Paper.book().read(send_list, new ArrayList<>());
                resend_list_local.remove(message);
                Paper.book().write(send_list, resend_list_local);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
