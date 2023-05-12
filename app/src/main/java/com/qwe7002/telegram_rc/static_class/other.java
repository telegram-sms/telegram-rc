package com.qwe7002.telegram_rc.static_class;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qwe7002.telegram_rc.R;
import com.qwe7002.telegram_rc.data_structure.sms_request_info;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import io.paperdb.Paper;


public class other {

    public static String get_nine_key_map_convert(String input) {
        final Map<Character, Integer> nine_key_map = new HashMap<Character, Integer>() {
            {
                put('A', 2);
                put('B', 2);
                put('C', 2);
                put('D', 3);
                put('E', 3);
                put('F', 3);
                put('G', 4);
                put('H', 4);
                put('I', 4);
                put('J', 5);
                put('K', 5);
                put('L', 5);
                put('M', 6);
                put('N', 6);
                put('O', 6);
                put('P', 7);
                put('Q', 7);
                put('R', 7);
                put('S', 7);
                put('T', 8);
                put('U', 8);
                put('V', 8);
                put('W', 9);
                put('X', 9);
                put('Y', 9);
                put('Z', 9);
            }
        };
        StringBuilder result_stringbuilder = new StringBuilder();
        char[] ussd_char_array = input.toUpperCase().toCharArray();
        for (char c : ussd_char_array) {
            if (Character.isUpperCase(c)) {
                result_stringbuilder.append(nine_key_map.get(c));
            } else {
                result_stringbuilder.append(c);
            }
        }
        return result_stringbuilder.toString();
    }

    public static long parse_string_to_long(@NotNull String int_str) {
        long result = 0;
        if (!int_str.isEmpty()) {
            try {
                result = Long.parseLong(int_str);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    @NotNull
    public static String get_send_phone_number(@NotNull String phone_number) {
        phone_number = get_nine_key_map_convert(phone_number);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < phone_number.length(); ++i) {
            char c = phone_number.charAt(i);
            if (c == '+' || Character.isDigit(c)) {
                result.append(c);
            }
        }
        return result.toString();
    }

    public static String get_dual_sim_card_display(Context context, int slot, boolean show_name) {
        String dual_sim = "";
        if (slot == -1) {
            return dual_sim;
        }
        if (other.get_active_card(context) >= 2) {
            String result = "";
            if (show_name) {
                result = "(" + get_sim_display_name(context, slot) + ")";
            }
            dual_sim = "SIM" + (slot + 1) + result + " ";
        }
        return dual_sim;
    }

    public static boolean is_phone_number(@NotNull String str) {
        for (int i = str.length(); --i >= 0; ) {
            char c = str.charAt(i);
            if (c == '+') {
                continue;
            }
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    @TargetApi(Build.VERSION_CODES.N)
    public static String get_data_sim_id(Context context) {
        String result = "Unknown";
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.d("get_data_sim_id", "No permission.");
            return result;
        }
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        assert subscriptionManager != null;
        SubscriptionInfo info = subscriptionManager.getActiveSubscriptionInfo(SubscriptionManager.getDefaultDataSubscriptionId());
        if (info == null) {
            return result;
        }
        result = String.valueOf(info.getSimSlotIndex() + 1);
        return result;
    }


    @NotNull
    public static Long get_message_id(String result) {
        JsonObject result_obj = JsonParser.parseString(result).getAsJsonObject().get("result").getAsJsonObject();
        return result_obj.get("message_id").getAsLong();
    }

    @NotNull
    public static Notification get_notification_obj(@NotNull Context context, String notification_name) {
        NotificationChannel channel = new NotificationChannel(notification_name, notification_name,
                NotificationManager.IMPORTANCE_MIN);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(channel);
        Notification.Builder notification = new Notification.Builder(context, notification_name).setAutoCancel(false)
                .setSmallIcon(R.drawable.ic_stat)
                .setOngoing(true)
                .setTicker(context.getString(R.string.app_name))
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(notification_name + context.getString(R.string.service_is_running));
        return notification.build();
    }

    public static int get_sub_id(Context context, int slot) {
        int active_card = other.get_active_card(context);
        if (active_card >= 2) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                Log.i("get_sub_id", "get_sub_id: No permission");
                return -1;
            }
            SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            assert subscriptionManager != null;
            return subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot).getSubscriptionId();
        }
        return -1;
    }

    public static int get_active_card(Context context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return -1;
        }
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        assert subscriptionManager != null;
        return subscriptionManager.getActiveSubscriptionInfoCount();
    }

    public static String get_sim_display_name(Context context, int slot) {
        final String TAG = "get_sim_display_name";
        String result = "Unknown";
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "No permission.");
            return result;
        }
        SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        assert subscriptionManager != null;
        SubscriptionInfo info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot);
        if (info == null) {
            Log.d(TAG, "The active card is in the second card slot.");
            if (get_active_card(context) == 1 && slot == 0) {
                info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(1);
            }
            if (info == null) {
                Log.d(TAG, "get_sim_display_name: Unable to obtain information");
                return result;
            }
            return result;
        }
        result = info.getDisplayName().toString();
        if (info.getDisplayName().toString().contains("CARD") || info.getDisplayName().toString().contains("SUB")) {
            result = info.getCarrierName().toString();
        }
        return result;
    }


    public static void add_message_list(long message_id, String phone, int slot) {
        sms_request_info item = new sms_request_info();
        item.phone = phone;
        item.card = slot;
        Paper.book().write(String.valueOf(message_id), item);
        Log.d("add_message_list", "add_message_list: " + message_id);
    }

}
