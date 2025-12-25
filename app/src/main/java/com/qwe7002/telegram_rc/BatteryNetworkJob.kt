package com.qwe7002.telegram_rc

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.util.Log
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.Network
import com.qwe7002.telegram_rc.static_class.Other
import com.qwe7002.telegram_rc.static_class.SMS
import com.tencent.mmkv.MMKV
import com.tencent.mmkv.MMKVLogLevel
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException


class BatteryNetworkJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        if (params == null) {
            Log.e(Const.TAG, "onStartJob: params is null")
            return false
        }
        
        val extras = params.extras
        val message: String = extras.getString("message", "") ?: ""
        val action: String? = extras.getString("action", null)
        
        MMKV.initialize(applicationContext)
        MMKV.setLogLevel(MMKVLogLevel.LevelWarning)
        val preferences = MMKV.defaultMMKV()
        val chatId = preferences.getString("chat_id", "") ?: ""
        val botToken = preferences.getString("bot_token", "") ?: ""
        val messageThreadId = preferences.getString("message_thread_id", "") ?: ""
        
        val requestBody = RequestMessage().apply {
            this.chatId = chatId
            this.text = message
            this.messageThreadId = messageThreadId
        }
        
        val chatInfoMMKV = MMKV.mmkvWithID(Const.CHAT_INFO_MMKV_ID)
        var requestUri = Network.getUrl(botToken, "sendMessage")
        
        if ((System.currentTimeMillis() - chatInfoMMKV.getLong(
                "batteryLastReceiveTime",
                0L
            )) <= 10000L && chatInfoMMKV.getLong(
                "batteryLastReceiveMessageId",
                -1L
            ) != -1L
        ) {
            requestUri = Network.getUrl(botToken, "editMessageText")
            requestBody.messageId = chatInfoMMKV.getLong("batteryLastReceiveMessageId", 0L)
            Log.d(Const.TAG, "onReceive: edit_mode")
        }
        
        chatInfoMMKV.putLong("batteryLastReceiveTime", System.currentTimeMillis())
        val okhttpClient = Network.getOkhttpObj()
        val requestBodyRaw = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        val errorHead = "Send battery info failed:"
        
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                e.printStackTrace()
                Log.e(Const.TAG, errorHead + e.message)
                if (action == Intent.ACTION_BATTERY_LOW) {
                    SMS.sendFallbackSMS(applicationContext, requestBody.text, -1)
                }
                jobFinished(params, false)
            }

            @Throws(IOException::class)
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBody = response.body

                val result = responseBody.string()
                if (response.code != 200) {
                    Log.e(Const.TAG, errorHead + response.code + " " + result)
                    if (action == Intent.ACTION_BATTERY_LOW) {
                        SMS.sendFallbackSMS(applicationContext, requestBody.text, -1)
                    }
                } else {
                    chatInfoMMKV.putLong(
                        "batteryLastReceiveMessageId",
                        Other.getMessageId(result)
                    )
                }
                jobFinished(params, false)
            }
        })
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }

    companion object {
        fun startJob(context: Context, message: String, action: String?) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            val jobInfoBuilder = JobInfo.Builder(
                11,
                ComponentName(context.packageName, BatteryNetworkJob::class.java.name)
            )
                .setPersisted(true)
            val extras = PersistableBundle()
            extras.putString("message", message)
            extras.putString("action", action)
            jobInfoBuilder.setExtras(extras)
            jobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            jobScheduler.schedule(jobInfoBuilder.build())

        }
    }
}
