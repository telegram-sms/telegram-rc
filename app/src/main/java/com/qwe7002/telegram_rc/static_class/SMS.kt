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
import com.qwe7002.telegram_rc.data_structure.requestMessage
import com.qwe7002.telegram_rc.static_class.LogManage.writeLog
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Objects

object SMS {
    @JvmStatic
    fun sendFallbackSMS(context: Context, content: String?, sub_id: Int) {
        val TAG = "send_fallback_sms"
        if (PermissionChecker.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            Log.d(TAG, ": No permission.")
            return
        }
        val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        val trustNumber = sharedPreferences.getString("trusted_phone_number", null)
        if (trustNumber == null) {
            Log.i(TAG, "The trusted number is empty.")
            return
        }
        if (!sharedPreferences.getBoolean("fallback_sms", false)) {
            Log.i(TAG, "Did not open the SMS to fall back.")
            return
        }
        val smsManager = if (sub_id == -1) {
            SmsManager.getDefault()
        } else {
            SmsManager.getSmsManagerForSubscriptionId(sub_id)
        }
        val divideContents = smsManager.divideMessage(content)
        smsManager.sendMultipartTextMessage(trustNumber, null, divideContents, null, null)
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
        if (!other.isPhoneNumber(sendTo)) {
            writeLog(context, "[$sendTo] is an illegal phone number")
            return
        }
        val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        val botToken = sharedPreferences.getString("bot_token", "").toString()
        val chatId = sharedPreferences.getString("chat_id", "").toString()
        var requestUri = network.getUrl(botToken, "sendMessage")
        if (privateMessageId != -1L) {
            Log.d("send_sms", "Find the message_id and switch to edit mode.")
            requestUri = network.getUrl(botToken, "editMessageText")
        }
        val requestBody = requestMessage()
        requestBody.chatId = chatId
        val smsManager = if (subId == -1) {
            SmsManager.getDefault()
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
        val dualSim = other.getDualSimCardDisplay(
            context,
            slot,
            sharedPreferences.getBoolean("display_dual_sim_display_name", false)
        )
        val sendContent = """
            [$dualSim${context.getString(R.string.send_sms_head)}]
            ${context.getString(R.string.to)}$sendTo
            ${context.getString(R.string.content)}$content
            """.trimIndent()
        requestBody.text = """
            $sendContent
            ${context.getString(R.string.status)}${context.getString(R.string.sending)}
            """.trimIndent()
        requestBody.messageId = privateMessageId
        val gson = Gson()
        val requestBodyRaw = gson.toJson(requestBody)
        val body: RequestBody = requestBodyRaw.toRequestBody(CONST.JSON)
        val okhttpClient = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true))
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        try {
            val response = call.execute()
            if (response.code != 200) {
                throw IOException(response.code.toString())
            }
            if (privateMessageId == -1L) {
                privateMessageId = other.getMessageId(Objects.requireNonNull(response.body).string())
            }
        } catch (e: IOException) {
            Log.d("sendSMS", "sendSMS: $e")
            writeLog(context, "failed to send message:" + e.message)
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
            0,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        sendReceiverList.add(sentIntent)
        smsManager.sendMultipartTextMessage(
            sendTo,
            null,
            divideContents,
            sendReceiverList,
            null
        )
    }
}