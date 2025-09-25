@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_rc.static_class

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.PermissionChecker
import com.google.gson.Gson
import com.qwe7002.telegram_rc.R
import com.qwe7002.telegram_rc.SMSSendResultReceiver
import com.qwe7002.telegram_rc.data_structure.RequestMessage
import com.qwe7002.telegram_rc.static_class.LogManage.writeLog
import com.tencent.mmkv.MMKV
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Objects

object SMS {
    @JvmStatic
    fun sendFallbackSMS(context: Context, content: String?, subId: Int) {
        val TAG = "send_fallback_sms"
        if (PermissionChecker.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            Log.d(TAG, ": No permission.")
            return
        }
        val preferences = MMKV.defaultMMKV()
        val trustNumber = preferences.getString("trusted_phone_number", "")
        if (trustNumber.isNullOrEmpty()) {
            Log.i(TAG, "The trusted number is empty.")
            return
        }
        if (!preferences.getBoolean("fallback_sms", false)) {
            Log.i(TAG, "Did not open the SMS to fall back.")
            return
        }
        if (content.isNullOrEmpty()) {
            Log.i(TAG, "The content is empty.")
            return
        }
        try {
            val smsManager = if (subId == -1) {
                SmsManager.getDefault()
            } else {
                SmsManager.getSmsManagerForSubscriptionId(subId)
            }
            val divideContents = smsManager.divideMessage(content)
            smsManager.sendMultipartTextMessage(trustNumber, null, divideContents, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send fallback SMS: ${e.message}")
        }
    }

    @JvmStatic
    fun sendSMS(context: Context, sendTo: String, content: String, slot: Int, subId: Int) {
        sendSMS(context, sendTo, content, slot, subId, -1)
    }

    @JvmStatic
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun sendSMS(
        context: Context,
        sendTo: String,
        content: String,
        slot: Int,
        subId: Int,
        messageId: Long
    ) {
        var privateMessageId = messageId
        if (PermissionChecker.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            Log.d("send_sms", "No permission.")
            return
        }
        if (!Other.isPhoneNumber(sendTo)) {
            writeLog(context, "[$sendTo] is an illegal phone number")
            return
        }
        val preferences = MMKV.defaultMMKV()
        val botToken = preferences.getString("bot_token", "") ?: ""
        val chatId = preferences.getString("chat_id", "") ?: ""
        var requestUri = Network.getUrl(botToken, "sendMessage")
        if (privateMessageId != -1L) {
            Log.d("send_sms", "Find the message_id and switch to edit mode.")
            requestUri = Network.getUrl(botToken, "editMessageText")
        }
        val requestBody = RequestMessage()
        requestBody.chatId = chatId
        val smsManager = if (subId == -1) {
            SmsManager.getDefault()
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
        val dualSim = Other.getDualSimCardDisplay(
            context,
            slot,
            preferences.getBoolean("display_dual_sim_display_name", false)
        )
        val sendContent = "[$dualSim${context.getString(R.string.send_sms_head)}]\n${context.getString(R.string.to)}$sendTo\n${context.getString(R.string.content)}$content"
        requestBody.text = "$sendContent\n${context.getString(R.string.status)}${context.getString(R.string.sending)}"
        requestBody.messageId = privateMessageId
        val gson = Gson()
        val requestBodyRaw = gson.toJson(requestBody)
        val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
        val okhttpClient = Network.getOkhttpObj()
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        try {
            val response = call.execute()
            if (response.code != 200) {
                writeLog(context, "Failed to send message: HTTP ${response.code}")
                response.close()
                return
            }
            if (privateMessageId == -1L) {
                privateMessageId = Other.getMessageId(Objects.requireNonNull(response.body).string())
            }
            response.close()
        } catch (e: Exception) {
            Log.d("sendSMS", "sendSMS: $e")
            writeLog(context, "failed to send message:" + e.message)
            return
        }
        val divideContents = smsManager.divideMessage(content)
        val sendReceiverList = ArrayList<PendingIntent>()
        val filter = IntentFilter("send_sms")
        val receiver: BroadcastReceiver = SMSSendResultReceiver()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.applicationContext.registerReceiver(receiver, filter)
        }
        val intent = Intent("send_sms")
        intent.putExtra("message_id", privateMessageId)
        intent.putExtra("message_text", sendContent)
        intent.putExtra("sub_id", smsManager.subscriptionId)
        val sentIntent = PendingIntent.getBroadcast(
            context,
            privateMessageId.toInt(),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        sendReceiverList.add(sentIntent)
        try {
            smsManager.sendMultipartTextMessage(
                sendTo,
                null,
                divideContents,
                sendReceiverList,
                null
            )
        } catch (e: Exception) {
            Log.e("sendSMS", "Failed to send SMS: ${e.message}")
            writeLog(context, "Failed to send SMS: ${e.message}")
            context.applicationContext.unregisterReceiver(receiver)
        }
    }
}
