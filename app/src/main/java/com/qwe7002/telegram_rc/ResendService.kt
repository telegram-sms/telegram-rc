package com.qwe7002.telegram_rc

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.request_message
import com.qwe7002.telegram_rc.static_class.CONST
import com.qwe7002.telegram_rc.static_class.log
import com.qwe7002.telegram_rc.static_class.network
import com.qwe7002.telegram_rc.static_class.notify
import com.qwe7002.telegram_rc.static_class.other
import io.paperdb.Paper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ResendService : Service() {
    lateinit var requestUri: String
    lateinit var receiver: stop_notify_receiver
    val table_name: String = "resend_list"

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        resend_list = Paper.book().read(table_name, ArrayList())

        startForegroundNotification()
        return START_NOT_STICKY
    }

    fun startForegroundNotification() {
        val notification: Notification.Builder =
            other.getNotificationObj(applicationContext, getString(R.string.failed_resend))
        // Create a PendingIntent for the broadcast receiver
        val deleteIntent = Intent(applicationContext, DeleteReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            notify.RESEND_SERVICE,
            deleteIntent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )
        // Set the deleteIntent on the notification
        notification.setDeleteIntent(pendingIntent)
        startForeground(
            notify.RESEND_SERVICE, notification.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    internal inner class DeleteReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Get the notification ID from the intent
            Log.d("battery", "onReceive: Received notification that it was removed, try to pull it up again.")
            val notificationId = intent.getIntExtra(Notification.EXTRA_NOTIFICATION_ID, 0)
            if (notificationId == notify.RESEND_SERVICE) {
                startForegroundNotification()
            }
        }
    }


    private fun networkProgressHandle(
        message: String,
        chat_id: String,
        okhttp_client: OkHttpClient,
        message_thread_id: String
    ) {
        val request_body = request_message()
        request_body.chat_id = chat_id
        request_body.text = message
        request_body.message_thread_id = message_thread_id
        if (message.contains("<code>") && message.contains("</code>")) {
            request_body.parse_mode = "html"
        }
        val requestBodyJson = Gson().toJson(request_body)
        val body: RequestBody = requestBodyJson.toRequestBody(CONST.JSON)
        val requestObj: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttp_client.newCall(requestObj)
        try {
            val response = call.execute()
            if (response.code == 200) {
                val resendListLocal = Paper.book().read(table_name, ArrayList<String>())!!
                resendListLocal.remove(message)
                Paper.book().write(table_name, resendListLocal)
            }
        } catch (e: IOException) {
            log.writeLog(applicationContext, "An error occurred while resending: " + e.message)
            e.printStackTrace()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Paper.init(applicationContext)
        val filter = IntentFilter()
        filter.addAction(CONST.BROADCAST_STOP_SERVICE)
        receiver = stop_notify_receiver()
        registerReceiver(receiver, filter)
        val sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        requestUri = network.getUrl(sharedPreferences.getString("bot_token", ""), "SendMessage")

        Thread {
            resend_list = Paper.book().read(table_name, ArrayList())
            while (true) {
                if (network.checkNetworkStatus(applicationContext)) {
                    val send_list = resend_list
                    val okhttp_client =
                        network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true))
                    for (item in send_list!!) {
                        networkProgressHandle(
                            item,
                            sharedPreferences.getString("chat_id", "").toString(),
                            okhttp_client,
                            sharedPreferences.getString("message_thread_id", "").toString()
                        )
                    }
                    resend_list = Paper.book().read(table_name, ArrayList())
                    if (resend_list === send_list || resend_list!!.isEmpty()) {
                        break
                    }
                }
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            log.writeLog(applicationContext, "The resend failure message is complete.")
            stopSelf()
        }.start()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    inner class stop_notify_receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == CONST.BROADCAST_STOP_SERVICE) {
                Log.i("resend_loop", "Received stop signal, quitting now...")
                stopSelf()
            }
        }
    }

    companion object {
        var resend_list: ArrayList<String>? = null
    }
}
