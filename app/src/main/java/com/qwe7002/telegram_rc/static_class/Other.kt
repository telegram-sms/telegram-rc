package com.qwe7002.telegram_rc.static_class

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.R
import com.qwe7002.telegram_rc.data_structure.SMSRequestInfo
import com.tencent.mmkv.MMKV
import java.util.Locale

object Other {
    private val NINE_KEY_MAP = mapOf(
        'A' to '2', 'B' to '2', 'C' to '2',
        'D' to '3', 'E' to '3', 'F' to '3',
        'G' to '4', 'H' to '4', 'I' to '4',
        'J' to '5', 'K' to '5', 'L' to '5',
        'M' to '6', 'N' to '6', 'O' to '6',
        'P' to '7', 'Q' to '7', 'R' to '7', 'S' to '7',
        'T' to '8', 'U' to '8', 'V' to '8',
        'W' to '9', 'X' to '9', 'Y' to '9', 'Z' to '9'
    )

    fun getNineKeyMapConvert(input: String): String {
        return input.uppercase(Locale.ROOT).map { c ->
            NINE_KEY_MAP[c] ?: c
        }.joinToString("")
    }

    @JvmStatic
    fun parseStringToLong(intStr: String): Long {
        var result: Long = 0
        if (intStr.isNotEmpty()) {
            try {
                result = intStr.toLong()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
        }
        return result
    }

    @JvmStatic
    fun getSendPhoneNumber(phoneNumber: String): String {
        var convertNumber = phoneNumber
        convertNumber = getNineKeyMapConvert(convertNumber)
        val result = StringBuilder()
        for (element in convertNumber) {
            if (element == '+' || Character.isDigit(element)) {
                result.append(element)
            }
        }
        return result.toString()
    }

    @JvmStatic
    fun getDualSimCardDisplay(context: Context, slot: Int): String {
        var dualSim = ""
        if (slot == -1) {
            return dualSim
        }
        if (getActiveCard(context) >= 2) {
            dualSim = "SIM" + (slot + 1) + " "
        }
        return dualSim
    }

    @JvmStatic
    fun isPhoneNumber(str: String): Boolean {
        var i = str.length
        while (--i >= 0) {
            val c = str[i]
            if (c == '+') {
                continue
            }
            if (!Character.isDigit(c)) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun getDataSimId(context: Context): String {
        var result = "Unknown"
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("get_data_sim_id", "No permission.")
            return result
        }
        val subscriptionManager =
            (context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager)
        val info =
            subscriptionManager.getActiveSubscriptionInfo(SubscriptionManager.getDefaultDataSubscriptionId())
                ?: return result
        result = (info.simSlotIndex + 1).toString()
        return result
    }


    @JvmStatic
    fun getMessageId(result: String?): Long {
        Log.d("getMessageId", "getMessageId: "+ result)
        val resultObj = JsonParser.parseString(result).asJsonObject["result"].asJsonObject
        return resultObj["message_id"].asLong
    }

    @JvmStatic
    fun getNotificationObj(context: Context, notificationName: String): Notification.Builder {
        val manager =
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        val channelExists = manager.getNotificationChannel(notificationName) != null
        if (!channelExists) {
            val channel = NotificationChannel(
                notificationName, notificationName,
                NotificationManager.IMPORTANCE_MIN
            )
            manager.createNotificationChannel(channel)
        }
        val appName = context.getString(R.string.app_name)
        val builder = Notification.Builder(context, notificationName).setAutoCancel(false)
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .setTicker(appName)
            .setContentTitle(appName)
            .setContentText(notificationName + context.getString(R.string.service_is_running))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder
    }

    @JvmStatic
    fun getSubId(context: Context, slot: Int): Int {
        val activeCard = getActiveCard(context)
        if (activeCard >= 2) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.i("getSubId", "get_sub_id: No permission")
                return -1
            }
            val subscriptionManager =
                (context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager)
            return subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot).subscriptionId
        }
        return -1
    }

    @JvmStatic
    fun getActiveCard(context: Context): Int {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return -1
        }
        val subscriptionManager =
            (context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager)
        return subscriptionManager.activeSubscriptionInfoCount
    }

    @JvmStatic
    fun getSimDisplayName(context: Context, slot: Int): String {
        val TAG = "get_sim_display_name"
        var result = "Unknown"
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "No permission.")
            return result
        }
        val subscriptionManager =
            (context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager)
        var info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)
        if (info == null) {
            Log.d(TAG, "The active card is in the second card slot.")
            if (getActiveCard(context) == 1 && slot == 0) {
                info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(1)
            }
            if (info == null) {
                Log.d(TAG, "get_sim_display_name: Unable to obtain information")
                return result
            }
        }

        val displayName = info.displayName?.toString() ?: ""
        result = if (displayName.contains("CARD") || displayName.contains("SUB")) {
            info.carrierName?.toString() ?: displayName
        } else {
            displayName
        }
        return result
    }


    fun addMessageList(messageId: Long, phone: String, slot: Int) {
        val item = SMSRequestInfo()
        item.phone = phone
        item.card = slot
        MMKV.mmkvWithID(Const.CHAT_INFO_MMKV_ID)
            .putString(messageId.toString(), Gson().toJson(item))
        Log.d("add_message_list", "add_message_list: $messageId")
    }
}
