package com.qwe7002.telegram_rc

import android.content.Context
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.UssdResponseCallback
import android.util.Log
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.request_message
import com.qwe7002.telegram_rc.static_class.CONST
import com.qwe7002.telegram_rc.static_class.Resend.addResendLoop
import com.qwe7002.telegram_rc.static_class.log
import com.qwe7002.telegram_rc.static_class.network
import com.qwe7002.telegram_rc.static_class.sms
import io.paperdb.Paper
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
    sharedPreferences: SharedPreferences,
    messageId: Long
) : UssdResponseCallback() {
    private val dohSwitch: Boolean
    private var requestUri: String
    private val messageHeader: String
    private val requestBody: request_message

    init {
        Paper.init(context)
        val chatId = sharedPreferences.getString("chat_id", "")
        this.dohSwitch = sharedPreferences.getBoolean("doh_switch", true)
        this.requestBody = request_message()
        requestBody.chat_id = chatId
        requestBody.message_thread_id = sharedPreferences.getString("message_thread_id", "")
        val botToken = sharedPreferences.getString("bot_token", "")
        this.requestUri = network.getUrl(botToken, "SendMessage")
        if (messageId != -1L) {
            this.requestUri = network.getUrl(botToken, "editMessageText")
            requestBody.message_id = messageId
        }
        this.messageHeader = context.getString(R.string.send_ussd_head)
    }

    override fun onReceiveUssdResponse(
        telephonyManager: TelephonyManager,
        request: String,
        response: CharSequence
    ) {
        super.onReceiveUssdResponse(telephonyManager, request, response)
        val message = """
            $messageHeader
            ${context.getString(R.string.request)}$request
            ${context.getString(R.string.content)}$response
            """.trimIndent()
        networkHandle(message)
    }

    override fun onReceiveUssdResponseFailed(
        telephonyManager: TelephonyManager,
        request: String,
        failureCode: Int
    ) {
        super.onReceiveUssdResponseFailed(telephonyManager, request, failureCode)
        val message = """
            $messageHeader
            ${context.getString(R.string.request)}$request
            ${context.getString(R.string.error_message)}${getErrorCodeString(failureCode)}
            """.trimIndent()
        networkHandle(message)
    }

    private fun networkHandle(message: String) {
        requestBody.text = message
        val requestBodyJson = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyJson.toRequestBody(CONST.JSON)
        val okhttpClient = network.getOkhttpObj(dohSwitch)
        val requestObj: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(requestObj)
        val errorHead = "Send USSD failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("ussdRequest", "onFailure: $e")
                log.writeLog(context, errorHead + e.message)
                sms.sendFallbackSMS(context, requestBody.text, -1)
                addResendLoop(requestBody.text)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (response.code != 200) {
                    log.writeLog(
                        context,
                        errorHead + response.code + " " + Objects.requireNonNull(response.body)
                            .string()
                    )
                    sms.sendFallbackSMS(context, requestBody.text, -1)
                    addResendLoop(requestBody.text)
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
