package com.qwe7002.telegram_rc.static_class;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;
import com.qwe7002.telegram_rc.R;
import com.qwe7002.telegram_rc.data_structure.request_message;
import com.qwe7002.telegram_rc.ussd_request_callback;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ussd {
    public static void send_ussd(Context context, String ussdRaw, int subId) {
        final String TAG = "send_ussd";
        final String ussd = other.getNineKeyMapConvert(ussdRaw);

        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        assert tm != null;

        if (subId != -1) {
            tm = tm.createForSubscriptionId(subId);
        }

        SharedPreferences sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "send_ussd: No permission.");
        }

        String botToken = sharedPreferences.getString("bot_token", "");
        String chatId = sharedPreferences.getString("chat_id", "");
        String requestUri = network.getUrl(botToken, "sendMessage");
        request_message requestBody = new request_message();
        requestBody.chat_id = chatId;
        requestBody.text = context.getString(R.string.send_ussd_head) + "\n" + context.getString(R.string.ussd_code_running);
        String request_body_raw = new Gson().toJson(requestBody);
        RequestBody body = RequestBody.create(request_body_raw, CONST.JSON);
        OkHttpClient okhttp_client = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true));
        Request request = new Request.Builder().url(requestUri).method("POST", body).build();
        Call call = okhttp_client.newCall(request);
        TelephonyManager telephonyManager = tm;
        new Thread(() -> {
            long message_id = -1L;
            try {
                Response response = call.execute();
                message_id = other.getMessageId(Objects.requireNonNull(response.body()).string());
            } catch (IOException e) {
                Log.d(TAG, "send_ussd: "+e);
            }
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                Looper.prepare();
                telephonyManager.sendUssdRequest(ussd, new ussd_request_callback(context, sharedPreferences, message_id), new Handler());
                Looper.loop();
            }
        }).start();
    }
}
