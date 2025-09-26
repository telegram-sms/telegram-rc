package com.qwe7002.telegram_rc

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.fitc.wifihotspot.TetherManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.qwe7002.telegram_rc.data_structure.PollingJson
import com.qwe7002.telegram_rc.data_structure.ReplyMarkupKeyboard
import com.qwe7002.telegram_rc.data_structure.ReplyMarkupKeyboard.InlineKeyboardButton
import com.qwe7002.telegram_rc.data_structure.ReplyMarkupKeyboard.KeyboardMarkup
import com.qwe7002.telegram_rc.data_structure.RequestMessage
import com.qwe7002.telegram_rc.data_structure.SMSRequestInfo
import com.qwe7002.telegram_rc.shizuku_kit.Networks.setData
import com.qwe7002.telegram_rc.shizuku_kit.Networks.setWifi
import com.qwe7002.telegram_rc.shizuku_kit.VPNHotspot
import com.qwe7002.telegram_rc.shizuku_kit.ISub
import com.qwe7002.telegram_rc.shizuku_kit.Telephony
import com.qwe7002.telegram_rc.static_class.ArfcnConverter
import com.qwe7002.telegram_rc.static_class.Battery
import com.qwe7002.telegram_rc.static_class.Const
import com.qwe7002.telegram_rc.static_class.DataUsage
import com.qwe7002.telegram_rc.static_class.LogManage.readLog
import com.qwe7002.telegram_rc.static_class.LogManage.writeLog
import com.qwe7002.telegram_rc.static_class.Network
import com.qwe7002.telegram_rc.static_class.Network.checkNetworkStatus
import com.qwe7002.telegram_rc.static_class.Network.getDataEnable
import com.qwe7002.telegram_rc.static_class.Network.getOkhttpObj
import com.qwe7002.telegram_rc.static_class.Network.getUrl
import com.qwe7002.telegram_rc.static_class.Network.requestUpdatedCellInfo
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
import com.qwe7002.telegram_rc.static_class.Phone
import com.qwe7002.telegram_rc.static_class.RemoteControl.disableHotspot
import com.qwe7002.telegram_rc.static_class.RemoteControl.enableHotspot
import com.qwe7002.telegram_rc.static_class.RemoteControl.isHotspotActive
import com.qwe7002.telegram_rc.static_class.SMS.sendFallbackSMS
import com.qwe7002.telegram_rc.static_class.SMS.sendSMS
import com.qwe7002.telegram_rc.static_class.ServiceManage
import com.qwe7002.telegram_rc.static_class.ServiceManage.stopAllService
import com.qwe7002.telegram_rc.static_class.USSD.sendUssd
import com.tencent.mmkv.MMKV
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rikka.shizuku.Shizuku
import java.io.IOException
import java.util.Locale
import java.util.Objects
import java.util.concurrent.TimeUnit


@Suppress("DEPRECATION", "ClassName", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class ChatService : Service() {
    // global object
    private lateinit var okhttpClient: OkHttpClient
    private lateinit var messageThreadId: String
    private lateinit var wakeLock: WakeLock
    private lateinit var wifiLock: WifiLock
    private lateinit var preferences: MMKV
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var chatID: String
    private lateinit var botToken: String
    private lateinit var statusMMKV: MMKV
    private lateinit var chatInfoMMKV: MMKV
    private lateinit var sendStatusMMKV: MMKV
    private lateinit var mainThread: Thread
    private var terminalThread = false


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = getNotificationObj(
            applicationContext, getString(R.string.chat_command_service_name)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notify.CHAT_COMMAND,
                notification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(Notify.CHAT_COMMAND, notification.build())
        }
        return START_STICKY
    }


    override fun onDestroy() {
        wifiLock.release()
        wakeLock.release()
        terminalThread = true
        if (mainThread.isAlive) {
            mainThread.interrupt()
        }
        stopForeground(true)
        super.onDestroy()
    }


    override fun onBind(intent: Intent): IBinder? {
        return null
    }


    private fun receiveHandle(resultObj: JsonObject) {
        val updateId = resultObj["update_id"].asLong
        chatInfoMMKV.putLong("offset", updateId + 1)
        var messageType = ""
        val requestBody = RequestMessage()
        requestBody.chatId = chatID
        requestBody.messageThreadId = messageThreadId
        var messageObj: JsonObject? = null
        var callbackData: String? = null
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

        // 提前返回，避免后续处理空对象
        if (messageObj == null && messageType != "callback_query") {
            Log.i(TAG, "receive_handle: message object is null and not a callback query")
            return
        }

        if (messageType == "callback_query" && sendStatusMMKV.getInt(
                "status",
                SEND_SMS_STATUS.STANDBY_STATUS
            ) != SEND_SMS_STATUS.STANDBY_STATUS
        ) {
            // 确保callbackData已初始化
            if (callbackData == null) {
                Log.e(TAG, "Callback data is null")
                return
            }
            val slot = sendStatusMMKV.getInt("slot", -1)
            val messageId = sendStatusMMKV.getLong("message_id", -1L)
            val to = sendStatusMMKV.getString("to", "")
            val content = sendStatusMMKV.getString("content", "")
            if (callbackData != CALLBACK_DATA_VALUE.SEND) {
                setSmsSendStatusStandby()
                val requestUri = getUrl(
                    botToken, "editMessageText"
                )
                val dualSim = getDualSimCardDisplay(
                    applicationContext,
                    slot,
                    preferences.getBoolean("display_dual_sim_display_name", false)
                )
                val sendContent =
                    "[$dualSim${applicationContext.getString(R.string.send_sms_head)}]\n${
                        applicationContext.getString(R.string.to)
                    }$to\n${applicationContext.getString(R.string.content)}$content"
                requestBody.text = "$sendContent\n${applicationContext.getString(R.string.status)}${
                    applicationContext.getString(R.string.cancel_button)
                }"
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
                sendStatusMMKV.putInt("slot", -1)
            } else {
                subId = getSubId(applicationContext, slot)
            }
            sendSMS(applicationContext, to!!, content!!, slot, subId, messageId)
            setSmsSendStatusStandby()
            return
        }

        var fromObj: JsonObject? = null
        var fromTopicId = ""
        val messageTypeIsPrivate = messageType == "private"
        if (messageObj != null && messageObj.has("from")) {
            fromObj = messageObj["from"].asJsonObject
            if (!messageTypeIsPrivate && fromObj["is_bot"].asBoolean) {
                Log.i(TAG, "receive_handle: receive from bot.")
                return
            }
        }
        if (messageThreadId != "") {
            if (messageObj != null && messageObj.has("is_topic_message")) {
                fromTopicId = messageObj["message_thread_id"].asString
            }
            if (messageThreadId != fromTopicId) {
                Log.i(TAG, "Topic ID[$fromTopicId] not allow.")
                return
            }
        }
        if (messageObj != null && messageObj.has("chat")) {
            fromObj = messageObj["chat"].asJsonObject
        }

        if (fromObj == null) {
            Log.e(TAG, "From object is null")
            return
        }

        val fromId = fromObj["id"].asString
        if (chatID != fromId) {
            writeLog(applicationContext, "Chat ID[$fromId] not allow")
            return
        }

        var command = ""
        var requestMsg = ""
        if (messageObj != null && messageObj.has("text")) {
            requestMsg = messageObj["text"].asString
        }
        if (messageObj != null && messageObj.has("reply_to_message")) {
            val saveItemString = chatInfoMMKV.getString(
                messageObj["reply_to_message"].asJsonObject["message_id"].asString,
                null
            )
            val saveItem =
                Gson().fromJson(saveItemString, SMSRequestInfo::class.java)

            if (saveItem != null && requestMsg.isNotEmpty()) {
                val phoneNumber = saveItem.phone
                val cardSlot = saveItem.card
                sendStatusMMKV.putInt("status", SEND_SMS_STATUS.WAITING_TO_SEND_STATUS)
                sendStatusMMKV.putInt("slot", cardSlot)
                sendStatusMMKV.putString("to", phoneNumber)
                sendStatusMMKV.putString("content", requestMsg)
            }
        }
        var hasCommand = false
        if (messageObj != null && messageObj.has("entities")) {
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
                }
            }
        }
        Log.d(TAG, "Command: $command")

        when (command) {
            "/help", "/start", "/commandlist" -> {
                var smsCommand = "\n${getString(R.string.sendsms)}"
                if (getActiveCard(applicationContext) == 2) {
                    smsCommand = "\n${getString(R.string.sendsms_dual)}"
                }
                smsCommand += "\n${getString(R.string.get_spam_sms)}"

                var ussdCommand = ""
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    ussdCommand = "\n${getString(R.string.send_ussd_command)}"
                    if (getActiveCard(applicationContext) == 2) {
                        ussdCommand = "\n${getString(R.string.send_ussd_dual_command)}"
                    }
                }
                var switchAp = ""
                if (Settings.System.canWrite(applicationContext)) {
                    switchAp += "\n${getString(R.string.switch_ap_message)}"
                }

                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    if (VPNHotspot.isVPNHotspotExist(applicationContext)) {
                        switchAp += "\n${
                            getString(R.string.switch_ap_message).replace(
                                "/hotspot",
                                "/vpnhotspot"
                            )
                        }"
                    }
                    switchAp += "\n${getString(R.string.switch_data_message)}"
                }
                if (command == "/commandlist") {
                    requestBody.text =
                        (getString(R.string.available_command) + smsCommand + ussdCommand + switchAp).replace(
                            "/",
                            ""
                        )
                } else {
                    val resultString =
                        "${getString(R.string.system_message_head)}\n${getString(R.string.available_command)}$smsCommand$ussdCommand$switchAp"
                    requestBody.text = resultString
                }
            }

            "/ping", "/getinfo" -> {
                var cardInfo = ""
                val networkType = Network.getNetworkType(
                    applicationContext
                )
                val telephonyManager =
                    applicationContext.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.READ_PHONE_STATE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    cardInfo = if (getActiveCard(applicationContext) == 2) {
                        val subId1 = getSubId(applicationContext, 0)
                        val subId2 = getSubId(applicationContext, 1)
                        val tm1 = telephonyManager.createForSubscriptionId(subId1)
                        val tm2 = telephonyManager.createForSubscriptionId(subId2)
                        val sim1Display = getSimDisplayName(applicationContext, 0)
                        val sim2Display = getSimDisplayName(applicationContext, 1)
                        var sim1Info: String
                        var sim2Info: String
                        if (sim1Display == tm1.simOperatorName) {
                            sim1Info = telephonyManager.simOperatorName
                        } else {
                            sim1Info =
                                telephonyManager.simOperatorName + "\nSIM1 Alias: " + sim1Display
                        }
                        if (sim2Display == tm2.simOperatorName) {
                            sim2Info = telephonyManager.simOperatorName
                        } else {
                            sim2Info =
                                telephonyManager.simOperatorName + "\nSIM2 Alias: " + sim2Display
                        }
                        if (ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.READ_PHONE_STATE
                            ) == PackageManager.PERMISSION_GRANTED &&
                            ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.READ_PHONE_NUMBERS
                            ) == PackageManager.PERMISSION_GRANTED &&
                            ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.READ_SMS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val phone1Number = Phone.getPhoneNumber(applicationContext, 0)
                            sim1Info += " ($phone1Number)"
                            val phone2Number = Phone.getPhoneNumber(applicationContext, 1)
                            sim2Info += " ($phone2Number)"
                            val imsiCache = MMKV.mmkvWithID(Const.IMSI_MMKV_ID)
                            val phone1DataUsage = DataUsage.getDataUsageForSim(
                                applicationContext,
                                imsiCache.getString("0", null)
                            )
                            val phone2DataUsage = DataUsage.getDataUsageForSim(
                                applicationContext,
                                imsiCache.getString("1", null)
                            )
                            sim1Info += "\nSIM1 Data Usage: $phone1DataUsage"
                            sim2Info += "\nSIM2 Data Usage: $phone2DataUsage"

                        }
                        // 获取SIM1信号信息
                        if (ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val cellInfoList1 = requestUpdatedCellInfo(applicationContext, tm1)
                            if (cellInfoList1.isNotEmpty()) {
                                val registeredCell1 = cellInfoList1.find { it.isRegistered }
                                if (registeredCell1 != null) {
                                    val cellDetails =
                                        ArfcnConverter.getCellInfoDetails(registeredCell1)
                                    sim1Info += "\nSIM1 Signal: $cellDetails"
                                }
                            }
                        }

                        // 获取SIM2信号信息
                        if (ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val cellInfoList2 = requestUpdatedCellInfo(applicationContext, tm2)
                            if (cellInfoList2.isNotEmpty()) {
                                val registeredCell2 = cellInfoList2.find { it.isRegistered }
                                if (registeredCell2 != null) {
                                    val cellDetails =
                                        ArfcnConverter.getCellInfoDetails(registeredCell2)
                                    sim2Info += "\nSIM2 Signal: $cellDetails"
                                }
                            }
                        }

                        "\n${getString(R.string.current_data_card)}: SIM" + getDataSimId(
                            applicationContext
                        ) + "\nSIM1: " + sim1Info + "\nSIM2: " + sim2Info
                    } else {
                        var simInfo = ""
                        if (ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.READ_PHONE_STATE
                            ) == PackageManager.PERMISSION_GRANTED &&
                            ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.READ_PHONE_NUMBERS
                            ) == PackageManager.PERMISSION_GRANTED &&
                            ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.READ_SMS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val simDisplayName = getSimDisplayName(
                                applicationContext,
                                getActiveCard(applicationContext)
                            )
                            if (simDisplayName == telephonyManager.simOperatorName) {
                                simInfo = telephonyManager.simOperatorName
                            } else {
                                simInfo =
                                    telephonyManager.simOperatorName + "\nSIM Alias: " + simDisplayName
                            }

                            val phone1Number = Phone.getPhoneNumber(applicationContext, 0)
                            simInfo += " ($phone1Number)"
                            val imsiCache = MMKV.mmkvWithID(Const.IMSI_MMKV_ID)
                            val phone1DataUsage = DataUsage.getDataUsageForSim(
                                applicationContext,
                                imsiCache.getString("0", null)
                            )
                            simInfo += "\nData Usage: $phone1DataUsage"
                        }
                        // 获取单卡信号信息
                        if (ActivityCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val cellInfoList = telephonyManager.allCellInfo
                            if (cellInfoList.isNotEmpty()) {
                                val registeredCell = cellInfoList.find { it.isRegistered }
                                if (registeredCell != null) {
                                    val cellDetails =
                                        ArfcnConverter.getCellInfoDetails(registeredCell)
                                    simInfo += "\nSignal: $cellDetails"
                                }
                            }
                        }

                        "\nSIM: $simInfo\nSIM Carrier: ${telephonyManager.simOperatorName}"
                    }
                }
                val spamList =
                    MMKV.mmkvWithID(Const.SPAM_MMKV_ID).decodeStringSet("sms", setOf())?.toList()
                        ?: ArrayList()
                val spamCount = "\n${getString(R.string.spam_count_title)}${spamList.size}"

                var isHotspotRunning = ""
                if (Settings.System.canWrite(applicationContext)) {
                    isHotspotRunning += "\n${getString(R.string.hotspot_status)}"
                    isHotspotRunning += if (isHotspotActive(applicationContext)) {
                        getString(R.string.enable)
                    } else {
                        getString(R.string.disable)
                    }
                }
                val beacon = MMKV.mmkvWithID(Const.BEACON_MMKV_ID);
                var beaconStatus = "\n${getString(R.string.beacon_monitoring_status)}"
                beaconStatus += if (beacon.getBoolean("beacon_enable", false)) {
                    if (!ServiceManage.hasLocationPermissions(applicationContext)) {
                        "Location Permission Missing"
                    } else {
                        getString(R.string.enable)
                    }
                } else {
                    getString(R.string.disable)
                }
                requestBody.text =
                    "${getString(R.string.system_message_head)}\n${applicationContext.getString(R.string.current_battery_level)}" + Battery.getBatteryInfo(
                        applicationContext
                    ) + "\n" + getString(R.string.current_network_connection_status) + networkType + isHotspotRunning + beaconStatus + spamCount + cardInfo
                Log.d(TAG, "getInfo: " + requestBody.text)
            }

            "/log" -> requestBody.text = getString(R.string.system_message_head) + readLog(
                applicationContext, 10
            )

            "/hotspot" -> {
                if (MMKV.mmkvWithID(Const.BEACON_MMKV_ID).getBoolean("beacon_enable", false)) {
                    requestBody.text =
                        "${getString(R.string.system_message_head)}\nAutoSwitch is enabled, please disable it first."
                } else {
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
                                    "usb" -> TetherManager.TetherMode.TETHERING_USB
                                    "nic" -> TetherManager.TetherMode.TETHERING_ETHERNET
                                    else -> TetherManager.TetherMode.TETHERING_WIFI
                                }
                        }
                        statusMMKV.putInt("tether_mode", tetherMode)
                        enableHotspot(applicationContext, tetherMode)
                        Thread.sleep(500)
                        val hotspotIp = Network.getHotspotIpAddress(tetherMode)
                        resultAp += "\nGateway IP: $hotspotIp"

                        // 如果获取到的IP是Unknown，则标记需要在消息发送后更新IP地址
                        if (hotspotIp == "Unknown") {
                            statusMMKV.putBoolean("hotspot_ip_update_needed", true)
                            statusMMKV.putInt("hotspot_tether_mode", tetherMode)
                        } else {
                            statusMMKV.putBoolean("hotspot_ip_update_needed", false)
                        }
                    } else {
                        statusMMKV.putBoolean("tether", false)
                        resultAp =
                            getString(R.string.disable_wifi) + applicationContext.getString(R.string.action_success)
                        // 清除标记
                        statusMMKV.putBoolean("hotspot_ip_update_needed", false)
                    }
                    resultAp += "\n${applicationContext.getString(R.string.current_battery_level)}" + Battery.getBatteryInfo(
                        applicationContext
                    ) + "\n" + getString(R.string.current_network_connection_status) + Network.getNetworkType(
                        applicationContext
                    )
                    requestBody.text = "${getString(R.string.system_message_head)}\n$resultAp"
                }
            }

            "/vpnhotspot" -> {
                if (MMKV.mmkvWithID(Const.BEACON_MMKV_ID).getBoolean("beacon_enable", false)) {
                    requestBody.text =
                        "${getString(R.string.system_message_head)}\nAutoSwitch is enabled, please disable it first."
                } else {
                    if ((!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) || !VPNHotspot.isVPNHotspotExist(
                            applicationContext
                        )
                    ) {
                        requestBody.text =
                            "${getString(R.string.system_message_head)}\n${getString(R.string.no_permission)}"

                    } else {
                        val wifiManager =
                            applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                        val wifiOpen = statusMMKV.getBoolean("VPNHotspot", false)
                        var resultVpnAp: String
                        if (!wifiOpen) {
                            resultVpnAp =
                                getString(R.string.enable_wifi) + applicationContext.getString(R.string.action_success)
                            Thread {
                                VPNHotspot.enableVPNHotspot(
                                    wifiManager
                                )
                            }.start()
                        } else {
                            statusMMKV.putBoolean("VPNHotspot", false)
                            resultVpnAp =
                                getString(R.string.disable_wifi) + applicationContext.getString(R.string.action_success)
                        }
                        resultVpnAp += "\n${applicationContext.getString(R.string.current_battery_level)}" + Battery.getBatteryInfo(
                            applicationContext
                        ) + "\n" + getString(R.string.current_network_connection_status) + Network.getNetworkType(
                            applicationContext
                        )

                        requestBody.text =
                            "${getString(R.string.system_message_head)}\n$resultVpnAp"
                    }
                }
            }

            "/data" -> {
                if (!Shizuku.pingBinder()) {
                    Log.e("Shizuku", "Shizuku not running")
                    requestBody.text =
                        "${getString(R.string.system_message_head)}\nShizuku not running"
                } else {
                    // 请求 Shizuku 权限
                    if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                        requestBody.text =
                            "${getString(R.string.system_message_head)}\nShizuku permission not granted"
                    } else {
                        val resultData =
                            "Switching mobile network status: " + !getDataEnable(
                                applicationContext
                            )
                        requestBody.text = "${getString(R.string.system_message_head)}\n$resultData"
                    }
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
                requestBody.text =
                    "${applicationContext.getString(R.string.system_message_head)}\n${getString(R.string.unknown_command)}"
            }

            "/getspamsms" -> {
                val spamSmsList =
                    MMKV.mmkvWithID(Const.SPAM_MMKV_ID).getStringSet("sms", setOf())?.toMutableSet()
                        ?: mutableSetOf()
                if (spamSmsList.isEmpty()) {
                    requestBody.text =
                        "${applicationContext.getString(R.string.system_message_head)}\n${
                            getString(R.string.no_spam_history)
                        }"
                } else {
                    Thread {
                        if (checkNetworkStatus(applicationContext)) {
                            for (item in spamSmsList) {
                                val sendSmsRequestBody =
                                    RequestMessage()
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
                                val reSendListLocal =
                                    MMKV.mmkvWithID(Const.SPAM_MMKV_ID).getStringSet("sms", setOf())
                                        ?.toMutableSet() ?: mutableSetOf()
                                reSendListLocal.remove(item)
                                MMKV.mmkvWithID(Const.SPAM_MMKV_ID)
                                    .putStringSet("sms", reSendListLocal)
                            }
                        }
                        writeLog(applicationContext, "Send spam message is complete.")
                    }.start()
                    return
                }
            }

            "/switch" -> {
                if (!Shizuku.pingBinder()) {
                    Log.e("Shizuku", "Shizuku not running")
                    requestBody.text =
                        "${getString(R.string.system_message_head)}\nShizuku not running"
                } else {
                    if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                        requestBody.text =
                            "${getString(R.string.system_message_head)}\nShizuku permission not granted"
                    } else {
                        val commandListData =
                            requestMsg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()

                        if (commandListData.size < 2) {
                            requestBody.text =
                                "${getString(R.string.system_message_head)}\nUsage: /switch [ autoswitch | data | wifi | sim | datacard ]\nPlease specify a switch type and action."
                        } else {
                            val switchType = commandListData[1].lowercase(Locale.getDefault())
                            var action: String? = null
                            if (commandListData.size > 2) {
                                action = commandListData[2].lowercase(Locale.getDefault())
                            }
                            var sim: String? = null
                            if (switchType == "sim" && commandListData.size > 3) {
                                sim = commandListData[3].lowercase(Locale.getDefault())

                            }

                            when (switchType) {
                                "autoswitch" -> {
                                    val beacon = MMKV.mmkvWithID(Const.BEACON_MMKV_ID)
                                    val currentState = beacon.getBoolean("beacon_enable", false)
                                    val newState = when (action) {
                                        "on" -> true
                                        "off" -> false
                                        else -> !currentState // toggle
                                    }
                                    beacon.putBoolean("beacon_enable", newState)
                                    requestBody.text =
                                        "${applicationContext.getString(R.string.system_message_head)}\nBeacon monitoring status: ${
                                            if (newState) getString(
                                                R.string.enable
                                            ) else getString(R.string.disable)
                                        }"
                                }

                                "data" -> {
                                    val dataEnable = getDataEnable(applicationContext)
                                    val newState = when (action) {
                                        "on" -> true
                                        "off" -> false
                                        else -> !dataEnable // toggle
                                    }
                                    setData(newState)
                                    requestBody.text =
                                        "${getString(R.string.system_message_head)}\nSwitching mobile network status: ${
                                            if (newState) getString(R.string.enable) else getString(
                                                R.string.disable
                                            )
                                        }"
                                }

                                "wifi" -> {
                                    val wifiManager =
                                        applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                                    val wifiEnabled = wifiManager.isWifiEnabled
                                    val newState = when (action) {
                                        "on" -> true
                                        "off" -> false
                                        else -> !wifiEnabled // toggle
                                    }
                                    setWifi(newState)
                                    requestBody.text =
                                        "${getString(R.string.system_message_head)}\nWIFI state: ${
                                            if (newState) getString(
                                                R.string.enable
                                            ) else getString(R.string.disable)
                                        }"
                                }

                                "sim" -> {
                                    val slot = sim?.toInt()?.minus(1)
                                    val newState = when (action) {
                                        "on" -> true
                                        "off" -> false
                                        else -> false
                                    }
                                    if (slot == null || slot < 0 || slot > 1) {
                                        requestBody.text =
                                            "${getString(R.string.system_message_head)}\nUsage: /switch sim [on|off] [0|1]\nPlease specify the SIM slot number."
                                    } else {
                                        val subscriptionManager =
                                            (applicationContext.getSystemService(
                                                TELEPHONY_SUBSCRIPTION_SERVICE
                                            ) as SubscriptionManager)
                                        val info = subscriptionManager.getActiveSubscriptionInfo(
                                            SubscriptionManager.getDefaultDataSubscriptionId()
                                        )
                                        if (info.simSlotIndex == slot) {
                                            //不需要切换
                                            requestBody.text =
                                                "${getString(R.string.system_message_head)}\nYou cannot switch the current data SIM card."
                                        } else {
                                            val tm = Telephony()
                                            tm.setSimPowerState(slot, newState)
                                            requestBody.text =
                                                "${getString(R.string.system_message_head)}\nSwitching SIM${slot + 1} card status: ${
                                                    if (newState) getString(
                                                        R.string.enable
                                                    ) else getString(R.string.disable)
                                                }"
                                        }
                                    }

                                }

                                "datacard" -> {
                                    if (getActiveCard(applicationContext) < 2) {
                                        requestBody.text =
                                            "${getString(R.string.system_message_head)}\nYou cannot switch the default data SIM card"
                                        return
                                    }

                                    val subscriptionManager =
                                        (applicationContext.getSystemService(
                                            TELEPHONY_SUBSCRIPTION_SERVICE
                                        ) as SubscriptionManager)
                                    val info =
                                        subscriptionManager.getActiveSubscriptionInfo(
                                            SubscriptionManager.getDefaultDataSubscriptionId()
                                        )
                                    var slotIndex = 0
                                    if (info.simSlotIndex == 0) {
                                        slotIndex = 1
                                    }
                                    val subscriptionInfo =
                                        subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(
                                            slotIndex
                                        )
                                    val dataSub = ISub()
                                    dataSub.setDefaultDataSubIdWithShizuku(subscriptionInfo.subscriptionId)
                                    requestBody.text =
                                        "${getString(R.string.system_message_head)}\nOriginal Data SIM: ${(info.simSlotIndex + 1)}\nCurrent Data SIM: ${(subscriptionInfo.simSlotIndex + 1)}"
                                }

                                else -> {
                                    requestBody.text =
                                        "${getString(R.string.system_message_head)}\nUnknown switch type. Available types: autoswitch, data, wifi, datacard"
                                }
                            }
                        }
                    }
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
                    sendStatusMMKV.putInt("status", SEND_SMS_STATUS.PHONE_INPUT_STATUS)
                    var sendSlot = -1
                    if (getActiveCard(applicationContext) > 1) {
                        sendSlot = 0
                        if (command == "/sendsms2") {
                            sendSlot = 1
                        }
                    }
                    sendStatusMMKV.putInt("slot", sendSlot)
                }
                requestBody.text =
                    "[${applicationContext.getString(R.string.send_sms_head)}]\n${getString(R.string.failed_to_get_information)}"
            }

            else -> {
                if (!messageTypeIsPrivate && sendStatusMMKV.getInt("status", -1) == -1) {
                    if (messageType != "supergroup" || messageThreadId.isEmpty()) {
                        Log.i(
                            TAG,
                            "receive_handle: The conversation is not Private and does not prompt an error."
                        )
                        return
                    }
                }

                requestBody.text =
                    "${applicationContext.getString(R.string.system_message_head)}\n${getString(R.string.unknown_command)}"
            }
        }
        if (hasCommand) {
            setSmsSendStatusStandby()
        }
        if (!hasCommand && sendStatusMMKV.getInt("status", -1) != -1) {
            Log.i(TAG, "receive_handle: Enter the interactive SMS sending mode.")
            var dualSim = ""
            val sendSlotTemp = sendStatusMMKV.getInt("slot", -1)
            if (sendSlotTemp != -1) {
                dualSim = "SIM" + (sendSlotTemp + 1) + " "
            }
            val head = "[" + dualSim + applicationContext.getString(R.string.send_sms_head) + "]"
            var resultSend = getString(R.string.failed_to_get_information)
            Log.d(TAG, "Sending mode status: ${sendStatusMMKV.getInt("status", -1)}")

            when (sendStatusMMKV.getInt("status", -1)) {
                SEND_SMS_STATUS.PHONE_INPUT_STATUS -> {
                    sendStatusMMKV.putInt("status", SEND_SMS_STATUS.MESSAGE_INPUT_STATUS)
                    resultSend = getString(R.string.enter_number)
                }

                SEND_SMS_STATUS.MESSAGE_INPUT_STATUS -> {
                    val tempTo = getSendPhoneNumber(requestMsg)
                    if (isPhoneNumber(tempTo)) {
                        sendStatusMMKV.putString("to", tempTo)
                        resultSend = getString(R.string.enter_content)
                        sendStatusMMKV.putInt("status", SEND_SMS_STATUS.WAITING_TO_SEND_STATUS)
                    } else {
                        setSmsSendStatusStandby()
                        resultSend = getString(R.string.unable_get_phone_number)
                    }
                }

                SEND_SMS_STATUS.WAITING_TO_SEND_STATUS -> {
                    sendStatusMMKV.putString("content", requestMsg)
                    val keyboardMarkup = KeyboardMarkup()
                    val inlineKeyboardButtons = ArrayList<ArrayList<InlineKeyboardButton>>()
                    inlineKeyboardButtons.add(
                        ReplyMarkupKeyboard.getInlineKeyboardObj(
                            applicationContext.getString(
                                R.string.send_button
                            ), CALLBACK_DATA_VALUE.SEND
                        )
                    )
                    inlineKeyboardButtons.add(
                        ReplyMarkupKeyboard.getInlineKeyboardObj(
                            applicationContext.getString(
                                R.string.cancel_button
                            ), CALLBACK_DATA_VALUE.CANCEL
                        )
                    )
                    keyboardMarkup.inlineKeyboard = inlineKeyboardButtons
                    requestBody.keyboardMarkup = keyboardMarkup
                    resultSend = "${applicationContext.getString(R.string.to)}${
                        sendStatusMMKV.getString("to", "")
                    }\n${applicationContext.getString(R.string.content)}${
                        sendStatusMMKV.getString("content", "")
                    }"
                    sendStatusMMKV.putInt("status", SEND_SMS_STATUS.SEND_STATUS)
                }
            }
            requestBody.text = "$head\n$resultSend"
        }

        val requestUri = getUrl(botToken, "sendMessage")
        val gson = Gson()
        val body: RequestBody = gson.toJson(requestBody).toRequestBody(Const.JSON)
        Log.d(TAG, "receive_handle: " + gson.toJson(requestBody))
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
                // 安全地获取响应体内容
                val responseString = try {
                    val body = response.body
                    body.string()
                } catch (e: IOException) {
                    writeLog(applicationContext, "Failed to read response body: ${e.message}")
                    return
                } catch (e: NullPointerException) {
                    writeLog(applicationContext, "Response body is null: ${e.message}")
                    return
                }

                if (response.code != 200) {
                    writeLog(applicationContext, errorHead + response.code + " " + responseString)
                    try {
                        response.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to close response: ${e.message}")
                    }
                    return
                }

                val resultObj: JsonObject
                try {
                    resultObj = JsonParser.parseString(responseString).asJsonObject
                } catch (e: Exception) {
                    writeLog(applicationContext, "Failed to parse response JSON: ${e.message}")
                    try {
                        response.close()
                    } catch (e2: Exception) {
                        Log.w(TAG, "Failed to close response: ${e2.message}")
                    }
                    return
                }

                val commandValue = command.replace("_", "")
                if (!hasCommand && sendStatusMMKV.getInt(
                        "status",
                        SEND_SMS_STATUS.STANDBY_STATUS
                    ) == SEND_SMS_STATUS.SEND_STATUS
                ) {
                    try {
                        sendStatusMMKV.putLong("message_id", getMessageId(responseString))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get message ID: ${e.message}")
                    }
                }

                // 合并/hotspot条件判断，并处理需要更新IP地址的情况
                if (commandValue == "/hotspot" && resultObj.has("result")) {
                    // 如果需要更新热点IP地址，则启动更新线程
                    if (statusMMKV.getBoolean("hotspot_ip_update_needed", false)) {
                        val messageObj = resultObj["result"].asJsonObject
                        if (messageObj.has("message_id")) {
                            val hotspotMessageId = messageObj["message_id"].asLong
                            statusMMKV.putLong("hotspot_message_id", hotspotMessageId)
                            val tetherMode = statusMMKV.getInt(
                                "hotspot_tether_mode",
                                TetherManager.TetherMode.TETHERING_WIFI
                            )
                            val editThread = Thread {
                                // 等待初始消息发送完成
                                try {
                                    Thread.sleep(2000)
                                } catch (e: InterruptedException) {
                                    Log.w(TAG, "Hotspot IP update thread interrupted: ${e.message}")
                                    return@Thread
                                }

                                // 尝试多次获取IP地址
                                var newIp = "Unknown"
                                val maxRetries = 10
                                val retryDelay = 1000L
                                for (i in 1..maxRetries) {
                                    try {
                                        newIp = Network.getHotspotIpAddress(tetherMode)
                                        if (newIp != "Unknown") {
                                            break
                                        }
                                        Thread.sleep(retryDelay)
                                    } catch (e: InterruptedException) {
                                        Log.w(
                                            TAG,
                                            "Hotspot IP update thread interrupted: ${e.message}"
                                        )
                                        return@Thread
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error getting hotspot IP address: ${e.message}")
                                        // 继续重试
                                    }
                                    if (i == maxRetries) {
                                        Log.e(
                                            TAG,
                                            "Failed to get hotspot IP after $maxRetries attempts"
                                        )
                                    }
                                }

                                // 如果获取到新IP，编辑消息
                                val messageId = statusMMKV.getLong("hotspot_message_id", -1L)
                                if (newIp != "Unknown" && messageId != -1L) {
                                    val editRequest = RequestMessage()
                                    editRequest.chatId = chatID
                                    editRequest.messageId = messageId
                                    editRequest.messageThreadId = messageThreadId
                                    // 获取原始消息内容
                                    val originalText = requestBody.text
                                    editRequest.text = originalText.replace(
                                        "Gateway IP: Unknown",
                                        "Gateway IP: $newIp"
                                    )

                                    val gson = Gson()
                                    val requestBodyRaw = gson.toJson(editRequest)
                                    val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
                                    val requestUri = getUrl(botToken, "editMessageText")
                                    val request: Request =
                                        Request.Builder().url(requestUri).method("POST", body)
                                            .build()

                                    try {
                                        val client = getOkhttpObj()
                                        val editResponse = client.newCall(request).execute()
                                        try {
                                            Log.d(
                                                TAG,
                                                "Hotspot IP update result: ${editResponse.code}"
                                            )
                                            if (editResponse.code != 200) {
                                                Log.e(
                                                    TAG,
                                                    "Failed to update hotspot IP message. Status code: ${editResponse.code}"
                                                )
                                            }
                                        } finally {
                                            editResponse.close()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to update hotspot IP message", e)
                                    } finally {
                                        // 更新完成后清除标记
                                        statusMMKV.putBoolean("hotspot_ip_update_needed", false)
                                    }
                                }
                            }
                            editThread.isDaemon = true
                            editThread.start()
                        }
                    }

                    // 处理热点关闭逻辑
                    if (!statusMMKV.getBoolean("tether", false)) {
                        disableHotspot(
                            applicationContext,
                            statusMMKV.getInt(
                                "tether_mode",
                                TetherManager.TetherMode.TETHERING_WIFI
                            )
                        )
                        statusMMKV.remove("tether_mode")
                    }
                }
                if (hasCommand && Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    when (commandValue) {
                        "/data" -> setData(
                            !getDataEnable(
                                applicationContext
                            )
                        )

                        "/vpnhotspot" -> if (!statusMMKV.getBoolean("VPNHotspot", false)) {
                            val wifiManager = checkNotNull(
                                applicationContext.getSystemService(
                                    WIFI_SERVICE
                                ) as WifiManager
                            )
                            VPNHotspot.disableVPNHotspot(wifiManager)
                        }
                    }
                }

                // 确保response被正确关闭
                try {
                    response.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to close response: ${e.message}")
                }
            }
        })
    }


    internal object CALLBACK_DATA_VALUE {
        const val SEND: String = "send"
        const val CANCEL: String = "cancel"
    }

    private fun setSmsSendStatusStandby() {
        sendStatusMMKV.clear()
    }

    @SuppressLint("InvalidWakeLockTag", "WakelockTimeout", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        connectivityManager =
            applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        MMKV.initialize(applicationContext)
        preferences = MMKV.defaultMMKV()
        sendStatusMMKV = MMKV.mmkvWithID("send_status")
        statusMMKV = MMKV.mmkvWithID(Const.STATUS_MMKV_ID)
        chatInfoMMKV = MMKV.mmkvWithID(Const.CHAT_INFO_MMKV_ID)
        setSmsSendStatusStandby()
        chatID = preferences.getString("chat_id", "").toString()
        botToken = preferences.getString("bot_token", "").toString()
        messageThreadId = preferences.getString("message_thread_id", "").toString()
        okhttpClient = getOkhttpObj()
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
        mainThread = Thread(threadMainRunnable())
        mainThread.start()
    }


    private object SEND_SMS_STATUS {
        const val STANDBY_STATUS: Int = -1
        const val PHONE_INPUT_STATUS: Int = 0
        const val MESSAGE_INPUT_STATUS: Int = 1
        const val WAITING_TO_SEND_STATUS: Int = 2
        const val SEND_STATUS: Int = 3
    }


    internal inner class threadMainRunnable : Runnable {

        override fun run() {
            Log.d(TAG, "run: thread main start")
            while (true) {
                if (terminalThread) {
                    Log.d(TAG, "run: thread Stop")
                    terminalThread = false
                    break
                }
                val timeout = 60
                val httpTimeout = 65L
                val okhttpClientNew = okhttpClient.newBuilder()
                    .readTimeout(httpTimeout, TimeUnit.SECONDS)
                    .writeTimeout(httpTimeout, TimeUnit.SECONDS)
                    .build()
                val requestUri = getUrl(
                    botToken, "getUpdates"
                )
                val requestBody = PollingJson()
                requestBody.offset = chatInfoMMKV.getLong("offset", 0L)
                requestBody.timeout = timeout
                val body: RequestBody = RequestBody.create(Const.JSON, Gson().toJson(requestBody))
                val request: Request =
                    Request.Builder().url(requestUri).method("POST", body).build()
                val call = okhttpClientNew.newCall(request)
                var response: Response
                try {
                    response = call.execute()
                } catch (e: IOException) {
                    e.printStackTrace()
                    writeLog(
                        applicationContext,
                        "Connection to the Telegram API service failed"
                    )
                    try {
                        Thread.sleep(100L)
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
                    val resultObj = JsonParser.parseString(result).asJsonObject
                    if (resultObj["ok"].asBoolean) {
                        val resultArray = resultObj["result"].asJsonArray
                        for (item in resultArray) {
                            receiveHandle(item.asJsonObject)
                        }
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
                        val resultMessage =
                            "${getString(R.string.system_message_head)}\n${getString(R.string.error_stop_message)}\n${
                                getString(R.string.error_message_head)
                            }${resultObj["description"].asString}\nCode: ${response.code}"
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

    }
}

