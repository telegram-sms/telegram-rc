package com.qwe7002.telegram_rc

import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.LogManage
import com.qwe7002.telegram_rc.static_class.Network
import com.qwe7002.telegram_rc.static_class.Resend
import com.tencent.mmkv.MMKV
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Objects

class NotifyListenerService : NotificationListenerService() {
    private val logTag: String = "notification_receiver"

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        Log.d(logTag, "onNotificationPosted: $packageName")
        MMKV.initialize(applicationContext)
        val preferences = MMKV.defaultMMKV()
        if (!preferences.contains("initialized")) {
            Log.i(logTag, "Uninitialized, Notification receiver is deactivated.")
            return
        }
        val listenList: List<String> = MMKV.defaultMMKV().decodeStringSet("notify_listen_list", setOf())?.toList() ?: listOf()
        if (!listenList.contains(packageName)) {
            Log.i(logTag, "[$packageName] Not in the list of listening packages.")
            return
        }
        val extras = sbn.notification.extras!!
        var appName: String? = "unknown"
        Log.d(logTag, "onNotificationPosted: $appNameList")
        if (appNameList.containsKey(packageName)) {
            appName = appNameList[packageName]
        } else {
            val pm = applicationContext.packageManager
            try {
                val applicationInfo = pm.getApplicationInfo(sbn.packageName, 0)
                appName = pm.getApplicationLabel(applicationInfo) as String
                appNameList[packageName] = appName
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }
        val title = extras.getString(Notification.EXTRA_TITLE, "None")
        val content = extras.getString(Notification.EXTRA_TEXT, "None")
        if (title == "None" && content == "None") {
            return
        }
        val botToken = preferences.getString("bot_token", "").toString()
        val chatId = preferences.getString("chat_id", "").toString()
        val requestUri = Network.getUrl(botToken, "sendMessage")
        val requestBody = RequestMessage()
        if ((System.currentTimeMillis() - lastSendTime) <= 1000L && (lastPackage == packageName)) {
            if (lastMessage == title + content) {
                return
            }
        }
        requestBody.chatId = chatId
        requestBody.messageThreadId = preferences.getString("message_thread_id", "")
        requestBody.text = "${getString(R.string.receive_notification_title)}\n${getString(R.string.app_name_title)}$appName\n${getString(R.string.title)}$title\n${getString(R.string.content)}$content"
        CcSendJob.startJob(applicationContext, getString(R.string.receive_notification_title), requestBody.text)
        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(Const.JSON)
        val okhttpClient = Network.getOkhttpObj()
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        lastPackage = packageName
        lastMessage = title + content
        lastSendTime = System.currentTimeMillis()
        val errorHead = "Send notification failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                LogManage.writeLog(applicationContext, errorHead + e.message)
                Resend.addResendLoop(applicationContext, requestBody.text)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val result = Objects.requireNonNull(response.body).string()
                if (response.code != 200) {
                    LogManage.writeLog(applicationContext, errorHead + response.code + " " + result)
                    Resend.addResendLoop(applicationContext, requestBody.text)
                }
            }
        })
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }


    companion object {
        var appNameList: MutableMap<String, String> = HashMap()
        lateinit var lastPackage: String
        lateinit var lastMessage: String
        var lastSendTime: Long = 0
    }
}
