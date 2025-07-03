package com.qwe7002.telegram_rc.static_class

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.qwe7002.telegram_rc.R
import com.qwe7002.telegram_rc.USSDCallBack
import com.qwe7002.telegram_rc.data_structure.RequestMessage
import com.tencent.mmkv.MMKV
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Objects

object USSD {
    fun sendUssd(context: Context, ussdRaw: String, subId: Int) {
        val TAG = "send_ussd"
        val ussd = Other.getNineKeyMapConvert(ussdRaw)

        var tm: TelephonyManager? =
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
        if (subId != -1) {
            tm = tm!!.createForSubscriptionId(subId)
        }
        val preferences = MMKV.defaultMMKV()
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "send_ussd: No permission.")
        }

        val botToken = preferences.getString("bot_token", "").toString()
        val chatId = preferences.getString("chat_id", "").toString()
        val requestUri = Network.getUrl(botToken, "sendMessage")
        val requestBody = RequestMessage()
        requestBody.chatId = chatId
        requestBody.text = "${context.getString(R.string.send_ussd_head)}\n${context.getString(R.string.ussd_code_running)}"
        val requestBodyRaw = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
        val okhttpClient = Network.getOkhttpObj()
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        val telephonyManager = tm
        Thread {
            var messageId = -1L
            try {
                val response = call.execute()
                messageId = Other.getMessageId(Objects.requireNonNull(response.body).string())
            } catch (e: IOException) {
                e.printStackTrace()
                Log.d(TAG, "send_ussd: $e")
            }
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Handler(Looper.getMainLooper()).post {
                    telephonyManager!!.sendUssdRequest(
                        ussd,
                        USSDCallBack(context, messageId),
                        Handler(Looper.getMainLooper())
                    )
                }
            }
        }.start()
    }
}
