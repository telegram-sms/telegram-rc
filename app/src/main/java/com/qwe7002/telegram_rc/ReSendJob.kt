package com.qwe7002.telegram_rc

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.LogManage
import com.qwe7002.telegram_rc.static_class.Network
import com.tencent.mmkv.MMKV
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ReSendJob : JobService() {
    private val logTag = this::class.simpleName
    private lateinit var requestUri: String
    private val tableName: String = "resend_list"
    private lateinit var resendMMKV:MMKV
    private lateinit var preferences: MMKV
    override fun onStartJob(params: JobParameters?): Boolean {
        if (params == null) {
            Log.e(logTag, "onStartJob: params is null")
            return false
        }
        
        try {
            MMKV.initialize(applicationContext)
            preferences = MMKV.defaultMMKV()
            resendMMKV = MMKV.mmkvWithID("resend_list", MMKV.MULTI_PROCESS_MODE)

            requestUri =
                Network.getUrl(preferences.getString("bot_token", "").toString(), "SendMessage")
            
            Thread {
                try {
                    val sendList = resendMMKV.decodeStringSet(tableName, setOf())?.toList() ?: listOf()
                    val okhttpClient = Network.getOkhttpObj()
                    for (item in sendList) {
                        networkProgressHandle(
                            item,
                            preferences.getString("chat_id", "").toString(),
                            okhttpClient,
                            preferences.getString("message_thread_id", "").toString()
                        )
                    }
                    if (sendList.isNotEmpty()) {
                        LogManage.writeLog(applicationContext, "The resend failure message is complete.")
                    }
                } catch (e: Exception) {
                    Log.e(logTag, "Error in resend job", e)
                } finally {
                    jobFinished(params, false)
                }
            }.start()
        } catch (e: Exception) {
            Log.e(logTag, "Failed to start resend job", e)
            return false
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }

    private fun networkProgressHandle(
        message: String,
        chatId: String,
        okhttpClient: OkHttpClient,
        messageThreadId: String
    ) {
        val requestBody = RequestMessage()
        requestBody.chatId = chatId
        requestBody.text = message
        requestBody.messageThreadId = messageThreadId
        if (message.contains("<code>") && message.contains("</code>")) {
            requestBody.parseMode = "html"
        }
        val requestBodyJson = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyJson.toRequestBody(Const.JSON)
        val requestObj: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(requestObj)
        try {
            val response = call.execute()
            try {
                if (response.code == 200) {
                    val resendListLocal = resendMMKV.decodeStringSet(tableName, setOf())?.toMutableList() ?: mutableListOf()
                    resendListLocal.remove(message)
                    resendMMKV.encode(tableName, resendListLocal.toSet())
                }
            } finally {
                response.close()
            }
        } catch (e: IOException) {
            LogManage.writeLog(
                applicationContext,
                "An error occurred while resending: " + e.message
            )
            e.printStackTrace()
        } catch (e: Exception) {
            LogManage.writeLog(
                applicationContext,
                "An unexpected error occurred while resending: " + e.message
            )
            e.printStackTrace()
        }
    }

    companion object {
        fun startJob(context: Context) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            val jobInfoBuilder = JobInfo.Builder(
                20,
                ComponentName(context.packageName, ReSendJob::class.java.getName())
            )
                .setPersisted(true)
            jobInfoBuilder.setPeriodic(TimeUnit.MINUTES.toMillis(15))
            jobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            jobScheduler.schedule(jobInfoBuilder.build())

        }

        fun stopJob(context: Context) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            jobScheduler.cancel(20)
        }
    }
}
