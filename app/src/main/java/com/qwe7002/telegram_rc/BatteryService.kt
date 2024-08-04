package com.qwe7002.telegram_rc

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.fitc.wifihotspot.TetherManager
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.requestMessage
import com.qwe7002.telegram_rc.static_class.Const
import com.qwe7002.telegram_rc.static_class.LogManage
import com.qwe7002.telegram_rc.static_class.Network
import com.qwe7002.telegram_rc.static_class.Notify
import com.qwe7002.telegram_rc.static_class.Other
import com.qwe7002.telegram_rc.static_class.RemoteControl
import com.qwe7002.telegram_rc.static_class.SMS
import io.paperdb.Paper
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Objects

class BatteryService : Service() {
    private lateinit var batteryReceiver: batteryBroadcastReceiver
    private lateinit var botToken: String
    private lateinit var chatId: String
    lateinit var messageThreadId: String
    private var dohSwitch = false
    private var lastReceiveTime: Long = 0
    private var lastReceiveMessageId: Long = -1
    private lateinit var sendLoopList: ArrayList<sendObj>

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()

        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notification =
            Other.getNotificationObj(
                applicationContext,
                getString(R.string.battery_monitoring_notify)
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notify.BATTERY, notification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                Notify.BATTERY, notification.build()
            )
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        Paper.init(applicationContext)
        val preferences = Paper.book("preferences")

        chatId = preferences.read("chat_id", "")!!
        botToken = preferences.read("bot_token", "")!!
        dohSwitch = preferences.read("doh_switch", true)!!
        messageThreadId = preferences.read("message_thread_id", "")!!
        val chargerStatus = preferences.read("charger_status", false)!!
        batteryReceiver = batteryBroadcastReceiver()
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_BATTERY_OKAY)
        filter.addAction(Intent.ACTION_BATTERY_LOW)
        if (chargerStatus) {
            filter.addAction(Intent.ACTION_POWER_CONNECTED)
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        filter.addAction(Const.BROADCAST_STOP_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
        sendLoopList = ArrayList()
        Thread {
            val needRemove = ArrayList<sendObj>()
            while (true) {
                for (item in sendLoopList) {
                    networkHandle(item)
                    needRemove.add(item)
                }
                sendLoopList.removeAll(needRemove.toSet())
                needRemove.clear()
                if (sendLoopList.size == 0) {
                    //Only enter sleep mode when there are no messages
                    try {
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
        }.start()
    }

    private fun networkHandle(obj: sendObj) {
        val TAG = "network_handle"
        val requestBody = requestMessage()
        requestBody.chatId = chatId
        requestBody.text = obj.content
        requestBody.messageThreadId = messageThreadId
        var requestUri = Network.getUrl(botToken, "sendMessage")
        if ((System.currentTimeMillis() - lastReceiveTime) <= 10000L && lastReceiveMessageId != -1L) {
            requestUri = Network.getUrl(botToken, "editMessageText")
            requestBody.messageId = lastReceiveMessageId
            Log.d(TAG, "onReceive: edit_mode")
        }
        lastReceiveTime = System.currentTimeMillis()
        val okhttpClient = Network.getOkhttpObj(dohSwitch)
        val requestBodyRaw = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        val errorHead = "Send battery info failed:"
        try {
            val response = call.execute()
            if (response.code == 200) {
                lastReceiveMessageId =
                    Other.getMessageId(Objects.requireNonNull(response.body).string())
            } else {
                lastReceiveMessageId = -1
                if (obj.action == Intent.ACTION_BATTERY_LOW) {
                    SMS.sendFallbackSMS(applicationContext, requestBody.text, -1)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            LogManage.writeLog(applicationContext, errorHead + e.message)
            if (obj.action == Intent.ACTION_BATTERY_LOW) {
                SMS.sendFallbackSMS(applicationContext, requestBody.text, -1)
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private class sendObj {
        lateinit var content: String
        lateinit var action: String
    }

    internal inner class batteryBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val TAG = "battery_receiver"
            Log.d(TAG, "Receive action: " + intent.action)
            if (intent.action == Const.BROADCAST_STOP_SERVICE) {
                Log.i(TAG, "Received stop signal, quitting now...")
                stopSelf()
                Process.killProcess(Process.myPid())
                return
            }
            val builder = StringBuilder(context.getString(R.string.system_message_head) + "\n")
            val action = intent.action
            val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
            when (Objects.requireNonNull(action)) {
                Intent.ACTION_BATTERY_OKAY -> builder.append(context.getString(R.string.low_battery_status_end))
                Intent.ACTION_BATTERY_LOW -> {
                    builder.append(context.getString(R.string.battery_low))
                    if (RemoteControl.isHotspotActive(context)) {
                        RemoteControl.disableHotspot(
                            context,
                            TetherManager.TetherMode.TETHERING_WIFI
                        )
                        builder.append("\n").append(getString(R.string.disable_wifi))
                            .append(context.getString(R.string.action_success))
                    }
                    if (Paper.book("temp").read("wifi_open", false)!!) {
                        val wifiManager =
                            (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
                        RemoteControl.disableVPNHotspot(wifiManager)
                        builder.append("\n").append(getString(R.string.disable_wifi))
                            .append(context.getString(R.string.action_success))
                    }
                }

                Intent.ACTION_POWER_CONNECTED -> builder.append(context.getString(R.string.charger_connect))

                Intent.ACTION_POWER_DISCONNECTED -> builder.append(context.getString(R.string.charger_disconnect))
            }
            var batteryLevel =
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (batteryLevel > 100) {
                Log.d(TAG, "The previous battery is over 100%, and the correction is 100%.")
                batteryLevel = 100
            }
            val result =
                builder.append("\n").append(context.getString(R.string.current_battery_level))
                    .append(batteryLevel).append("%").toString()
            val obj = sendObj()
            if (action != null) {
                obj.action = action
                obj.content = result
            }

            sendLoopList.add(obj)
        }
    }


}

