package com.qwe7002.telegram_rc

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.request_message
import com.qwe7002.telegram_rc.static_class.CONST
import com.qwe7002.telegram_rc.static_class.log
import com.qwe7002.telegram_rc.static_class.network
import com.qwe7002.telegram_rc.static_class.notify
import com.qwe7002.telegram_rc.static_class.other
import com.qwe7002.telegram_rc.static_class.resend
import io.paperdb.Paper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Objects

class NotificationListenerService : NotificationListenerService() {
    val TAG: String = "notification_receiver"
    lateinit var  sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        Paper.init(applicationContext)
        sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        startForegroundNotification()
        return START_STICKY
    }

    fun startForegroundNotification() {
        val notification: Notification.Builder = other.getNotificationObj(
            applicationContext,
            getString(R.string.Notification_Listener_title)
        )
        startForeground(
            notify.NOTIFICATION_LISTENER_SERVICE, notification.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        Log.d(TAG, "onNotificationPosted: $packageName")

        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, Notification receiver is deactivated.")
            return
        }

        val listenList: List<String> =
            Paper.book("system_config").read("notify_listen_list", ArrayList())!!
        if (!listenList.contains(packageName)) {
            Log.i(TAG, "[$packageName] Not in the list of listening packages.")
            return
        }
        val extras = sbn.notification.extras!!
        var appName: String? = "unknown"
        Log.d(TAG, "onNotificationPosted: $appNameList")
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
        val botToken = sharedPreferences.getString("bot_token", "")
        val chatId = sharedPreferences.getString("chat_id", "")
        val requestUri = network.getUrl(botToken, "sendMessage")
        val requestBody = request_message()
        if ((System.currentTimeMillis() - lastSendTime) <= 1000L && (lastPackage == packageName)) {
            if (lastMessage == title + content) {
                return
            }
        }
        requestBody.chat_id = chatId
        requestBody.message_thread_id = sharedPreferences.getString("message_thread_id", "")
        requestBody.text = """
            ${getString(R.string.receive_notification_title)}
            ${getString(R.string.app_name_title)}$appName
            ${getString(R.string.title)}$title
            ${getString(R.string.content)}$content
            """.trimIndent()
        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(CONST.JSON)
        val okhttpClient = network.getOkhttpObj(
            sharedPreferences.getBoolean("doh_switch", true)
        )
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        lastPackage = packageName
        lastMessage = title + content
        lastSendTime = System.currentTimeMillis()
        val error_head = "Send notification failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                log.writeLog(applicationContext, error_head + e.message)
                resend.addResendLoop(applicationContext, requestBody.text)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val result = Objects.requireNonNull(response.body).string()
                if (response.code != 200) {
                    log.writeLog(applicationContext, error_head + response.code + " " + result)
                    resend.addResendLoop(applicationContext, requestBody.text)
                }
            }
        })
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }


    companion object {
        var appNameList: MutableMap<String, String?> = HashMap()
        var lastPackage: String? = null
        var lastMessage: String? = null
        var lastSendTime: Long = 0
    }
}
