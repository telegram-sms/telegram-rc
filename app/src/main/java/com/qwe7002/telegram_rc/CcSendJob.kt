package com.qwe7002.telegram_rc

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qwe7002.telegram_rc.data_structure.CcSendService
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.LogManage
import com.qwe7002.telegram_rc.static_class.Network
import com.tencent.mmkv.MMKV
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class CcSendJob : JobService() {
    private val logTag = this::class.java.simpleName
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(logTag, "startJob: Trying to send message.")
        
        if (params == null) {
            Log.e(logTag, "onStartJob: params is null")
            return false
        }
        
        val extras = params.extras

        val message: String = extras.getString("message", "") ?: ""
        var title: String = extras.getString("title", getString(R.string.app_name))
            ?: getString(R.string.app_name)
        var verificationCode: String = extras.getString("verification_code", "") ?: ""
        if (verificationCode.isEmpty()) {
            verificationCode = message
        } else {
            title += getString(R.string.verification_code)
        }
        Thread {
            val mmkv = MMKV.defaultMMKV()
            try {
                val serviceListJson = mmkv.getString("CC_service_list", "[]") ?: "[]"
                val gson = Gson()
                val type = object : TypeToken<ArrayList<CcSendService>>() {}.type
                val sendList: ArrayList<CcSendService> = gson.fromJson(serviceListJson, type)
                val okhttpClient = Network.getOkhttpObj()
                for (item in sendList) {
                    if(item.enabled.not()) continue
                    var header: Map<String, String> = mapOf()
                    if (item.header.isNotEmpty()) {
                        val headerType = object : TypeToken<Map<String, String>>() {}.type
                        header = gson.fromJson(item.header, headerType)
                    }
                    val urlParameters = mapOf(
                        "Title" to Uri.encode(title),
                        "Message" to Uri.encode(message),
                        "Code" to Uri.encode(verificationCode)
                    )
                    val bodyParameters = mapOf(
                        "Title" to title,
                        "Message" to message,
                        "Code" to verificationCode
                    )
                    when (item.method) {
                        // 0: GET, 1: POST
                        0 -> {
                            networkProgressHandle(
                                "GET",
                                render(item.webhook, urlParameters),
                                null,
                                header,
                                okhttpClient
                            )
                        }

                        1 -> {
                            networkProgressHandle(
                                "POST",
                                render(item.webhook, urlParameters),
                                render(item.body, bodyParameters).toRequestBody(Const.JSON),
                                header,
                                okhttpClient
                            )
                        }
                    }
                }
                if (sendList.isNotEmpty()) {
                    LogManage.writeLog(applicationContext, "The Cc message is complete.")
                }
            } catch (e: Exception) {
                Log.e(logTag, "Error in CcSend job", e)
            } finally {
                try {
                    jobFinished(params, false)
                } catch (e: Exception) {
                    Log.e(logTag, "Error finishing job", e)
                }
            }
        }.start()

        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }

    private fun networkProgressHandle(
        function: String,
        requestUri: String,
        body: RequestBody?,
        header: Map<String, String>,
        okhttpClient: OkHttpClient,
    ) {

        val requestObj = Request.Builder().url(requestUri).method(function, body)
        for (item in header) {
            requestObj.addHeader(item.key, item.value)
        }
        val call = okhttpClient.newCall(requestObj.build())
        try {
            val response = call.execute()
            if (response.code == 200) {
                Log.i(logTag, "networkProgressHandle: Message sent successfully.")
            }else{
                LogManage.writeLog(applicationContext, "Send message failed: " + response.code + " " + response.body.string())
            }
        } catch (e: IOException) {
            LogManage.writeLog(applicationContext, "An error occurred while resending: " + e.message)
            e.printStackTrace()
        }
    }

    companion object {
        private val TAG = this::class.java.simpleName
        fun startJob(context: Context, title: String, message: String, verificationCode: String) {
            try {
                val jobScheduler =
                    context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
                val jobId = JOB_ID_GENERATOR.getAndIncrement()
                val jobInfoBuilder = JobInfo.Builder(
                    jobId,
                    ComponentName(context.packageName, CcSendJob::class.java.getName())
                )
                    .setPersisted(true)
                val extras = PersistableBundle()
                extras.putString("title", title)
                extras.putString("message", message)
                extras.putString("verification_code", verificationCode)
                jobInfoBuilder.setExtras(extras)
                jobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                val result = jobScheduler.schedule(jobInfoBuilder.build())
                if (result <= 0) {
                    Log.e(TAG, "Failed to schedule job, result code: $result")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start job", e)
            }
        }

        fun startJob(context: Context, title: String, message: String) {
            try {
                val jobScheduler =
                    context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
                val jobId = JOB_ID_GENERATOR.getAndIncrement()
                val jobInfoBuilder = JobInfo.Builder(
                    jobId,
                    ComponentName(context.packageName, CcSendJob::class.java.getName())
                )
                    .setPersisted(true)
                val extras = PersistableBundle()
                extras.putString("title", title)
                extras.putString("message", message)
                jobInfoBuilder.setExtras(extras)
                jobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                val result = jobScheduler.schedule(jobInfoBuilder.build())
                if (result <= 0) {
                    Log.e(TAG, "Failed to schedule job, result code: $result")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start job", e)
            }
        }
        
        fun render(template: String, values: Map<String, String>): String {
            var result = template
            for ((key, value) in values) {
                result = result.replace("{{${key}}}", value)
            }
            return result
        }
        
        val options: ArrayList<String> = arrayListOf(
            "GET",
            "POST"
        )

        private val JOB_ID_GENERATOR = AtomicInteger(100)
    }
}
