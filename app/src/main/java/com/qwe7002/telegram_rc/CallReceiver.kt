@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_rc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.LogManage
import com.qwe7002.telegram_rc.static_class.Network
import com.qwe7002.telegram_rc.static_class.Other
import com.qwe7002.telegram_rc.static_class.Resend
import com.qwe7002.telegram_rc.static_class.SMS
import com.tencent.mmkv.MMKV
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Objects

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("call_receiver", "Receive action: " + intent.action)
        stateMMKV = MMKV.mmkvWithID(Const.STATUS_MMKV_ID)
        when (Objects.requireNonNull(intent.action)) {
            "android.intent.action.PHONE_STATE" -> {
                val incomingNumber = intent.getStringExtra("incoming_number")
                if (incomingNumber != null) {
                    stateMMKV.putString("incoming_number", incomingNumber)
                }
                val telephony = context
                    .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val customPhoneListener = CallStatusListener(context)
                telephony.listen(customPhoneListener, PhoneStateListener.LISTEN_CALL_STATE)
            }

            "android.intent.action.SUBSCRIPTION_PHONE_STATE" -> {
                val slot = intent.getIntExtra("slot", -1)
                if (slot != -1) {
                    stateMMKV.putInt("incoming_slot", slot)
                }
            }
        }
    }

    internal class CallStatusListener(private val context: Context) : PhoneStateListener() {

        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(nowState: Int, nowIncomingNumber: String) {
            if (stateMMKV.getInt("incoming_last_status", TelephonyManager.CALL_STATE_IDLE) == TelephonyManager.CALL_STATE_RINGING
                && nowState == TelephonyManager.CALL_STATE_IDLE
            ) {
                val incomingNumber = stateMMKV.getString("incoming_number", "")
                // 检查电话号码是否为空
                if (incomingNumber.isNullOrEmpty()) {
                    Log.i("call_status_listener", "Incoming number is empty")
                    return
                }
                
                val preferences = MMKV.defaultMMKV()
                if (!preferences.contains("initialized")) {
                    Log.i("call_status_listener", "Uninitialized, Phone receiver is deactivated.")
                    return
                }
                val botToken = preferences.getString("bot_token", "") ?: ""
                val chatId = preferences.getString("chat_id", "") ?: ""
                val requestUri = Network.getUrl(botToken, "sendMessage")
                val requestBody = RequestMessage()
                requestBody.chatId = chatId
                requestBody.messageThreadId = preferences.getString("message_thread_id", "") ?: ""
                val dualSim = Other.getDualSimCardDisplay(
                    context,
                    stateMMKV.getInt("incoming_slot", -1),
                    preferences.getBoolean("display_dual_sim_display_name", false)
                )
                requestBody.text = "[$dualSim${context.getString(R.string.missed_call_head)}]\n${context.getString(R.string.Incoming_number)}$incomingNumber"
                CcSendJob.startJob(context, context.getString(R.string.missed_call_head), requestBody.text)
                val gson = Gson()
                val requestBodyRaw = gson.toJson(requestBody)
                val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
                val okhttpClient = Network.getOkhttpObj()
                val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
                val call = okhttpClient.newCall(request)
                val errorHead = "Send missed call error:"
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                        LogManage.writeLog(context, "$errorHead${e.message}")
                        SMS.sendFallbackSMS(
                            context,
                            requestBody.text,
                            Other.getSubId(context, stateMMKV.getInt("incoming_slot", -1))
                        )
                        Resend.addResendLoop(context, requestBody.text)
                    }

                    @Throws(IOException::class)
                    override fun onResponse(call: Call, response: Response) {
                        if (response.code != 200) {
                            LogManage.writeLog(
                                context,
                                "$errorHead${response.code} ${Objects.requireNonNull(response.body).string()}"
                            )
                            Resend.addResendLoop(context, requestBody.text)
                        } else {
                            val result = Objects.requireNonNull(response.body).string()
                            if (!Other.isPhoneNumber(incomingNumber)) {
                                LogManage.writeLog(
                                    context,
                                    "[$incomingNumber] Not a regular phone number."
                                )
                                return
                            }
                            Other.addMessageList(Other.getMessageId(result), incomingNumber, stateMMKV.getInt("incoming_slot", -1))
                        }
                    }
                })
            }
            stateMMKV.putInt("incoming_last_status", nowState)
        }

    }
    companion object{
        private lateinit var stateMMKV: MMKV
    }
}

