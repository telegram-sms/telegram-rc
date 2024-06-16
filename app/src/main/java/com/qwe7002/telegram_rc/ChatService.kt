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
@Suppress("DEPRECATION")
class ChatService : Service() {
    // global object
    private lateinit var okhttpClient: OkHttpClient
    private lateinit var messageThreadId: String
    private lateinit var broadcastReceiver: broadcast_receiver
    private lateinit var wakelock: WakeLock
    private lateinit var wifilock: WifiLock
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var callback: network_callback
    private lateinit var botUsername: String
    private var privacyMode = false
    private lateinit var chatId: String
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
        wifilock.release()
        wakelock.release()
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
        requestBody.chatId = chatId
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
        if (messageType == "callback_query" && send_sms_next_status != SEND_SMS_STATUS.STANDBY_STATUS) {
            var slot = Paper.book("send_temp").read("slot", -1)!!
            val messageId = Paper.book("send_temp").read("message_id", -1L)!!
            val to = Paper.book("send_temp").read("to", "")
            val content = Paper.book("send_temp").read("content", "")
            if (callbackData != CALLBACK_DATA_VALUE.SEND) {
                set_sms_send_status_standby()
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
                val okhttpClient1 = getOkhttpObj(
                    sharedPreferences.getBoolean("doh_switch", true)
                )
                val request: Request =
                    Request.Builder().url(requestUri).method("POST", body).build()
                val call = okhttpClient1.newCall(request)
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
            set_sms_send_status_standby()
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
        val from_id = fromObj["id"].asString
        if (chatId != from_id) {
            writeLog(applicationContext, "Chat ID[$from_id] not allow")
            return
        }

        var command = ""
        var command_bot_username = ""
        var request_msg = ""
        if (messageObj.has("text")) {
            request_msg = messageObj["text"].asString
        }
        if (messageObj.has("reply_to_message")) {
            val save_item = Paper.book().read<smsRequestInfo>(
                messageObj["reply_to_message"].asJsonObject["message_id"].asString,
                null
            )
            if (save_item != null && !request_msg.isEmpty()) {
                val phone_number = save_item.phone
                val card_slot = save_item.card
                send_sms_next_status = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS
                Paper.book("send_temp").write("slot", card_slot)
                Paper.book("send_temp").write("to", phone_number)
                Paper.book("send_temp").write("content", request_msg)
            }
        }
        var has_command = false
        if (messageObj.has("entities")) {
            val temp_command: String
            val temp_command_lowercase: String
            val entities_arr = messageObj["entities"].asJsonArray
            val entities_obj_command = entities_arr[0].asJsonObject
            if (entities_obj_command["type"].asString == "bot_command") {
                has_command = true
                val command_offset = entities_obj_command["offset"].asInt
                val command_end_offset = command_offset + entities_obj_command["length"].asInt
                temp_command =
                    request_msg.substring(command_offset, command_end_offset).trim { it <= ' ' }
                temp_command_lowercase =
                    temp_command.lowercase(Locale.getDefault()).replace("_", "")
                command = temp_command_lowercase
                if (temp_command_lowercase.contains("@")) {
                    val command_at_location = temp_command_lowercase.indexOf("@")
                    command = temp_command_lowercase.substring(0, command_at_location)
                    command_bot_username = temp_command.substring(command_at_location + 1)
                }
            }
        }

        if (!messageTypeIsPrivate && privacyMode && command_bot_username != botUsername) {
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
                val spam_list = Paper.book().read("spam_sms_list", ArrayList<String>())!!
                if (spam_list.isNotEmpty()) {
                    spamCount = """
                        
                        ${getString(R.string.spam_count_title)}${spam_list.size}
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
                val ap_status = isHotspotActive(applicationContext)
                var result_ap: String
                if (!ap_status) {
                    result_ap =
                        getString(R.string.enable_wifi) + applicationContext.getString(R.string.action_success)
                    val command_list_data =
                        request_msg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    var tether_mode = TetherManager.TetherMode.TETHERING_WIFI
                    if (command_list_data.size == 2) {
                        tether_mode =
                            when (command_list_data[1].lowercase(Locale.getDefault())) {
                                "bluetooth" -> TetherManager.TetherMode.TETHERING_BLUETOOTH
                                "ncm" -> TetherManager.TetherMode.TETHERING_NCM
                                "usb" -> TetherManager.TetherMode.TETHERING_USB
                                "nic" -> TetherManager.TetherMode.TETHERING_ETHERNET
                                "wigig" -> TetherManager.TetherMode.TETHERING_WIGIG
                                else -> tether_mode
                            }
                    }
                    Paper.book("temp").write("tether_mode", tether_mode)
                    enableHotspot(applicationContext, tether_mode)
                } else {
                    Paper.book("temp").write("tether_open", false)
                    result_ap =
                        getString(R.string.disable_wifi) + applicationContext.getString(R.string.action_success)
                }
                result_ap += """
     
     ${applicationContext.getString(R.string.current_battery_level)}
     """.trimIndent() + getBatteryInfo(
                    applicationContext
                ) + "\n" + getString(R.string.current_network_connection_status) + getNetworkType(
                    applicationContext, sharedPreferences
                )
                requestBody.text = """
                        ${getString(R.string.system_message_head)}
                        $result_ap
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
                    checkNotNull(wifiManager)
                    val wifi_open =
                        Paper.book("temp").read("wifi_open", wifiManager.isWifiEnabled)!!
                    var result_vpn_ap: String
                    if (!wifi_open) {
                        result_vpn_ap =
                            getString(R.string.enable_wifi) + applicationContext.getString(R.string.action_success)
                        Thread { enableVPNHotspot(wifiManager) }.start()
                    } else {
                        Paper.book("temp").write("wifi_open", false)
                        result_vpn_ap =
                            getString(R.string.disable_wifi) + applicationContext.getString(R.string.action_success)
                    }
                    result_vpn_ap += """
     
     ${applicationContext.getString(R.string.current_battery_level)}
     """.trimIndent() + getBatteryInfo(
                        applicationContext
                    ) + "\n" + getString(R.string.current_network_connection_status) + getNetworkType(
                        applicationContext, sharedPreferences
                    )

                    requestBody.text = """
                    ${getString(R.string.system_message_head)}
                    $result_vpn_ap
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
                    val result_data = applicationContext.getString(R.string.switch_data)

                    requestBody.text = """
                    ${getString(R.string.system_message_head)}
                    $result_data
                    """.trimIndent()
                }
            }

            "/sendussd", "/sendussd1", "/sendussd2" -> {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    var sub_id = -1
                    if (getActiveCard(applicationContext) == 2) {
                        if (command == "/sendussd2") {
                            sub_id = getSubId(applicationContext, 1)
                        }
                    }
                    val command_list =
                        request_msg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    if (command_list.size == 2) {
                        sendUssd(applicationContext, command_list[1], sub_id)
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
                val spam_sms_list = Paper.book().read("spam_sms_list", ArrayList<String>())!!
                if (spam_sms_list.isEmpty()) {
                    requestBody.text = """
                        ${applicationContext.getString(R.string.system_message_head)}
                        ${getString(R.string.no_spam_history)}
                        """.trimIndent()
                } else {
                    Thread {
                        if (checkNetworkStatus(applicationContext)) {
                            val okhttp_client = getOkhttpObj(
                                sharedPreferences.getBoolean("doh_switch", true)
                            )
                            for (item in spam_sms_list) {
                                val send_sms_request_body = requestMessage()
                                send_sms_request_body.chatId = chatId
                                send_sms_request_body.text = item
                                val request_uri = getUrl(
                                    botToken, "sendMessage"
                                )
                                val request_body_json = Gson().toJson(send_sms_request_body)
                                val body: RequestBody =
                                    request_body_json.toRequestBody(Const.JSON)
                                val request_obj: Request =
                                    Request.Builder().url(request_uri).method("POST", body).build()
                                val call = okhttp_client.newCall(request_obj)
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
                                val resend_list_local =
                                    Paper.book().read("spam_sms_list", ArrayList<String>())!!
                                resend_list_local.remove(item)
                                Paper.book().write("spam_sms_list", resend_list_local)
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
                    val command_list =
                        request_msg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    if (command_list.size == 2) {
                        Paper.book("system_config").write("dummy_ip_addr", command_list[1])
                        addDummyDevice(command_list[1])
                    } else {
                        if (Paper.book("system_config").contains("dummy_ip_addr")) {
                            val dummy_ip_addr =
                                Paper.book("system_config").read<String>("dummy_ip_addr")
                            addDummyDevice(dummy_ip_addr!!)
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
                val msg_send_list =
                    request_msg.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (msg_send_list.size > 2) {
                    val msg_send_to = getSendPhoneNumber(
                        msg_send_list[1]
                    )
                    if (isPhoneNumber(msg_send_to)) {
                        val msg_send_content = StringBuilder()
                        var i = 2
                        while (i < msg_send_list.size) {
                            if (msg_send_list.size != 3 && i != 2) {
                                msg_send_content.append("\n")
                            }
                            msg_send_content.append(msg_send_list[i])
                            ++i
                        }
                        if (getActiveCard(applicationContext) == 1) {
                            sendSMS(
                                applicationContext,
                                msg_send_to,
                                msg_send_content.toString(),
                                -1,
                                -1
                            )
                            return
                        }
                        var send_slot = -1
                        if (getActiveCard(applicationContext) > 1) {
                            send_slot = 0
                            if (command == "/sendsms2") {
                                send_slot = 1
                            }
                        }
                        val sub_id = getSubId(
                            applicationContext, send_slot
                        )
                        if (sub_id != -1) {
                            sendSMS(
                                applicationContext,
                                msg_send_to,
                                msg_send_content.toString(),
                                send_slot,
                                sub_id
                            )
                            return
                        }
                    }
                } else {
                    has_command = false
                    send_sms_next_status = SEND_SMS_STATUS.PHONE_INPUT_STATUS
                    var send_slot = -1
                    if (getActiveCard(applicationContext) > 1) {
                        send_slot = 0
                        if (command == "/sendsms2") {
                            send_slot = 1
                        }
                    }
                    Paper.book("send_temp").write("slot", send_slot)
                }
                requestBody.text = """
                    [${applicationContext.getString(R.string.send_sms_head)}]
                    ${getString(R.string.failed_to_get_information)}
                    """.trimIndent()
            }

            else -> {
                if (!messageTypeIsPrivate && send_sms_next_status == -1) {
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
        if (has_command) {
            set_sms_send_status_standby()
        }
        if (!has_command && send_sms_next_status != -1) {
            Log.i(TAG, "receive_handle: Enter the interactive SMS sending mode.")
            var dual_sim = ""
            val send_slot_temp = Paper.book("send_temp").read("slot", -1)!!
            if (send_slot_temp != -1) {
                dual_sim = "SIM" + (send_slot_temp + 1) + " "
            }
            val head = "[" + dual_sim + applicationContext.getString(R.string.send_sms_head) + "]"
            var result_send = getString(R.string.failed_to_get_information)
            Log.d(TAG, "Sending mode status: " + send_sms_next_status)

            when (send_sms_next_status) {
                SEND_SMS_STATUS.PHONE_INPUT_STATUS -> {
                    send_sms_next_status = SEND_SMS_STATUS.MESSAGE_INPUT_STATUS
                    result_send = getString(R.string.enter_number)
                }

                SEND_SMS_STATUS.MESSAGE_INPUT_STATUS -> {
                    val temp_to = getSendPhoneNumber(request_msg)
                    if (isPhoneNumber(temp_to)) {
                        Paper.book("send_temp").write("to", temp_to)
                        result_send = getString(R.string.enter_content)
                        send_sms_next_status = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS
                    } else {
                        set_sms_send_status_standby()
                        result_send = getString(R.string.unable_get_phone_number)
                    }
                }

                SEND_SMS_STATUS.WAITING_TO_SEND_STATUS -> {
                    Paper.book("send_temp").write("content", request_msg)
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
                    result_send = """
                        ${applicationContext.getString(R.string.to)}${
                        Paper.book("send_temp").read<Any>("to")
                    }
                        ${applicationContext.getString(R.string.content)}${
                        Paper.book("send_temp").read("content", "")
                    }
                        """.trimIndent()
                    send_sms_next_status = SEND_SMS_STATUS.SEND_STATUS
                }
            }
            requestBody.text = """
                $head
                $result_send
                """.trimIndent()
        }

        val request_uri = getUrl(botToken, "sendMessage")
        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(Const.JSON)
        Log.d(TAG, "receive_handle: " + Gson().toJson(requestBody))
        val send_request: Request = Request.Builder().url(request_uri).method("POST", body).build()
        val call = okhttpClient.newCall(send_request)
        val error_head = "Send reply failed:"
        val final_has_command = has_command
        val final_command = command
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                writeLog(applicationContext, error_head + e.message)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                checkNotNull(response.body)
                val response_string = Objects.requireNonNull(response.body).string()
                if (response.code != 200) {
                    writeLog(applicationContext, error_head + response.code + " " + response_string)
                    return
                }
                val splite_command_value = final_command.replace("_", "")
                if (!final_has_command && send_sms_next_status == SEND_SMS_STATUS.SEND_STATUS) {
                    Paper.book("send_temp").write("message_id", getMessageId(response_string))
                }

                if (splite_command_value == "/hotspot" || splite_command_value == "/vpnhotspot") {
                    if (!Paper.book("temp").read("tether_open", false)!!) {
                        disableHotspot(
                            applicationContext,
                            Paper.book("temp")
                                .read("tether_mode", TetherManager.TetherMode.TETHERING_WIFI)!!
                        )
                        Paper.book("temp").delete("tether_mode")
                    }
                }
                if (final_has_command && sharedPreferences.getBoolean("root", false)) {
                    when (splite_command_value) {
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

    private fun set_sms_send_status_standby() {
        send_sms_next_status = SEND_SMS_STATUS.STANDBY_STATUS
        Paper.book("send_temp").destroy()
    }

    @SuppressLint("InvalidWakeLockTag", "WakelockTimeout", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        connectivityManager =
            applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        Paper.init(applicationContext)
        set_sms_send_status_standby()
        sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)

        chatId = sharedPreferences.getString("chat_id", "").toString()
        botToken = sharedPreferences.getString("bot_token", "").toString()
        messageThreadId = sharedPreferences.getString("message_thread_id", "").toString()
        okhttpClient = getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true))
        privacyMode = sharedPreferences.getBoolean("privacy_mode", false)
        wifilock = (Objects.requireNonNull(
            applicationContext.getSystemService(
                WIFI_SERVICE
            )
        ) as WifiManager).createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "bot_command_polling_wifi"
        )
        wakelock =
            (Objects.requireNonNull(applicationContext.getSystemService(POWER_SERVICE)) as PowerManager).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "bot_command_polling"
            )
        wifilock.setReferenceCounted(false)
        wakelock.setReferenceCounted(false)
        if (!wifilock.isHeld()) {
            wifilock.acquire()
        }
        if (!wakelock.isHeld()) {
            wakelock.acquire()
        }
        thread_main = Thread(thread_main_runnable())
        thread_main!!.start()
        val intentFilter = IntentFilter()
        intentFilter.addAction(Const.BROADCAST_STOP_SERVICE)
        val network_request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        callback = network_callback()
        connectivityManager.registerNetworkCallback(network_request, callback)
        broadcastReceiver = broadcast_receiver()
        registerReceiver(broadcastReceiver, intentFilter)
    }

    private fun when_network_change() {
        if (checkNetworkStatus(applicationContext)) {
            if (!thread_main!!.isAlive) {
                writeLog(applicationContext, "Network connections has been restored.")
                thread_main = Thread(thread_main_runnable())
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

    private inner class broadcast_receiver : BroadcastReceiver() {
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

    private inner class network_callback : NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "onAvailable")
            when_network_change()
        }
    }

    internal inner class thread_main_runnable : Runnable {
        override fun run() {
            Log.d(TAG, "run: thread main start")
            if (parseStringToLong(chatId) < 0) {
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
                val http_timeout = 65
                val okhttp_client_new = okhttpClient.newBuilder()
                    .readTimeout(http_timeout.toLong(), TimeUnit.SECONDS)
                    .writeTimeout(http_timeout.toLong(), TimeUnit.SECONDS)
                    .build()
                val request_uri = getUrl(
                    botToken, "getUpdates"
                )
                val request_body = pollingJson()
                request_body.offset = offset
                request_body.timeout = timeout
                if (first_request) {
                    request_body.timeout = 0
                    Log.d(TAG, "run: first_request")
                }
                val body: RequestBody = RequestBody.create(Const.JSON, Gson().toJson(request_body))
                val request: Request =
                    Request.Builder().url(request_uri).method("POST", body).build()
                val call = okhttp_client_new.newCall(request)
                var response: Response
                try {
                    response = call.execute()
                } catch (e: IOException) {
                    e.printStackTrace()
                    if (!checkNetworkStatus(applicationContext)) {
                        writeLog(applicationContext, "No network connections available. ")
                        break
                    }
                    val sleep_time = 5
                    writeLog(
                        applicationContext,
                        "Connection to the Telegram API service failed, try again after $sleep_time seconds."
                    )
                    try {
                        Thread.sleep(sleep_time * 1000L)
                    } catch (e1: InterruptedException) {
                        e1.printStackTrace()
                    }
                    continue
                }
                if (response.code == 200) {
                    checkNotNull(response.body)
                    var result: String
                    try {
                        result = Objects.requireNonNull(response.body).string()
                    } catch (e: IOException) {
                        e.printStackTrace()
                        continue
                    }
                    Log.d(TAG, "run: $result")
                    val result_obj = JsonParser.parseString(result).asJsonObject
                    if (result_obj["ok"].asBoolean) {
                        val result_array = result_obj["result"].asJsonArray
                        for (item in result_array) {
                            receiveHandle(item.asJsonObject, first_request)
                        }
                        first_request = false
                    }
                } else {
                    writeLog(applicationContext, "response code:" + response.code)
                    if (response.code == 401) {
                        checkNotNull(response.body)
                        var result: String
                        try {
                            result = Objects.requireNonNull(response.body).string()
                        } catch (e: IOException) {
                            e.printStackTrace()
                            continue
                        }
                        val result_obj = JsonParser.parseString(result).asJsonObject
                        val result_message = """
                            ${getString(R.string.system_message_head)}
                            ${getString(R.string.error_stop_message)}
                            ${getString(R.string.error_message_head)}${result_obj["description"].asString}
                            Code: ${response.code}
                            """.trimIndent()
                        sendFallbackSMS(applicationContext, result_message, -1)
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
        private var send_sms_next_status = SEND_SMS_STATUS.STANDBY_STATUS
        private var first_request = true


        private fun checkCellularNetworkType(
            type: Int,
            sharedPreferences: SharedPreferences?
        ): String {
            var net_type = "Unknown"
            when (type) {
                TelephonyManager.NETWORK_TYPE_NR -> net_type = "NR"
                TelephonyManager.NETWORK_TYPE_LTE -> {
                    net_type = "LTE"
                    if (sharedPreferences!!.getBoolean("root", false)) {
                        if (isLTECA) {
                            net_type += "+"
                        }
                        if (isNRConnected) {
                            net_type += " & NR"
                        }
                        if (isNRStandby) {
                            net_type += " (NR Standby)"
                        }
                    }
                }

                TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_TD_SCDMA, TelephonyManager.NETWORK_TYPE_UMTS -> net_type =
                    "3G"

                TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> net_type =
                    "2G"
            }
            return net_type
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
            val intentfilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = checkNotNull(context.registerReceiver(null, intentfilter))
            val charge_status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val batteryStringBuilder = StringBuilder().append(batteryLevel).append("%")
            when (charge_status) {
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

        fun getNetworkType(context: Context, sharedPreferences: SharedPreferences?): String {
            var net_type = "Unknown"
            val connect_manager =
                checkNotNull(context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
            val telephonyManager = checkNotNull(
                context
                    .getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            )
            val networks = connect_manager.allNetworks
            for (network in networks) {
                val network_capabilities =
                    checkNotNull(connect_manager.getNetworkCapabilities(network))
                if (!network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        net_type = "WIFI"
                        break
                    }
                    if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_PHONE_STATE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            Log.i("get_network_type", "No permission.")
                            return net_type
                        }
                        net_type = checkCellularNetworkType(
                            telephonyManager.dataNetworkType,
                            sharedPreferences
                        )
                    }
                    if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                        net_type = "Bluetooth"
                    }
                    if (network_capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        net_type = "Ethernet"
                    }
                }
            }

            return net_type
        }
    }
}

