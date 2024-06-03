@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_rc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.request_message
import com.qwe7002.telegram_rc.static_class.CONST
import com.qwe7002.telegram_rc.static_class.log
import com.qwe7002.telegram_rc.static_class.network
import com.qwe7002.telegram_rc.static_class.other
import com.qwe7002.telegram_rc.static_class.resend
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

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Paper.init(context)
        Log.d("call_receiver", "Receive action: " + intent.action)
        when (Objects.requireNonNull(intent.action)) {
            "android.intent.action.PHONE_STATE" -> {
                if (intent.getStringExtra("incoming_number") != null) {
                    Paper.book("temp").write(
                        "incoming_number",
                        intent.getStringExtra("incoming_number")!!
                    )
                }
                val telephony = context
                    .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val customPhoneListener = callStatusListerner(context)
                telephony.listen(customPhoneListener, PhoneStateListener.LISTEN_CALL_STATE)
            }

            "android.intent.action.SUBSCRIPTION_PHONE_STATE" -> {
                val slot = intent.getIntExtra("slot", -1)
                if (slot != -1) {
                    Paper.book("temp").write("incoming_slot", slot)
                }
            }
        }
    }

    internal class callStatusListerner(private val context: Context) : PhoneStateListener() {
        private val slot = Paper.book("temp").read<Int>("incoming_slot")!!

        init {
            incomingNumber = Paper.book("temp").read("incoming_number")
        }

        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(nowState: Int, nowIncomingNumber: String) {
            if (lastStatus == TelephonyManager.CALL_STATE_RINGING
                && nowState == TelephonyManager.CALL_STATE_IDLE
            ) {
                val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
                if (!sharedPreferences.getBoolean("initialized", false)) {
                    Log.i("call_status_listener", "Uninitialized, Phone receiver is deactivated.")
                    return
                }
                val bot_token = sharedPreferences.getString("bot_token", "")
                val chat_id = sharedPreferences.getString("chat_id", "")
                val request_uri = network.getUrl(bot_token, "sendMessage")
                val request_body = request_message()
                request_body.chat_id = chat_id
                request_body.message_thread_id =
                    sharedPreferences.getString("message_thread_id", "")
                val dual_sim = other.getDualSimCardDisplay(
                    context,
                    slot,
                    sharedPreferences.getBoolean("display_dual_sim_display_name", false)
                )
                request_body.text = """
                    [$dual_sim${context.getString(R.string.missed_call_head)}]
                    ${context.getString(R.string.Incoming_number)}$incomingNumber
                    """.trimIndent()

                val request_body_raw = Gson().toJson(request_body)
                val body: RequestBody = request_body_raw.toRequestBody(CONST.JSON)
                val okhttp_client =
                    network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true))
                val request: Request = Request.Builder().url(request_uri).method("POST", body).build()
                val call = okhttp_client.newCall(request)
                val error_head = "Send missed call error:"
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                        log.writeLog(context, error_head + e.message)
                        sms.sendFallbackSMS(
                            context,
                            request_body.text,
                            other.getSubId(context, slot)
                        )
                        resend.addResendLoop(context, request_body.text)
                    }

                    @Throws(IOException::class)
                    override fun onResponse(call: Call, response: Response) {
                        if (response.code != 200) {
                            log.writeLog(
                                context,
                                error_head + response.code + " " + Objects.requireNonNull(response.body)
                                    .string()
                            )
                            resend.addResendLoop(context, request_body.text)
                        } else {
                            val result = Objects.requireNonNull(response.body).string()
                            if (!other.isPhoneNumber(incomingNumber!!)) {
                                log.writeLog(
                                    context,
                                    "[$incomingNumber] Not a regular phone number."
                                )
                                return
                            }
                            other.addMessageList(other.getMessageId(result), incomingNumber, slot)
                        }
                    }
                })
            }

            lastStatus = nowState
        }

        companion object {
            private var lastStatus = TelephonyManager.CALL_STATE_IDLE
            private var incomingNumber: String? = null
        }
    }
}

