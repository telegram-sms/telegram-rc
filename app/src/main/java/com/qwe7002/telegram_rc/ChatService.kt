package com.qwe7002.telegram_rc

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.Process
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.fitc.wifihotspot.TetherManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.qwe7002.telegram_rc.data_structure.pollingJson
import com.qwe7002.telegram_rc.data_structure.replyMarkupKeyboard
import com.qwe7002.telegram_rc.data_structure.replyMarkupKeyboard.InlineKeyboardButton
import com.qwe7002.telegram_rc.data_structure.replyMarkupKeyboard.keyboardMarkup
import com.qwe7002.telegram_rc.data_structure.requestMessage
import com.qwe7002.telegram_rc.data_structure.smsRequestInfo
import com.qwe7002.telegram_rc.root_kit.ActivityManage.checkServiceIsRunning
import com.qwe7002.telegram_rc.root_kit.Networks.addDummyDevice
import com.qwe7002.telegram_rc.root_kit.Networks.delDummyDevice
import com.qwe7002.telegram_rc.root_kit.Networks.setData
import com.qwe7002.telegram_rc.root_kit.Networks.setWifi
import com.qwe7002.telegram_rc.root_kit.Radio.isLTECA
import com.qwe7002.telegram_rc.root_kit.Radio.isNRConnected
import com.qwe7002.telegram_rc.root_kit.Radio.isNRStandby
import com.qwe7002.telegram_rc.static_class.Const
import com.qwe7002.telegram_rc.static_class.LogManage.readLog
import com.qwe7002.telegram_rc.static_class.LogManage.writeLog
import com.qwe7002.telegram_rc.static_class.Network.checkNetworkStatus
import com.qwe7002.telegram_rc.static_class.Network.getDataEnable
import com.qwe7002.telegram_rc.static_class.Network.getOkhttpObj
import com.qwe7002.telegram_rc.static_class.Network.getUrl
import com.qwe7002.telegram_rc.static_class.Notify
import com.qwe7002.telegram_rc.static_class.Other.getActiveCard
import com.qwe7002.telegram_rc.static_class.Other.getDataSimId
import com.qwe7002.telegram_rc.static_class.Other.getDualSimCardDisplay
import com.qwe7002.telegram_rc.static_class.Other.getMessageId
import com.qwe7002.telegram_rc.static_class.Other.getNotificationObj
import com.qwe7002.telegram_rc.static_class.Other.getSendPhoneNumber
import com.qwe7002.telegram_rc.static_class.Other.getSimDisplayName
import com.qwe7002.telegram_rc.static_class.Other.getSubId
import com.qwe7002.telegram_rc.static_class.Other.isPhoneNumber
import com.qwe7002.telegram_rc.static_class.Other.parseStringToLong
import com.qwe7002.telegram_rc.static_class.RemoteControl.disableHotspot
import com.qwe7002.telegram_rc.static_class.RemoteControl.disableVPNHotspot
import com.qwe7002.telegram_rc.static_class.RemoteControl.enableHotspot
import com.qwe7002.telegram_rc.static_class.RemoteControl.enableVPNHotspot
import com.qwe7002.telegram_rc.static_class.RemoteControl.isHotspotActive
import com.qwe7002.telegram_rc.static_class.RemoteControl.isVPNHotspotExist
import com.qwe7002.telegram_rc.static_class.SMS.sendFallbackSMS
import com.qwe7002.telegram_rc.static_class.SMS.sendSMS
import com.qwe7002.telegram_rc.static_class.ServiceManage.stopAllService
import com.qwe7002.telegram_rc.static_class.USSD.sendUssd
import io.paperdb.Paper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.Objects
import java.util.concurrent.TimeUnit

/**
 * @noinspection CallToPrintStackTrace
 */
@Suppress("DEPRECATION", "ClassName")
class ChatService : Service() {
    // global object
    private lateinit var okhttpClient: OkHttpClient
    private lateinit var messageThreadId: String
    private lateinit var broadcastReceiver: quitBroadcastReceiver
    private lateinit var wakeLock: WakeLock
    private lateinit var wifiLock: WifiLock
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var callback: networkCallBack
    private lateinit var botUsername: String
    private var privacyMode = false
    private lateinit var chatID: String
    private lateinit var botToken: String


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        startForegroundNotification()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notification = getNotificationObj(
            applicationContext, getString(R.string.chat_command_service_name)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notify.CHAT_COMMAND,
                notification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Notify.CHAT_COMMAND, notification.build())
        }
    }


    override fun onDestroy() {
        wifiLock.release()
        wakeLock.release()
        unregisterReceiver(broadcastReceiver)
        connectivityManager.unregisterNetworkCallback(callback)
        super.onDestroy()
    }


    override fun onBind(intent: Intent): IBinder? {
        return null
    }


    private fun me(): Boolean{
            val okhttpClientNew = okhttpClient
            val url = getUrl(botToken, "getMe")
            val request: Request = Request.Builder().url(url).build()
            val call = okhttpClientNew.newCall(request)
            val response: Response
            try {
                response = call.execute()
            } catch (e: IOException) {
                e.printStackTrace()
                writeLog(applicationContext, "Get username failed:" + e.message)
                return false
            }
            if (response.code == 200) {
                val result: String
                try {
                    result = Objects.requireNonNull(response.body).string()
                } catch (e: IOException) {
                    e.printStackTrace()
                    return false
                }
                val resultObj = JsonParser.parseString(result).asJsonObject
                if (resultObj["ok"].asBoolean) {
                    botUsername = resultObj["result"].asJsonObject["username"].asString
                    writeLog(applicationContext, "Get the bot username: $botUsername")
                }
                return true
            }
            return false
        }

    private fun receiveHandle(resultObj: JsonObject, getIdOnly: Boolean) {
        val updateId = resultObj["update_id"].asLong
        offset = updateId + 1
        if (getIdOnly) {
            Log.i(TAG, "receive_handle: get_id_only")
            return
        }

        var messageType = ""
        val requestBody = requestMessage()
        requestBody.chatId = chatID
        requestBody.messageThreadId = messageThreadId
        lateinit var messageObj: JsonObject
        lateinit var callbackData: String
        if (resultObj.has("message")) {
            messageObj = resultObj["message"].asJsonObject
            messageType = messageObj["chat"].asJsonObject["type"].asString
        }
        if (resultObj.has("channel_post")) {
            messageType = "channel"
            messageObj = resultObj["channel_post"].asJsonObject
        }
        if (resultObj.has("callback_query")) {
            messageType = "callback_query"
            val callbackQuery = resultObj["callback_query"].asJsonObject
            callbackData = callbackQuery["data"].asString
        }
        if (messageType == "callback_query" && sendSmsNextStatus != SEND_SMS_STATUS.STANDBY_STATUS) {
            var slot = Paper.book("send_temp").read("slot", -1)!!
            val messageId = Paper.book("send_temp").read("message_id", -1L)!!
            val to = Paper.book("send_temp").read("to", "")
            val content = Paper.book("send_temp").read("content", "")
            if (callbackData != CALLBACK_DATA_VALUE.SEND) {
                setSmsSendStatusStandby()
                val requestUri = getUrl(
                    botToken, "editMessageText"
                )
                val dualSim = getDualSimCardDisplay(
                    applicationContext,
                    slot,
                    sharedPreferences.getBoolean("display_dual_sim_display_name", false)
                )
                val sendContent = """
                    [$dualSim${applicationContext.getString(R.string.send_sms_head)}]
                    ${applicationContext.getString(R.string.to)}$to
                    ${applicationContext.getString(R.string.content)}$content
                    """.trimIndent()
                requestBody.text = """
                    $sendContent
                    ${applicationContext.getString(R.string.status)}${applicationContext.getString(R.string.cancel_button)}
                    """.trimIndent()
                requestBody.messageId = messageId
                requestBody.messageThreadId = messageThreadId
                val gson = Gson()
                val requestBodyRaw = gson.toJson(requestBody)
                val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
                val request: Request =
                    Request.Builder().url(requestUri).method("POST", body).build()
                val call = okhttpClient.newCall(request)
                try {
                    val response = call.execute()
                    if (response.code != 200) {
                        throw IOException(response.code.toString())
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    writeLog(applicationContext, "failed to send message:" + e.message)
                }
                return
            }
            var subId = -1
            if (getActiveCard(applicationContext) == 1) {
                slot = -1
            } else {
                subId = getSubId(applicationContext, slot)
            }
            sendSMS(applicationContext, to!!, content!!, slot, subId, messageId)
            setSmsSendStatusStandby()
            return
        }

        lateinit var fromObj: JsonObject
        var fromTopicId = ""
        val messageTypeIsPrivate = messageType == "private"
        if (messageObj.has("from")) {
            fromObj = messageObj["from"].asJsonObject
            if (!messageTypeIsPrivate && fromObj["is_bot"].asBoolean) {
                Log.i(TAG, "receive_handle: receive from bot.")
                return
            }
        }
        if (messageThreadId != "") {
            if (messageObj.has("is_topic_message")) {
                fromTopicId = messageObj["message_thread_id"].asString
            }
            if (messageThreadId != fromTopicId) {
                Log.i(TAG, "Topic ID[$fromTopicId] not allow.")
                return
            }
        }
        if (messageObj.has("chat")) {
            fromObj = messageObj["chat"].asJsonObject
        }
        val fromId = fromObj["id"].asString
        if (chatID != fromId) {
            writeLog(applicationContext, "Chat ID[$fromId] not allow")
            return
        }

        var command = ""
        var commandBotUsername = ""
        var requestMsg = ""
        if (messageObj.has("text")) {
            requestMsg = messageObj["text"].asString
        }
        if (messageObj.has("reply_to_message")) {
            val saveItem = Paper.book().read<smsRequestInfo>(
                messageObj["reply_to_message"].asJsonObject["message_id"].asString,
                null
            )
            if (saveItem != null && requestMsg.isNotEmpty()) {
                val phoneNumber = saveItem.phone
                val cardSlot = saveItem.card
                sendSmsNextStatus = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS
                Paper.book("send_temp").write("slot", cardSlot)
                Paper.book("send_temp").write("to", phoneNumber)
                Paper.book("send_temp").write("content", requestMsg)
            }
        }
        var hasCommand = false
        if (messageObj.has("entities")) {
            val tempCommand: String
            val tempCommandLowercase: String
            val entitiesArr = messageObj["entities"].asJsonArray
            val entitiesObjCommand = entitiesArr[0].asJsonObject
            if (entitiesObjCommand["type"].asString == "bot_command") {
                hasCommand = true
                val commandOffset = entitiesObjCommand["offset"].asInt
                val commandEndOffset = commandOffset + entitiesObjCommand["length"].asInt
                tempCommand =
                    requestMsg.substring(commandOffset, commandEndOffset).trim { it <= ' ' }
                tempCommandLowercase =
                    tempCommand.lowercase(Locale.getDefault()).replace("_", "")
                command = tempCommandLowercase
                if (tempCommandLowercase.contains("@")) {
                    val commandAtLocation = tempCommandLowercase.indexOf("@")
                    command = tempCommandLowercase.substring(0, commandAtLocation)
                    commandBotUsername = tempCommand.substring(commandAtLocation + 1)
                }
            }
        }

        if (!messageTypeIsPrivate && privacyMode && commandBotUsername != botUsername) {
            Log.i(TAG, "receive_handle: Privacy mode, no username found.")
            return
        }

        Log.d(TAG, "Command: $command")

        when (command) {
            "/help", "/start", "/commandlist" -> {
                var smsCommand = """
                
                ${getString(R.string.sendsms)}
                """.trimIndent()
                if (getActiveCard(applicationContext) == 2) {
                    smsCommand = """
                        
                        ${getString(R.string.sendsms_dual)}
                        """.trimIndent()
                }
                smsCommand += """
                
                ${getString(R.string.get_spam_sms)}
                """.trimIndent()

                var ussdCommand = ""
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    ussdCommand = """
                        
                        ${getString(R.string.send_ussd_command)}
                        """.trimIndent()
                    if (getActiveCard(applicationContext) == 2) {
                        ussdCommand = """
                            
                            ${getString(R.string.send_ussd_dual_command)}
                            """.trimIndent()
                    }
                }
                var switchAp = ""
                if (Settings.System.canWrite(applicationContext)) {
                    switchAp += """
                        
                        ${getString(R.string.switch_ap_message)}
                        """.trimIndent()
                }
                if (sharedPreferences.getBoolean("root", false)) {
                    if (isVPNHotspotExist(applicationContext)) {
                        switchAp += """
                            
                            ${
                            getString(R.string.switch_ap_message).replace(
                                "/hotspot",
                                "/vpnhotspot"
                            )
                        }
                            """.trimIndent()
                    }
                    switchAp += """
                        
                        ${getString(R.string.switch_data_message)}
                        """.trimIndent()
                }
                if (command == "/commandlist") {
                    requestBody.text =
                        (getString(R.string.available_command) + smsCommand + ussdCommand + switchAp).replace(
                            "/",
                            ""
                        )
                } else {
                    var resultString = """
                ${getString(R.string.system_message_head)}
                ${getString(R.string.available_command)}$smsCommand$ussdCommand$switchAp
                """.trimIndent()
                    if (!messageTypeIsPrivate && privacyMode && botUsername.isNotEmpty()) {
                        resultString = resultString.replace(" -", "@$botUsername -")
                    }
                    requestBody.text = resultString
                }
            }

            "/ping", "/getinfo" -> {
                var cardInfo = ""
                applicationContext
                    .getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.READ_PHONE_STATE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    cardInfo = "\nSIM: " + getSimDisplayName(
                        applicationContext, 0
                    )
                    if (getActiveCard(applicationContext) == 2) {
                        cardInfo = """
     
     ${getString(R.string.current_data_card)}: SIM
     """.trimIndent() + getDataSimId(
                            applicationContext
                        ) + "\nSIM1: " + getSimDisplayName(
                            applicationContext, 0
                        ) + "\nSIM2: " + getSimDisplayName(
                            applicationContext, 1
                        )
                    }
                }
                var spamCount = ""
                val spamList = Paper.book().read("spam_sms_list", ArrayList<String>())!!
                if (spamList.isNotEmpty()) {
                    spamCount = """
                        
                        ${getString(R.string.spam_count_title)}${spamList.size}
                        """.trimIndent()
                }
                var isHotspotRunning = ""
                if (Settings.System.canWrite(applicationContext)) {
                    isHotspotRunning += """
                        
                        ${getString(R.string.hotspot_status)}
                        """.trimIndent()
                    isHotspotRunning += if (isHotspotActive(applicationContext)) {
                        getString(R.string.enable)
                    } else {
                        getString(R.string.disable)
                    }
                }
                if (sharedPreferences.getBoolean("root", false) && isVPNHotspotExist(
                        applicationContext
                    )
                ) {
                    isHotspotRunning += """
                        
                        VPN ${getString(R.string.hotspot_status)}
                        """.trimIndent()
                    isHotspotRunning += if (checkServiceIsRunning(
                            "be.mygod.vpnhotspot",
                            ".RepeaterService"
                        )
                    ) {
                        getString(R.string.enable)
                    } else {
                        getString(R.string.disable)
                    }
                }
                var beaconStatus = """
                    
                    ${getString(R.string.beacon_monitoring_status)}
                    """.trimIndent()
                beaconStatus += if (java.lang.Boolean.FALSE == Paper.book()
                        .read("disable_beacon", false)
                ) {
                    getString(R.string.enable)
                } else {
                    getString(R.string.disable)
                }
                requestBody.text = """
     ${getString(R.string.system_message_head)}
     ${applicationContext.getString(R.string.current_battery_level)}
     """.trimIndent() + getBatteryInfo(
                    applicationContext
                ) + "\n" + getString(R.string.current_network_connection_status) + getNetworkType(
                    applicationContext, sharedPreferences
                ) + isHotspotRunning + beaconStatus + spamCount + cardInfo
                Log.d(TAG, "receive_handle: " + requestBody.text)
            }

            "/log" -> requestBody.text = getString(R.string.system_message_head) + readLog(
                applicationContext, 10
            )

            "/wifi" -> {
                if (!sharedPreferences.getBoolean("root", false)) {
                    requestBody.text = """
                        ${getString(R.string.system_message_head)}
                        ${getString(R.string.no_permission)}
                        """.trimIndent()
                } else {
                    val wifimanager =
                        applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                    setWifi(!wifimanager.isWifiEnabled)
                    requestBody.text = """
                    ${getString(R.string.system_message_head)}
                    Done
                    """.trimIndent()
                }
            }

            "/hotspot" -> {
                val apStatus = isHotspotActive(applicationContext)
                var resultAp: String
                if (!apStatus) {
                    resultAp =
                        getString(R.string.enable_wifi) + applicationContext.getString(R.string.action_success)
                    val commandListData =
                        requestMsg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    var tetherMode = TetherManager.TetherMode.TETHERING_WIFI
                    if (commandListData.size == 2) {
                        tetherMode =
                            when (commandListData[1].lowercase(Locale.getDefault())) {
                                "bluetooth" -> TetherManager.TetherMode.TETHERING_BLUETOOTH
                                "ncm" -> TetherManager.TetherMode.TETHERING_NCM
                                "usb" -> TetherManager.TetherMode.TETHERING_USB
                                "nic" -> TetherManager.TetherMode.TETHERING_ETHERNET
                                "wigig" -> TetherManager.TetherMode.TETHERING_WIGIG
                                else -> TetherManager.TetherMode.TETHERING_WIFI
                            }
                    }
                    Paper.book("temp").write("tether_mode", tetherMode)
                    enableHotspot(applicationContext, tetherMode)
                } else {
                    Paper.book("temp").write("tether_open", false)
                    resultAp =
                        getString(R.string.disable_wifi) + applicationContext.getString(R.string.action_success)
                }
                resultAp += """
     
     ${applicationContext.getString(R.string.current_battery_level)}
     """.trimIndent() + getBatteryInfo(
                    applicationContext
                ) + "\n" + getString(R.string.current_network_connection_status) + getNetworkType(
                    applicationContext, sharedPreferences
                )
                requestBody.text = """
                        ${getString(R.string.system_message_head)}
                        $resultAp
                        """.trimIndent()
            }

            "/vpnhotspot" -> {
                if (!sharedPreferences.getBoolean("root", false) || !isVPNHotspotExist(
                        applicationContext
                    )
                ) {
                    requestBody.text = """
                        ${getString(R.string.system_message_head)}
                        ${getString(R.string.no_permission)}
                        """.trimIndent()

                } else {
                    val wifiManager =
                        applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                    val wifiOpen =
                        Paper.book("temp").read("wifi_open", wifiManager.isWifiEnabled)!!
                    var resultVpnAp: String
                    if (!wifiOpen) {
                        resultVpnAp =
                            getString(R.string.enable_wifi) + applicationContext.getString(R.string.action_success)
                        Thread { enableVPNHotspot(wifiManager) }.start()
                    } else {
                        Paper.book("temp").write("wifi_open", false)
                        resultVpnAp =
                            getString(R.string.disable_wifi) + applicationContext.getString(R.string.action_success)
                    }
                    resultVpnAp += """
     
     ${applicationContext.getString(R.string.current_battery_level)}
     """.trimIndent() + getBatteryInfo(
                        applicationContext
                    ) + "\n" + getString(R.string.current_network_connection_status) + getNetworkType(
                        applicationContext, sharedPreferences
                    )

                    requestBody.text = """
                    ${getString(R.string.system_message_head)}
                    $resultVpnAp
                    """.trimIndent()
                }
            }

            "/mobiledata" -> {
                if (!sharedPreferences.getBoolean("root", false)) {
                    requestBody.text = """
                        ${getString(R.string.system_message_head)}
                        ${getString(R.string.no_permission)}
                        """.trimIndent()
                } else {
                    val resultData = applicationContext.getString(R.string.switch_data)

                    requestBody.text = """
                    ${getString(R.string.system_message_head)}
                    $resultData
                    """.trimIndent()
                }
            }

            "/sendussd", "/sendussd1", "/sendussd2" -> {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    var subId = -1
                    if (getActiveCard(applicationContext) == 2) {
                        if (command == "/sendussd2") {
                            subId = getSubId(applicationContext, 1)
                        }
                    }
                    val commandList =
                        requestMsg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    if (commandList.size == 2) {
                        sendUssd(applicationContext, commandList[1], subId)
                        return
                    } else {
                        requestBody.text = "Error"
                    }
                } else {
                    Log.i(TAG, "send_ussd: No permission.")
                }
                requestBody.text = """
                    ${applicationContext.getString(R.string.system_message_head)}
                    ${getString(R.string.unknown_command)}
                    """.trimIndent()
            }

            "/getspamsms" -> {
                val spamSmsList = Paper.book().read("spam_sms_list", ArrayList<String>())!!
                if (spamSmsList.isEmpty()) {
                    requestBody.text = """
                        ${applicationContext.getString(R.string.system_message_head)}
                        ${getString(R.string.no_spam_history)}
                        """.trimIndent()
                } else {
                    Thread {
                        if (checkNetworkStatus(applicationContext)) {
                            for (item in spamSmsList) {
                                val sendSmsRequestBody = requestMessage()
                                sendSmsRequestBody.chatId = chatID
                                sendSmsRequestBody.text = item
                                val requestUri = getUrl(
                                    botToken, "sendMessage"
                                )
                                val requestBodyJson = Gson().toJson(sendSmsRequestBody)
                                val body: RequestBody =
                                    requestBodyJson.toRequestBody(Const.JSON)
                                val requestObj: Request =
                                    Request.Builder().url(requestUri).method("POST", body).build()
                                val call = okhttpClient.newCall(requestObj)
                                call.enqueue(object : Callback {
                                    override fun onFailure(call: Call, e: IOException) {
                                        e.printStackTrace()
                                    }

                                    @Throws(IOException::class)
                                    override fun onResponse(call: Call, response: Response) {
                                        Log.d(
                                            TAG,
                                            "onResponse: " + Objects.requireNonNull(response.body)
                                                .string()
                                        )
                                    }
                                })
                                val resendListLocal =
                                    Paper.book().read("spam_sms_list", ArrayList<String>())!!
                                resendListLocal.remove(item)
                                Paper.book().write("spam_sms_list", resendListLocal)
                            }
                        }
                        writeLog(applicationContext, "Send spam message is complete.")
                    }.start()
                    return
                }
            }

            "/autoswitch" -> {
                val state = !Paper.book().read("disable_beacon", false)!!
                Paper.book().write("disable_beacon", state)
                requestBody.text = """
                    ${applicationContext.getString(R.string.system_message_head)}
                    Beacon monitoring status: ${!state}
                    """.trimIndent()
            }

            "/setdummy" -> {
                if (!sharedPreferences.getBoolean("root", false)) {
                    requestBody.text = """
                        ${getString(R.string.system_message_head)}
                        ${getString(R.string.no_permission)}
                        """.trimIndent()
                } else {
                    val commandList =
                        requestMsg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    if (commandList.size == 2) {
                        Paper.book("system_config").write("dummy_ip_addr", commandList[1])
                        addDummyDevice(commandList[1])
                    } else {
                        if (Paper.book("system_config").contains("dummy_ip_addr")) {
                            val dummyIp =
                                Paper.book("system_config").read<String>("dummy_ip_addr")
                            addDummyDevice(dummyIp!!)
                        }
                    }
                    requestBody.text = """
                    ${applicationContext.getString(R.string.system_message_head)}
                    Done
                    """.trimIndent()
                }
            }

            "/deldummy" -> {
                if (!sharedPreferences.getBoolean("root", false)) {
                    requestBody.text = """
                        ${getString(R.string.system_message_head)}
                        ${getString(R.string.no_permission)}
                        """.trimIndent()
                }else {
                    delDummyDevice()
                    requestBody.text = """
                    ${applicationContext.getString(R.string.system_message_head)}
                    Done
                    """.trimIndent()
                }
            }

            "/sendsms", "/sendsms1", "/sendsms2" -> {
                val msgSendList =
                    requestMsg.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (msgSendList.size > 2) {
                    val msgSendTo = getSendPhoneNumber(
                        msgSendList[1]
                    )
                    if (isPhoneNumber(msgSendTo)) {
                        val msgSendContent = StringBuilder()
                        var i = 2
                        while (i < msgSendList.size) {
                            if (msgSendList.size != 3 && i != 2) {
                                msgSendContent.append("\n")
                            }
                            msgSendContent.append(msgSendList[i])
                            ++i
                        }
                        if (getActiveCard(applicationContext) == 1) {
                            sendSMS(
                                applicationContext,
                                msgSendTo,
                                msgSendContent.toString(),
                                -1,
                                -1
                            )
                            return
                        }
                        var sendSlot = -1
                        if (getActiveCard(applicationContext) > 1) {
                            sendSlot = 0
                            if (command == "/sendsms2") {
                                sendSlot = 1
                            }
                        }
                        val subId = getSubId(
                            applicationContext, sendSlot
                        )
                        if (subId != -1) {
                            sendSMS(
                                applicationContext,
                                msgSendTo,
                                msgSendContent.toString(),
                                sendSlot,
                                subId
                            )
                            return
                        }
                    }
                } else {
                    hasCommand = false
                    sendSmsNextStatus = SEND_SMS_STATUS.PHONE_INPUT_STATUS
                    var sendSlot = -1
                    if (getActiveCard(applicationContext) > 1) {
                        sendSlot = 0
                        if (command == "/sendsms2") {
                            sendSlot = 1
                        }
                    }
                    Paper.book("send_temp").write("slot", sendSlot)
                }
                requestBody.text = """
                    [${applicationContext.getString(R.string.send_sms_head)}]
                    ${getString(R.string.failed_to_get_information)}
                    """.trimIndent()
            }

            else -> {
                if (!messageTypeIsPrivate && sendSmsNextStatus == -1) {
                    if (messageType != "supergroup" || messageThreadId.isEmpty()) {
                        Log.i(
                            TAG,
                            "receive_handle: The conversation is not Private and does not prompt an error."
                        )
                        return
                    }
                }

                requestBody.text = """
                    ${applicationContext.getString(R.string.system_message_head)}
                    ${getString(R.string.unknown_command)}
                    """.trimIndent()
            }
        }
        if (hasCommand) {
            setSmsSendStatusStandby()
        }
        if (!hasCommand && sendSmsNextStatus != -1) {
            Log.i(TAG, "receive_handle: Enter the interactive SMS sending mode.")
            var dualSim = ""
            val sendSlotTemp = Paper.book("send_temp").read("slot", -1)!!
            if (sendSlotTemp != -1) {
                dualSim = "SIM" + (sendSlotTemp + 1) + " "
            }
            val head = "[" + dualSim + applicationContext.getString(R.string.send_sms_head) + "]"
            var resultSend = getString(R.string.failed_to_get_information)
            Log.d(TAG, "Sending mode status: $sendSmsNextStatus")

            when (sendSmsNextStatus) {
                SEND_SMS_STATUS.PHONE_INPUT_STATUS -> {
                    sendSmsNextStatus = SEND_SMS_STATUS.MESSAGE_INPUT_STATUS
                    resultSend = getString(R.string.enter_number)
                }

                SEND_SMS_STATUS.MESSAGE_INPUT_STATUS -> {
                    val tempTo = getSendPhoneNumber(requestMsg)
                    if (isPhoneNumber(tempTo)) {
                        Paper.book("send_temp").write("to", tempTo)
                        resultSend = getString(R.string.enter_content)
                        sendSmsNextStatus = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS
                    } else {
                        setSmsSendStatusStandby()
                        resultSend = getString(R.string.unable_get_phone_number)
                    }
                }

                SEND_SMS_STATUS.WAITING_TO_SEND_STATUS -> {
                    Paper.book("send_temp").write("content", requestMsg)
                    val keyboardMarkup = keyboardMarkup()
                    val inlineKeyboardButtons = ArrayList<ArrayList<InlineKeyboardButton>>()
                    inlineKeyboardButtons.add(
                        replyMarkupKeyboard.getInlineKeyboardObj(
                            applicationContext.getString(
                                R.string.send_button
                            ), CALLBACK_DATA_VALUE.SEND
                        )
                    )
                    inlineKeyboardButtons.add(
                        replyMarkupKeyboard.getInlineKeyboardObj(
                            applicationContext.getString(
                                R.string.cancel_button
                            ), CALLBACK_DATA_VALUE.CANCEL
                        )
                    )
                    keyboardMarkup.inlineKeyboard = inlineKeyboardButtons
                    requestBody.keyboardMarkup = keyboardMarkup
                    resultSend = """
                        ${applicationContext.getString(R.string.to)}${
                        Paper.book("send_temp").read<Any>("to")
                    }
                        ${applicationContext.getString(R.string.content)}${
                        Paper.book("send_temp").read("content", "")
                    }
                        """.trimIndent()
                    sendSmsNextStatus = SEND_SMS_STATUS.SEND_STATUS
                }
            }
            requestBody.text = """
                $head
                $resultSend
                """.trimIndent()
        }

        val requestUri = getUrl(botToken, "sendMessage")
        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(Const.JSON)
        Log.d(TAG, "receive_handle: " + Gson().toJson(requestBody))
        val sendRequest: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(sendRequest)
        val errorHead = "Send reply failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                writeLog(applicationContext, errorHead + e.message)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val responseString = Objects.requireNonNull(response.body).string()
                if (response.code != 200) {
                    writeLog(applicationContext, errorHead + response.code + " " + responseString)
                    return
                }
                val commandValue = command.replace("_", "")
                if (!hasCommand && sendSmsNextStatus == SEND_SMS_STATUS.SEND_STATUS) {
                    Paper.book("send_temp").write("message_id", getMessageId(responseString))
                }

                if (commandValue == "/hotspot" || commandValue == "/vpnhotspot") {
                    if (!Paper.book("temp").read("tether_open", false)!!) {
                        disableHotspot(
                            applicationContext,
                            Paper.book("temp")
                                .read("tether_mode", TetherManager.TetherMode.TETHERING_WIFI)!!
                        )
                        Paper.book("temp").delete("tether_mode")
                    }
                }
                if (hasCommand && sharedPreferences.getBoolean("root", false)) {
                    when (commandValue) {
                        "/vpnhotspot" -> if (!Paper.book("temp").read("wifi_open", false)!!) {
                            val wifiManager = checkNotNull(
                                applicationContext.getSystemService(
                                    WIFI_SERVICE
                                ) as WifiManager
                            )
                            disableVPNHotspot(wifiManager)
                        }

                        "/mobiledata" -> setData(
                            !getDataEnable(
                                applicationContext
                            )
                        )
                    }
                }
            }
        })
    }


    internal object CALLBACK_DATA_VALUE {
        const val SEND: String = "send"
        const val CANCEL: String = "cancel"
    }

    private fun setSmsSendStatusStandby() {
        sendSmsNextStatus = SEND_SMS_STATUS.STANDBY_STATUS
        Paper.book("send_temp").destroy()
    }

    @SuppressLint("InvalidWakeLockTag", "WakelockTimeout", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        connectivityManager =
            applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        Paper.init(applicationContext)
        setSmsSendStatusStandby()
        sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)

        chatID = sharedPreferences.getString("chat_id", "").toString()
        botToken = sharedPreferences.getString("bot_token", "").toString()
        messageThreadId = sharedPreferences.getString("message_thread_id", "").toString()
        okhttpClient = getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true))
        privacyMode = sharedPreferences.getBoolean("privacy_mode", false)
        wifiLock = (Objects.requireNonNull(
            applicationContext.getSystemService(
                WIFI_SERVICE
            )
        ) as WifiManager).createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "bot_command_polling_wifi"
        )
        wakeLock =
            (Objects.requireNonNull(applicationContext.getSystemService(POWER_SERVICE)) as PowerManager).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "bot_command_polling"
            )
        wifiLock.setReferenceCounted(false)
        wakeLock.setReferenceCounted(false)
        if (!wifiLock.isHeld) {
            wifiLock.acquire()
        }
        if (!wakeLock.isHeld) {
            wakeLock.acquire()
        }
        thread_main = Thread(threadMainRunnable())
        thread_main!!.start()
        val intentFilter = IntentFilter()
        intentFilter.addAction(Const.BROADCAST_STOP_SERVICE)
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        callback = networkCallBack()
        connectivityManager.registerNetworkCallback(networkRequest, callback)
        broadcastReceiver = quitBroadcastReceiver()
        registerReceiver(broadcastReceiver, intentFilter)
    }

    private fun whenNetworkChange() {
        if (checkNetworkStatus(applicationContext)) {
            if (!thread_main!!.isAlive) {
                writeLog(applicationContext, "Network connections has been restored.")
                thread_main = Thread(threadMainRunnable())
                thread_main!!.start()
            }
        }
    }

    private object SEND_SMS_STATUS {
        const val STANDBY_STATUS: Int = -1
        const val PHONE_INPUT_STATUS: Int = 0
        const val MESSAGE_INPUT_STATUS: Int = 1
        const val WAITING_TO_SEND_STATUS: Int = 2
        const val SEND_STATUS: Int = 3
    }

    private inner class quitBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "onReceive: " + intent.action)
            checkNotNull(intent.action)
            if (Const.BROADCAST_STOP_SERVICE == intent.action) {
                Log.i(TAG, "Received stop signal, quitting now...")
                stopSelf()
                Process.killProcess(Process.myPid())
            }
        }
    }

    private inner class networkCallBack : NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "onAvailable")
            whenNetworkChange()
        }
    }

    internal inner class threadMainRunnable : Runnable {
        override fun run() {
            Log.d(TAG, "run: thread main start")
            if (parseStringToLong(chatID) < 0) {
                botUsername = Paper.book().read<String>("bot_username", "").toString()
                if (botUsername.isEmpty()) {
                    while (me()) {
                        writeLog(
                            applicationContext,
                            "Failed to get bot Username, Wait 5 seconds and try again."
                        )
                        try {
                            Thread.sleep(5000)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                    }
                }
                Log.i(TAG, "run: The Bot Username is loaded. The Bot Username is: $botUsername")
            }
            while (true) {
                val timeout = 60
                val httpTimeout = 65
                val okhttpClientNew = okhttpClient.newBuilder()
                    .readTimeout(httpTimeout.toLong(), TimeUnit.SECONDS)
                    .writeTimeout(httpTimeout.toLong(), TimeUnit.SECONDS)
                    .build()
                val requestUri = getUrl(
                    botToken, "getUpdates"
                )
                val requestBody = pollingJson()
                requestBody.offset = offset
                requestBody.timeout = timeout
                if (firstRequest) {
                    requestBody.timeout = 0
                    Log.d(TAG, "run: first_request")
                }
                val body: RequestBody = RequestBody.create(Const.JSON, Gson().toJson(requestBody))
                val request: Request =
                    Request.Builder().url(requestUri).method("POST", body).build()
                val call = okhttpClientNew.newCall(request)
                var response: Response
                try {
                    response = call.execute()
                } catch (e: IOException) {
                    e.printStackTrace()
                    if (!checkNetworkStatus(applicationContext)) {
                        writeLog(applicationContext, "No network connections available. ")
                        break
                    }
                    val sleepTime = 5
                    writeLog(
                        applicationContext,
                        "Connection to the Telegram API service failed, try again after $sleepTime seconds."
                    )
                    try {
                        Thread.sleep(sleepTime * 1000L)
                    } catch (e1: InterruptedException) {
                        e1.printStackTrace()
                    }
                    continue
                }
                if (response.code == 200) {
                    var result: String
                    try {
                        result = response.body.string()
                    } catch (e: IOException) {
                        e.printStackTrace()
                        continue
                    }
                    Log.d(TAG, "run: $result")
                    val resultObj = JsonParser.parseString(result).asJsonObject
                    if (resultObj["ok"].asBoolean) {
                        val resultArray = resultObj["result"].asJsonArray
                        for (item in resultArray) {
                            receiveHandle(item.asJsonObject, firstRequest)
                        }
                        firstRequest = false
                    }
                } else {
                    writeLog(applicationContext, "response code:" + response.code)
                    if (response.code == 401) {
                        var result: String
                        try {
                            result = Objects.requireNonNull(response.body).string()
                        } catch (e: IOException) {
                            e.printStackTrace()
                            continue
                        }
                        val resultObj = JsonParser.parseString(result).asJsonObject
                        val resultMessage = """
                            ${getString(R.string.system_message_head)}
                            ${getString(R.string.error_stop_message)}
                            ${getString(R.string.error_message_head)}${resultObj["description"].asString}
                            Code: ${response.code}
                            """.trimIndent()
                        sendFallbackSMS(applicationContext, resultMessage, -1)
                        stopAllService(applicationContext)
                        break
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "chat_command"

        //Global counter
        private var offset: Long = 0
        private var sendSmsNextStatus = SEND_SMS_STATUS.STANDBY_STATUS
        private var firstRequest = true


        private fun checkCellularNetworkType(
            type: Int,
            sharedPreferences: SharedPreferences?
        ): String {
            var netType = "Unknown"
            when (type) {
                TelephonyManager.NETWORK_TYPE_NR -> netType = "NR"
                TelephonyManager.NETWORK_TYPE_LTE -> {
                    netType = "LTE"
                    if (sharedPreferences!!.getBoolean("root", false)) {
                        if (isLTECA) {
                            netType += "+"
                        }
                        if (isNRConnected) {
                            netType += " & NR"
                        }
                        if (isNRStandby) {
                            netType += " (NR Standby)"
                        }
                    }
                }

                TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_TD_SCDMA, TelephonyManager.NETWORK_TYPE_UMTS -> netType =
                    "3G"

                TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> netType =
                    "2G"
            }
            return netType
        }


        private var thread_main: Thread? = null
        fun getBatteryInfo(context: Context): String {
            val batteryManager =
                checkNotNull(context.getSystemService(BATTERY_SERVICE) as BatteryManager)
            var batteryLevel =
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (batteryLevel > 100) {
                Log.i(
                    "get_battery_info",
                    "The previous battery is over 100%, and the correction is 100%."
                )
                batteryLevel = 100
            }
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = checkNotNull(context.registerReceiver(null, intentFilter))
            val chargeStatus = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val batteryStringBuilder = StringBuilder().append(batteryLevel).append("%")
            when (chargeStatus) {
                BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> batteryStringBuilder.append(
                    " ("
                ).append(context.getString(R.string.charging)).append(")")

                BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> when (batteryStatus.getIntExtra(
                    BatteryManager.EXTRA_PLUGGED,
                    -1
                )) {
                    BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_PLUGGED_USB, BatteryManager.BATTERY_PLUGGED_WIRELESS -> batteryStringBuilder.append(
                        " ("
                    ).append(context.getString(R.string.not_charging)).append(")")
                }
            }
            return batteryStringBuilder.toString()
        }

        fun getNetworkType(context: Context, sharedPreferences: SharedPreferences): String {
            var netType = "Unknown"
            val connectManager =
                checkNotNull(context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
            val telephonyManager = checkNotNull(
                context
                    .getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            )
            val networks = connectManager.allNetworks
            for (network in networks) {
                val networkCapabilities =
                    checkNotNull(connectManager.getNetworkCapabilities(network))
                if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        netType = "WIFI"
                        break
                    }
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_PHONE_STATE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            Log.i("get_network_type", "No permission.")
                            return netType
                        }
                        netType = checkCellularNetworkType(
                            telephonyManager.dataNetworkType,
                            sharedPreferences
                        )
                    }
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                        netType = "Bluetooth"
                    }
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        netType = "Ethernet"
                    }
                }
            }

            return netType
        }
    }
}

