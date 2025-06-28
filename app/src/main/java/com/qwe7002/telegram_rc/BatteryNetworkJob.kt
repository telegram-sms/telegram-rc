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
import com.qwe7002.telegram_rc.data_structure.RequestMessage
import com.qwe7002.telegram_rc.static_class.Const
import com.qwe7002.telegram_rc.static_class.LogManage
import com.qwe7002.telegram_rc.static_class.Network
import com.qwe7002.telegram_rc.static_class.Other
import com.qwe7002.telegram_rc.static_class.SMS
import com.tencent.mmkv.MMKV
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Objects


class BatteryNetworkJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("BatteryNetworkJob", "onStartJob: ")
        MMKV.initialize(applicationContext)
        val message: String = params?.extras?.getString("message", "") ?: ""
        val action: String? = params?.extras?.getString("action", null)
        val TAG = "network_handle"
        val preferences = MMKV.defaultMMKV()
        val chatId = preferences.getString("chat_id", "")!!
        val botToken = preferences.getString("bot_token", "")!!
        val messageThreadId = preferences.getString("message_thread_id", "")!!
        val requestBody = RequestMessage()
        requestBody.chatId = chatId
        requestBody.text = message
        requestBody.messageThreadId = messageThreadId
        var requestUri = Network.getUrl(botToken, "sendMessage")
        val chatInfoMMKV = MMKV.mmkvWithID(Const.CHAT_INFO_MMKV_ID)
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
            Log.d(TAG, "onReceive: edit_mode")
        }
        chatInfoMMKV.getLong("batteryLastReceiveTime", System.currentTimeMillis())
        val okhttpClient = Network.getOkhttpObj()
        val requestBodyRaw = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        val errorHead = "Send battery info failed:"
        /*try {
            val response = call.execute()
            if (response.code == 200) {
                chatInfoMMKV.putLong(
                    "batteryLastReceiveMessageId",
                    Other.getMessageId(Objects.requireNonNull(response.body).string())
                )
            } else {
                chatInfoMMKV.remove("batteryLastReceiveMessageId")
                if (action == Intent.ACTION_BATTERY_LOW) {
                    SMS.sendFallbackSMS(applicationContext, requestBody.text, -1)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            LogManage.writeLog(applicationContext, errorHead + e.message)

        }*/
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                e.printStackTrace()
                LogManage.writeLog(applicationContext, errorHead + e.message)
                if (action == Intent.ACTION_BATTERY_LOW) {
                    SMS.sendFallbackSMS(applicationContext, requestBody.text, -1)
                }
                jobFinished(params,false)
            }

            @Throws(IOException::class)
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val result = Objects.requireNonNull(response.body).string()
                if (response.code != 200) {
                    LogManage.writeLog(applicationContext, errorHead + response.code + " " + result)
                    if (action == Intent.ACTION_BATTERY_LOW) {
                        SMS.sendFallbackSMS(applicationContext, requestBody.text, -1)
                    }
                } else {
                    chatInfoMMKV.putLong(
                        "batteryLastReceiveMessageId",
                        Other.getMessageId(result)
                    )
                }
                jobFinished(params,false)
            }
        })
        return false
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
                ComponentName(context.packageName, BatteryNetworkJob::class.java.getName())
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
