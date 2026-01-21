package com.qwe7002.telegram_rc

import android.content.Context
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.UssdResponseCallback
import android.util.Log
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_rc.value.Const
import com.qwe7002.telegram_rc.static_class.Network
import com.qwe7002.telegram_rc.static_class.SMS
import com.qwe7002.telegram_rc.value.TAG
import com.tencent.mmkv.MMKV
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Objects

class USSDCallBack(
    private val context: Context,
    messageId: Long
) : UssdResponseCallback() {
    private val dohSwitch: Boolean
    private var requestUri: String
    private val messageHeader: String
    private val requestBody: RequestMessage

    init {
        val preferences = MMKV.defaultMMKV()
        this.dohSwitch = preferences.getBoolean("doh_switch", true)
        this.requestBody =
            RequestMessage()
        requestBody.chatId = preferences.getString("chat_id", "").toString()
        requestBody.messageThreadId = preferences.getString("message_thread_id", "").toString()
        val botToken = preferences.getString("bot_token", "").toString()
        this.requestUri = Network.getUrl(botToken, "SendMessage")
        if (messageId != -1L) {
            this.requestUri = Network.getUrl(botToken, "editMessageText")
            requestBody.messageId = messageId
        }
        this.messageHeader = context.getString(R.string.send_ussd_head)
    }

    override fun onReceiveUssdResponse(
        telephonyManager: TelephonyManager,
        request: String,
        response: CharSequence
    ) {
        super.onReceiveUssdResponse(telephonyManager, request, response)
        val message = "$messageHeader\n${context.getString(R.string.request)}$request\n${context.getString(R.string.content)}$response"
        networkHandle(message)
    }

    override fun onReceiveUssdResponseFailed(
        telephonyManager: TelephonyManager,
        request: String,
        failureCode: Int
    ) {
        super.onReceiveUssdResponseFailed(telephonyManager, request, failureCode)
        val message = "$messageHeader\n${context.getString(R.string.request)}$request\n${context.getString(R.string.error_message)}${getErrorCodeString(failureCode)}"
        networkHandle(message)
    }

    private fun networkHandle(message: String) {
        requestBody.text = message
        val requestBodyJson = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyJson.toRequestBody(Const.JSON)
        val okhttpClient = Network.getOkhttpObj()
        val requestObj: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(requestObj)
        val errorHead = "Send USSD failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "onFailure: $e")
                Log.e(TAG, errorHead + e.message)
                SMS.sendFallbackSMS(context, requestBody.text, -1)
                ReSendJob.addResendLoop(context,requestBody.text)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (response.code != 200) {
                    Log.e(
                        TAG,
                        errorHead + response.code + " " + Objects.requireNonNull(response.body)
                            .string()
                    )
                    SMS.sendFallbackSMS(context, requestBody.text, -1)
                    ReSendJob.addResendLoop(context,requestBody.text)
                }
            }
        })
    }

    private fun getErrorCodeString(errorCode: Int): String {
        return when (errorCode) {
            -1 -> "Connection problem or invalid MMI code."
            -2 -> "No service."
            else -> "An unknown error occurred ($errorCode)"
        }
    }
}
