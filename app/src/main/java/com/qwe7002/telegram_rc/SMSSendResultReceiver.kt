package com.qwe7002.telegram_rc

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.requestMessage
import com.qwe7002.telegram_rc.static_class.Const
import com.qwe7002.telegram_rc.static_class.LogManage.writeLog
import com.qwe7002.telegram_rc.static_class.Resend.addResendLoop
import com.qwe7002.telegram_rc.static_class.Network
import com.qwe7002.telegram_rc.static_class.SMS
import io.paperdb.Paper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Objects

class SMSSendResultReceiver : BroadcastReceiver() {
    private lateinit var preferences: io.paperdb.Book
    override fun onReceive(context: Context, intent: Intent) {
        Paper.init(context)
        val logTag = "sms_send_receiver"
        Log.d(logTag, "Receive action: " + intent.action)
        val extras = intent.extras!!
        val sub = extras.getInt("sub_id")
        context.unregisterReceiver(this)
        preferences = Paper.book("preferences")
        if (!preferences.contains("initialized")) {
            Log.i(logTag, "Uninitialized, SMS receiver is deactivated.")
            return
        }
        val botToken = preferences.read("bot_token", "").toString()
        val chatId = preferences.read("chat_id", "").toString()
        val requestBody = requestMessage()
        requestBody.chatId = chatId
        requestBody.messageThreadId =preferences.read("message_thread_id", "")
        var requestUri = Network.getUrl(botToken, "sendMessage")
        val messageId = extras.getLong("message_id")
        if (messageId != -1L) {
            Log.d(logTag, "Find the message_id and switch to edit mode.")
            requestUri = Network.getUrl(botToken, "editMessageText")
            requestBody.messageId = messageId
        }
        var resultStatus = "Unknown"
        when (resultCode) {
            Activity.RESULT_OK -> resultStatus = context.getString(R.string.success)
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> resultStatus =
                context.getString(R.string.send_failed)

            SmsManager.RESULT_ERROR_RADIO_OFF -> resultStatus =
                context.getString(R.string.airplane_mode)

            SmsManager.RESULT_ERROR_NO_SERVICE -> resultStatus =
                context.getString(R.string.no_network)
        }
        requestBody.text = """
            ${extras.getString("message_text")}
            ${context.getString(R.string.status)}$resultStatus
            """.trimIndent()
        val requestBodyRaw = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
        val okhttpClient = Network.getOkhttpObj(preferences.read("doh_switch", true)!!)
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        val errorHead = "Send SMS status failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                writeLog(context, errorHead + e.message)
                SMS.sendFallbackSMS(context, requestBody.text, sub)
                addResendLoop(requestBody.text)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (response.code != 200) {
                    writeLog(
                        context,
                        errorHead + response.code + " " + Objects.requireNonNull(response.body)
                            .string()
                    )
                    addResendLoop(requestBody.text)
                }
            }
        })
    }
}
