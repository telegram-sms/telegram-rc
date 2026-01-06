package com.qwe7002.telegram_rc

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.BatteryManager
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
import com.google.gson.annotations.SerializedName
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.data_structure.SMSRequestInfo
import com.qwe7002.telegram_rc.data_structure.telegram.PollingJson
import com.qwe7002.telegram_rc.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_rc.shizuku_kit.ISub
import com.qwe7002.telegram_rc.shizuku_kit.SVC.setBlueTooth
import com.qwe7002.telegram_rc.shizuku_kit.SVC.setData
import com.qwe7002.telegram_rc.shizuku_kit.SVC.setWifi
import com.qwe7002.telegram_rc.shizuku_kit.Telephony
import com.qwe7002.telegram_rc.shizuku_kit.VPNHotspot
import com.qwe7002.telegram_rc.shizuku_kit.VPNHotspot.isVPNHotspotActive
import com.qwe7002.telegram_rc.shizuku_kit.ShizukuKit
import com.qwe7002.telegram_rc.static_class.ArfcnConverter
import com.qwe7002.telegram_rc.static_class.Battery
import com.qwe7002.telegram_rc.static_class.DataUsage
import com.qwe7002.telegram_rc.static_class.Hotspot.disableHotspot
import com.qwe7002.telegram_rc.static_class.Hotspot.enableHotspot
import com.qwe7002.telegram_rc.static_class.Hotspot.isHotspotActive
import com.qwe7002.telegram_rc.static_class.Network
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
import com.qwe7002.telegram_rc.static_class.SMS.sendSMS
import com.qwe7002.telegram_rc.static_class.ServiceManage
import com.qwe7002.telegram_rc.static_class.USSD.sendUssd
import com.tencent.mmkv.MMKV
import com.tencent.mmkv.MMKVLogLevel
import moe.shizuku.server.IShizukuService
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale
import java.util.Objects
import java.util.concurrent.TimeUnit


@Suppress("DEPRECATION", "ClassName")
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
    private var offset = 0L

    object ReplyMarkupKeyboard {
        fun getInlineKeyboardObj(
            text: String,
            callbackData: String
        ): ArrayList<InlineKeyboardButton> {
            val button = InlineKeyboardButton()
            button.text = text
            button.callbackData = callbackData
            val buttonArraylist = ArrayList<InlineKeyboardButton>()
            buttonArraylist.add(button)
            return buttonArraylist
        }

        fun getReplyKeyboardMarkup(context: Context): KeyboardMarkup {
            val keyboard = KeyboardMarkup()
            val keyboardButtons = ArrayList<ArrayList<KeyboardButton>>()

            val row1 = ArrayList<KeyboardButton>()
            row1.add(KeyboardButton("/getinfo"))
            row1.add(KeyboardButton("/battery"))
            row1.add(KeyboardButton("/log"))
            keyboardButtons.add(row1)

            val row2 = ArrayList<KeyboardButton>()
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                row2.add(KeyboardButton("/sendsms"))
            }
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                row2.add(KeyboardButton("/sendussd"))
            }
            keyboardButtons.add(row2)

            val row3 = ArrayList<KeyboardButton>()
            row3.add(KeyboardButton("/hotspot"))
            row3.add(KeyboardButton("/switch auto"))
            keyboardButtons.add(row3)
            val row4 = ArrayList<KeyboardButton>()
            row4.add(KeyboardButton("/switch wifi"))
            row4.add(KeyboardButton("/switch bluetooth"))
            row4.add(KeyboardButton("/switch data"))
            row4.add(KeyboardButton("/switch datacard"))

            keyboardButtons.add(row4)
            if (getActiveCard(context) == 2) {
                val row5 = ArrayList<KeyboardButton>()
                row5.add(KeyboardButton("/switch sim on 1"))
                row5.add(KeyboardButton("/switch sim off 1"))
                row5.add(KeyboardButton("/switch sim on 2"))
                row5.add(KeyboardButton("/switch sim off 2"))
                keyboardButtons.add(row5)
            }

            keyboard.keyboard = keyboardButtons
            keyboard.resizeKeyboard = true
            keyboard.oneTimeKeyboard = false
            keyboard.isPersistent = true

            return keyboard
        }

        fun getRemoveKeyboardMarkup(): KeyboardMarkup {
            val keyboard = KeyboardMarkup()
            keyboard.removeKeyboard = true
            return keyboard
        }

        class KeyboardMarkup {
            @SerializedName(value = "inline_keyboard")
            var inlineKeyboard: ArrayList<ArrayList<InlineKeyboardButton>>? = null

            @SerializedName(value = "keyboard")
            var keyboard: ArrayList<ArrayList<KeyboardButton>>? = null

            @SerializedName(value = "resize_keyboard")
            var resizeKeyboard: Boolean = false

            @SerializedName(value = "one_time_keyboard")
            var oneTimeKeyboard: Boolean = true

            @SerializedName(value = "is_persistent")
            var isPersistent: Boolean = false

            @SerializedName(value = "remove_keyboard")
            var removeKeyboard: Boolean = false
        }

        class InlineKeyboardButton {
            lateinit var text: String

            @SerializedName(value = "callback_data")
            lateinit var callbackData: String
        }

        class KeyboardButton(val text: String)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        return START_STICKY
    }


    override fun onDestroy() {
        if(wifiLock.isHeld){
            wifiLock.release()
        }
        if (wakeLock.isHeld){
            wakeLock.release()
        }
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
            Log.i(
                Const.TAG,
                "receive_handle: message object is null and not a callback query"
            )
            return
        }

        if (messageType == "callback_query" && sendStatusMMKV.getInt(
                "status",
                SEND_SMS_STATUS.STANDBY_STATUS
            ) != SEND_SMS_STATUS.STANDBY_STATUS
        ) {
            // 确保callbackData已初始化
            if (callbackData == null) {
                Log.e(Const.TAG, "Callback data is null")
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
                    slot
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
                var response: Response? = null
                try {
                    response = call.execute()
                    if (response.code != 200) {
                        throw IOException(response.code.toString())
                    }
                } catch (e: IOException) {
                    Log.e(Const.TAG, "failed to send message:" + e.message, e)
                } finally {
                    try {
                        response?.close()
                    } catch (e: Exception) {
                        Log.w(Const.TAG, "Failed to close response: ${e.message}", e)
                    }
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
                Log.i(Const.TAG, "receive_handle: receive from bot.")
                return
            }
        }
        if (messageThreadId != "") {
            if (messageObj != null && messageObj.has("is_topic_message")) {
                fromTopicId = messageObj["message_thread_id"].asString
            }
            if (messageThreadId != fromTopicId) {
                Log.i(Const.TAG, "Topic ID[$fromTopicId] not allow.")
                return
            }
        }
        if (messageObj != null && messageObj.has("chat")) {
            fromObj = messageObj["chat"].asJsonObject
        }

        if (fromObj == null) {
            Log.e(Const.TAG, "From object is null")
            return
        }

        val fromId = fromObj["id"].asString
        if (chatID != fromId) {
            Log.w(Const.TAG, "Chat ID [$fromId] not allow")
            return
        }

        var command = ""
        var requestMsg: String?
        if (messageObj != null && messageObj.has("text")) {
            requestMsg = messageObj["text"].asString
        } else {
            Log.e(Const.TAG, "Text is null")
            Log.w(Const.TAG, "Command message text is null")
            return
        }
        if (messageObj.has("reply_to_message")) {
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
                    command = tempCommandLowercase.take(commandAtLocation)
                }
            }
        }
        Log.d(Const.TAG, "Command: $command")

        when (command) {
            "/help", "/start", "/commandlist" -> {
                val smsCommand = "\n${getString(R.string.sendsms)}"

                var ussdCommand = ""
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    ussdCommand = "\n${getString(R.string.send_ussd_command)}"
                }
                var switchAp = ""
                if (Settings.System.canWrite(applicationContext)) {
                    switchAp += "\n${getString(R.string.switch_ap_message)}"
                }

                var switch = ""
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    switch = "\n/switch - Toggle setting switch"
                }
                if (command == "/commandlist") {
                    requestBody.text =
                        (getString(R.string.available_command) + smsCommand + ussdCommand + switchAp + switch).replace(
                            "/",
                            ""
                        )
                } else {
                    val resultString =
                        "${getString(R.string.system_message_head)}\n${getString(R.string.available_command)}$smsCommand$ussdCommand$switchAp$switch"
                    requestBody.text = resultString
                }
            }

            "/ping", "/getinfo" -> {
                // Send immediate response first, then collect detailed info asynchronously
                val quickInfoRequest = RequestMessage()
                quickInfoRequest.chatId = chatID
                quickInfoRequest.messageThreadId = messageThreadId
                quickInfoRequest.text =
                    "${getString(R.string.system_message_head)}\n${getString(R.string.collecting_info)}"

                val quickRequestUri = getUrl(botToken, "sendMessage")
                val quickBody = Gson().toJson(quickInfoRequest).toRequestBody(Const.JSON)
                val quickSendRequest =
                    Request.Builder().url(quickRequestUri).method("POST", quickBody).build()

                Thread {
                    var messageId: Long = -1
                    try {
                        val quickResponse = okhttpClient.newCall(quickSendRequest).execute()
                        if (quickResponse.code == 200) {
                            val responseBody = quickResponse.body.string()
                            messageId = getMessageId(responseBody)
                        }
                        quickResponse.close()
                    } catch (e: Exception) {
                        Log.e(Const.TAG, "Failed to send quick response: ${e.message}", e)
                    }

                    // Collect detailed info
                    var cardInfo = ""
                    val networkType = Network.getNetworkType(applicationContext)
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
                                sim1Info = tm1.simOperatorName
                            } else {
                                sim1Info =
                                    tm1.simOperatorName + "\nSIM1 Alias: " + sim1Display
                            }
                            if (sim2Display == tm2.simOperatorName) {
                                sim2Info = tm2.simOperatorName
                            } else {
                                sim2Info =
                                    tm2.simOperatorName + "\nSIM2 Alias: " + sim2Display
                            }
                            if (DataUsage.hasPermission(applicationContext)) {
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
                                        imsiCache.getString(phone1Number, null)
                                    )
                                    val phone2DataUsage = DataUsage.getDataUsageForSim(
                                        applicationContext,
                                        imsiCache.getString(phone2Number, null)
                                    )
                                    sim1Info += "\nSIM1 Data Usage: $phone1DataUsage"
                                    sim2Info += "\nSIM2 Data Usage: $phone2DataUsage"

                                }
                            }
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
                            if (DataUsage.hasPermission(applicationContext)) {
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
                                        0
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
                                        imsiCache.getString(phone1Number, null)
                                    )
                                    simInfo += "\nData Usage: $phone1DataUsage"
                                }
                            }
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

                            "\nSIM: $simInfo"
                        }
                    }

                    var isHotspotRunning = ""
                    isHotspotRunning += "\n${getString(R.string.hotspot_status)}"
                    isHotspotRunning += if (isHotspotActive(applicationContext)) {
                        getString(R.string.enable)
                    } else {
                        getString(R.string.disable)
                    }
                    val beacon = MMKV.mmkvWithID(Const.BEACON_MMKV_ID)
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
                    var batteryHealth = ""
                    if (Shizuku.pingBinder()) {
                        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                            // Check if we have BATTERY_STATS permission, if not, try to grant it via Shizuku
                            if (checkSelfPermission(Manifest.permission.BATTERY_STATS) != PackageManager.PERMISSION_GRANTED) {
                                // Try to grant BATTERY_STATS permission using Shizuku shell command
                                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        val service: IShizukuService? =
                                            IShizukuService.Stub.asInterface(Shizuku.getBinder())
                                        if (service != null) {
                                            val shizukuCommand = arrayOf(
                                                "pm",
                                                "grant",
                                                packageName,
                                                Manifest.permission.BATTERY_STATS
                                            )
                                            val process =
                                                service.newProcess(shizukuCommand, null, null)

                                            try {
                                                process.waitFor()
                                            } catch (e: Exception) {
                                                Log.e(
                                                    Const.TAG,
                                                    "BATTERY_STATS grant process error: ${e.message}",
                                                    e
                                                )
                                            }

                                            if (process.exitValue() == 0) {
                                                Log.i(
                                                    Const.TAG,
                                                    "Successfully granted BATTERY_STATS permission via Shizuku"
                                                )
                                            } else {
                                                Log.e(
                                                    Const.TAG,
                                                    "Failed to grant BATTERY_STATS permission via Shizuku"
                                                )
                                            }

                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                            Const.TAG,
                                            "Error granting BATTERY_STATS permission: ${e.message}",
                                            e
                                        )
                                    }
                                }
                            }

                        }
                    }
                    if (batteryHealth.isEmpty() && checkSelfPermission(Manifest.permission.BATTERY_STATS) == PackageManager.PERMISSION_GRANTED) {
                        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                        val batteryStatus = applicationContext.registerReceiver(null, intentFilter)

                        val batteryHealthValue =
                            batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
                        val batteryHealthString = when (batteryHealthValue) {
                            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
                            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                            else -> "Unknown"
                        }
                        val cycleCount =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                val count =
                                    batteryStatus?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
                                        ?: -1
                                "Cycle Count: $count, "
                            } else {
                                ""
                            }
                        val batteryTemperature =
                            batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                                ?.div(10.0)
                        val healthRatio = Battery.getLearnedBatteryCapacity()
                            ?.let { (it.toDouble() / Battery.getBatteryCapacity(applicationContext)) * 100 }
                        val health = healthRatio?.toInt()
                        Log.d(
                            Const.TAG,
                            "getInfo: battery health ratio: $healthRatio, temperature: $batteryTemperature"
                        )
                        batteryHealth =
                            "\nBattery Health: $batteryHealthString${
                                if (health != null) " (${health}%)" else ""
                            } ($cycleCount Temperature: ${batteryTemperature ?: "Unknown"}℃)"
                    }
                    val shizukuStatus =
                        "\nShizuku Status: " + if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) "Enabled" else "Disabled"
                    val fullInfoText =
                        "${getString(R.string.system_message_head)}\n${
                            applicationContext.getString(
                                R.string.current_battery_level
                            )
                        }" + Battery.getBatteryInfo(
                            applicationContext
                        ) + batteryHealth + "\n" + getString(R.string.current_network_connection_status) + networkType + isHotspotRunning + shizukuStatus + beaconStatus + cardInfo

                    Log.d(Const.TAG, "getInfo: $fullInfoText")

                    // Edit the message with full info
                    if (messageId != -1L) {
                        val editRequest = RequestMessage()
                        editRequest.chatId = chatID
                        editRequest.messageId = messageId
                        editRequest.messageThreadId = messageThreadId
                        editRequest.text = fullInfoText

                        val editRequestUri = getUrl(botToken, "editMessageText")
                        val editBody = Gson().toJson(editRequest).toRequestBody(Const.JSON)
                        val editSendRequest =
                            Request.Builder().url(editRequestUri).method("POST", editBody).build()

                        try {
                            val editResponse = okhttpClient.newCall(editSendRequest).execute()
                            if (editResponse.code != 200) {
                                Log.e(
                                    Const.TAG,
                                    "Failed to edit getinfo message: ${editResponse.code}"
                                )
                            }
                            editResponse.close()
                        } catch (e: Exception) {
                            Log.e(Const.TAG, "Failed to edit getinfo message: ${e.message}", e)
                        }
                    }
                }.start()
                return
            }

            "/log" -> {
                requestBody.text =
                    getString(R.string.system_message_head) + "\n" + readLogcat(10)
            }

            "/battery" -> {
                val batteryInfo = Battery.getBatteryInfo(applicationContext)
                val batteryCapacity = Battery.getBatteryCapacity(applicationContext)

                var reportText = "${getString(R.string.battery_report_head)}\n"
                reportText += "${getString(R.string.current_battery_level)}$batteryInfo\n"
                reportText += "Battery Capacity: ${batteryCapacity.toInt()} mAh\n"

                // Get learned battery capacity if Shizuku is available
                var batteryHealthPercent: Double? = null
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    val learnedCapacity = Battery.getLearnedBatteryCapacity()
                    if (learnedCapacity != null) {
                        try {
                            val learnedCapacityValue = learnedCapacity.toDoubleOrNull()
                            if (learnedCapacityValue != null && learnedCapacityValue > 0) {
                                reportText += "Max Learned Capacity: $learnedCapacity mAh\n"
                                // Calculate battery health percentage
                                batteryHealthPercent =
                                    (learnedCapacityValue / batteryCapacity) * 100.0
                                reportText += "Battery Health: %.1f%%\n".format(batteryHealthPercent)
                            }
                        } catch (e: Exception) {
                            Log.e(Const.TAG, "Error calculating battery health: ${e.message}", e)
                        }
                    }
                }

                // Get battery health status
                val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus = checkNotNull(registerReceiver(null, intentFilter))
                val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val healthStr = when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                    else -> "Unknown"
                }
                // Only show status if health percentage was not calculated
                if (batteryHealthPercent == null) {
                    reportText += "Battery Health Status: $healthStr\n"
                } else {
                    reportText += "Health Status: $healthStr\n"
                }

                // Get battery temperature
                val temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (temperature > 0) {
                    reportText += "Temperature: ${temperature / 10.0}°C\n"
                }

                // Get battery voltage
                val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                if (voltage > 0) {
                    reportText += "Voltage: ${voltage / 1000.0}V\n"
                }

                // Get charging status details
                val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                if (plugged > 0) {
                    val pluggedStr = when (plugged) {
                        BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                        else -> "Unknown"
                    }
                    reportText += "Power Source: $pluggedStr"
                }

                requestBody.text = reportText.trim()
                Log.d(Const.TAG, "battery: " + requestBody.text)
            }

            "/hotspot" -> {
                if (MMKV.mmkvWithID(Const.BEACON_MMKV_ID).getBoolean("beacon_enable", false)) {
                    requestBody.text =
                        "${getString(R.string.system_message_head)}\nAutoSwitch is enabled, please disable it first."
                } else {
                    if ((Build.VERSION.SDK_INT >= 36) && (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)) {
                        requestBody.text =
                            "${getString(R.string.system_message_head)}\n${getString(R.string.no_permission)}"
                    } else {
                        val apStatus: Boolean = if (statusMMKV.getInt(
                                "tether_mode",
                                -1
                            ) != TetherManager.TetherMode.TETHERING_VPN
                        ) {
                            isHotspotActive(applicationContext)
                        } else {
                            isVPNHotspotActive()
                        }
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
                                        "wifi" -> TetherManager.TetherMode.TETHERING_WIFI
                                        "vpn" -> TetherManager.TetherMode.TETHERING_VPN
                                        else -> -1
                                    }
                            }
                            if (tetherMode == -1) {
                                resultAp =
                                    "${getString(R.string.system_message_head)}\nUsage: /hotspot [wifi|vpn]"

                            } else {
                                statusMMKV.putInt("tether_mode", tetherMode)
                                if (tetherMode != TetherManager.TetherMode.TETHERING_VPN) {
                                    enableHotspot(applicationContext, tetherMode)
                                    Thread.sleep(300)
                                    val hotspotIp = Network.getHotspotIpAddress(tetherMode)
                                    resultAp += "\nGateway IP: $hotspotIp"
                                    statusMMKV.putBoolean(
                                        "hotspot_ip_update_needed",
                                        hotspotIp == "Unknown"
                                    )
                                } else {
                                    if (!VPNHotspot.isVPNHotspotExist(
                                            applicationContext
                                        )
                                    ) {
                                        resultAp = "VPNHotspot not found"
                                    } else {
                                        val wifiManager =
                                            applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                                        val vpnHotspotStatus =
                                            statusMMKV.getBoolean("tether", false)
                                        if (!vpnHotspotStatus) {
                                            Thread {
                                                VPNHotspot.enableVPNHotspot(
                                                    wifiManager
                                                )
                                            }.start()
                                        }
                                    }

                                }
                            }
                        } else {
                            statusMMKV.putBoolean("tether", false)
                            resultAp =
                                getString(R.string.disable_wifi) + applicationContext.getString(
                                    R.string.action_success
                                )
                            statusMMKV.putBoolean("hotspot_ip_update_needed", false)

                        }
                        resultAp += "\n${applicationContext.getString(R.string.current_battery_level)}" + Battery.getBatteryInfo(
                            applicationContext
                        ) + "\n" + getString(R.string.current_network_connection_status) + Network.getNetworkType(
                            applicationContext
                        )
                        if (DataUsage.hasPermission(applicationContext)) {
                            if (ActivityCompat.checkSelfPermission(
                                    applicationContext,
                                    Manifest.permission.READ_PHONE_STATE
                                ) == PackageManager.PERMISSION_GRANTED &&
                                ActivityCompat.checkSelfPermission(
                                    applicationContext,
                                    Manifest.permission.READ_PHONE_NUMBERS
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val subscriptionManager =
                                    (applicationContext.getSystemService(
                                        TELEPHONY_SUBSCRIPTION_SERVICE
                                    ) as SubscriptionManager)
                                val info =
                                    subscriptionManager.getActiveSubscriptionInfo(
                                        SubscriptionManager.getDefaultDataSubscriptionId()
                                    )
                                val phone1Number =
                                    Phone.getPhoneNumber(applicationContext, info.simSlotIndex)
                                val imsiCache = MMKV.mmkvWithID(Const.IMSI_MMKV_ID)
                                val phone1DataUsage = DataUsage.getDataUsageForSim(
                                    applicationContext,
                                    imsiCache.getString(phone1Number, null)
                                )
                                if (getActiveCard(applicationContext) == 2) {
                                    resultAp += "\n${getString(R.string.current_data_card)}: SIM" + (info.simSlotIndex + 1)
                                }
                                resultAp += "\nData Usage: $phone1DataUsage"
                                if (ActivityCompat.checkSelfPermission(
                                        applicationContext,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    val subId = getSubId(applicationContext, info.simSlotIndex)
                                    val telephonyManager =
                                        applicationContext.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                                    val tm = telephonyManager.createForSubscriptionId(subId)
                                    val cellInfoList =
                                        requestUpdatedCellInfo(applicationContext, tm)
                                    if (cellInfoList.isNotEmpty()) {
                                        val registeredCell = cellInfoList.find { it.isRegistered }
                                        if (registeredCell != null) {
                                            val cellDetails =
                                                ArfcnConverter.getCellInfoDetails(registeredCell)
                                            resultAp += "\nSignal: $cellDetails"
                                        }
                                    }
                                }
                            }

                        }
                        requestBody.text = "${getString(R.string.system_message_head)}\n$resultAp"
                    }
                }
            }

            "/data" -> {
                if (!Shizuku.pingBinder()) {
                    Log.e(Const.TAG, "Shizuku not running")
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

            "/sendussd" -> {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val commandList =
                        requestMsg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    var subId = -1
                    if (getActiveCard(applicationContext) == 2) {
                        if (commandList.size == 3) {
                            subId = getSubId(applicationContext, commandList[1].toInt() - 1)
                            sendUssd(applicationContext, commandList[2], subId)
                            return
                        } else {
                            requestBody.text =
                                "${getString(R.string.system_message_head)}\nUsage: /sendussd <SIM> <USSD Code>"
                        }
                    } else {
                        if (commandList.size == 2) {
                            sendUssd(applicationContext, commandList[1], subId)
                            return
                        } else {
                            requestBody.text =
                                "${getString(R.string.system_message_head)}\nUsage: /sendussd <USSD Code>"
                        }
                    }
                } else {
                    Log.i(Const.TAG, "send_ussd: No permission.")
                    requestBody.text =
                        "${getString(R.string.system_message_head)}\n${getString(R.string.no_permission)}"
                }
            }

            "/switch" -> {
                if (!Shizuku.pingBinder()) {
                    Log.e(Const.TAG, "Shizuku not running")
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
                                "${getString(R.string.system_message_head)}\nUsage: /switch [ auto | data | wifi | bluetooth | sim | datacard ]\nPlease specify a switch type and action."
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

                            // Prepare response message and action to execute after sending
                            var actionToExecute: (() -> Unit)? = null

                            when (switchType) {
                                "auto" -> {
                                    val beacon = MMKV.mmkvWithID(Const.BEACON_MMKV_ID)
                                    val currentState = beacon.getBoolean("beacon_enable", false)
                                    val newState = when (action) {
                                        "on" -> true
                                        "off" -> false
                                        else -> !currentState // toggle
                                    }

                                    // Set response message first
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                                        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                                            requestBody.text =
                                                "${applicationContext.getString(R.string.system_message_head)}\nShizuku permission not granted"
                                        } else {
                                            requestBody.text =
                                                "${applicationContext.getString(R.string.system_message_head)}\nBeacon monitoring status: ${
                                                    if (newState) getString(R.string.enable) else getString(
                                                        R.string.disable
                                                    )
                                                }"
                                            actionToExecute =
                                                { beacon.putBoolean("beacon_enable", newState) }
                                        }
                                    } else {
                                        requestBody.text =
                                            "${applicationContext.getString(R.string.system_message_head)}\nBeacon monitoring status: ${
                                                if (newState) getString(R.string.enable) else getString(
                                                    R.string.disable
                                                )
                                            }"
                                        actionToExecute =
                                            { beacon.putBoolean("beacon_enable", newState) }
                                    }
                                }

                                "data" -> {
                                    val dataEnable = getDataEnable(applicationContext)
                                    val newState = when (action) {
                                        "on" -> true
                                        "off" -> false
                                        else -> !dataEnable // toggle
                                    }
                                    requestBody.text =
                                        "${getString(R.string.system_message_head)}\nSwitching mobile network status: ${
                                            if (newState) getString(R.string.enable) else getString(
                                                R.string.disable
                                            )
                                        }"
                                    actionToExecute = { setData(newState) }
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
                                    requestBody.text =
                                        "${getString(R.string.system_message_head)}\nWIFI state: ${
                                            if (newState) getString(R.string.enable) else getString(
                                                R.string.disable
                                            )
                                        }"
                                    actionToExecute = { setWifi(newState) }
                                }

                                "bluetooth" -> {
                                    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                                    val bluetoothEnabled = bluetoothAdapter?.isEnabled ?: false
                                    val newState = when (action) {
                                        "on" -> true
                                        "off" -> false
                                        else -> !bluetoothEnabled // toggle
                                    }
                                    requestBody.text =
                                        "${getString(R.string.system_message_head)}\nBluetooth state: ${
                                            if (newState) getString(R.string.enable) else getString(
                                                R.string.disable
                                            )
                                        }"
                                    actionToExecute = { setBlueTooth(newState) }
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
                                            "${getString(R.string.system_message_head)}\nUsage: /switch sim [on|off] [1|2]\nPlease specify the SIM slot number."
                                    } else {
                                        val subscriptionManager =
                                            (applicationContext.getSystemService(
                                                TELEPHONY_SUBSCRIPTION_SERVICE
                                            ) as SubscriptionManager)
                                        val info = subscriptionManager.getActiveSubscriptionInfo(
                                            SubscriptionManager.getDefaultDataSubscriptionId()
                                        )
                                        if (info.simSlotIndex == slot) {
                                            requestBody.text =
                                                "${getString(R.string.system_message_head)}\nYou cannot switch the current data SIM card."
                                        } else {
                                            requestBody.text =
                                                "${getString(R.string.system_message_head)}\nSwitching SIM${slot + 1} card status: ${
                                                    if (newState) getString(R.string.enable) else getString(
                                                        R.string.disable
                                                    )
                                                }"
                                            actionToExecute = {
                                                try {
                                                    val tm = Telephony()
                                                    tm.setSimPowerState(slot, newState)
                                                    Log.d(
                                                        Const.TAG,
                                                        "Successfully switched SIM${slot + 1} card status"
                                                    )
                                                } catch (e: Exception) {
                                                    Log.e(
                                                        Const.TAG,
                                                        "Switching SIM${slot + 1} card status failed: ${e.message}",
                                                        e
                                                    )
                                                } catch (e: NoSuchMethodError) {
                                                    Log.e(
                                                        Const.TAG,
                                                        "Switching SIM${slot + 1} card status failed: ${e.message}",
                                                        e
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                "datacard" -> {
                                    if (getActiveCard(applicationContext) < 2) {
                                        requestBody.text =
                                            "${getString(R.string.system_message_head)}\nYou cannot switch the default data SIM card"
                                    } else {
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
                                        requestBody.text =
                                            "${getString(R.string.system_message_head)}\nOriginal Data SIM: ${(info.simSlotIndex + 1)}\nCurrent Data SIM: ${(subscriptionInfo.simSlotIndex + 1)}"
                                        actionToExecute = {
                                            try {
                                                val dataSub = ISub()
                                                dataSub.setDefaultDataSubIdWithShizuku(
                                                    subscriptionInfo.subscriptionId
                                                )
                                                Log.d(
                                                    Const.TAG,
                                                    "Successfully switched default data SIM"
                                                )
                                            } catch (e: Exception) {
                                                Log.e(
                                                    Const.TAG,
                                                    "Switching default data SIM failed: ${e.message}",
                                                    e
                                                )
                                            } catch (e: NoSuchMethodError) {
                                                Log.e(
                                                    Const.TAG,
                                                    "Switching default data SIM failed: Method is not available",
                                                    e
                                                )
                                            }
                                        }
                                    }
                                }

                                else -> {
                                    requestBody.text =
                                        "${getString(R.string.system_message_head)}\nUnknown switch type. Available types: auto, sim, data, wifi, bluetooth, datacard"
                                }
                            }

                            // Store the action to be executed after message is sent
                            if (actionToExecute != null) {
                                statusMMKV.putString("pending_switch_action", switchType)
                                // Execute the action in the callback after message is successfully sent
                                Thread {
                                    // Send message first using synchronous approach
                                    val requestUri = getUrl(botToken, "sendMessage")
                                    val gson = Gson()
                                    val body = gson.toJson(requestBody).toRequestBody(Const.JSON)
                                    val sendRequest =
                                        Request.Builder().url(requestUri).method("POST", body)
                                            .build()

                                    try {
                                        val response = okhttpClient.newCall(sendRequest).execute()
                                        if (response.code == 200) {
                                            Log.d(
                                                Const.TAG,
                                                "Switch message sent successfully, executing action"
                                            )
                                            // Execute the network action after successful message send
                                            actionToExecute.invoke()
                                        } else {
                                            Log.e(
                                                Const.TAG,
                                                "Failed to send switch message: ${response.code}"
                                            )
                                        }
                                        response.close()
                                    } catch (e: Exception) {
                                        Log.e(
                                            Const.TAG,
                                            "Exception sending switch message: ${e.message}",
                                            e
                                        )
                                    }
                                }.start()
                                // Return early to avoid sending the message again in the normal flow
                                return
                            }
                        }
                    }
                }
            }


            "/sendsms" -> {
                val msgSendList =
                    requestMsg.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                val commandRaw = msgSendList[0]
                val command = commandRaw.split(" ").toTypedArray()
                var sendSlot = -1
                if (getActiveCard(applicationContext) > 1) {
                    sendSlot = if (command.size < 2) {
                        -3
                    } else {
                        when (command[1]) {
                            "1" -> 0
                            "2" -> 1
                            else -> -2
                        }
                    }
                }
                when (sendSlot) {
                    -3 -> requestBody.text =
                        "${getString(R.string.system_message_head)}\nPlease specify the SIM card number to send the message"

                    -2 -> requestBody.text =
                        "${getString(R.string.system_message_head)}\nInvalid SIM card number"

                    else -> {
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
                            sendStatusMMKV.putInt("slot", sendSlot)
                        }
                    }
                }
                requestBody.text =
                    "[${applicationContext.getString(R.string.send_sms_head)}]\n${getString(R.string.failed_to_get_information)}"
            }

            else -> {
                if (!messageTypeIsPrivate && sendStatusMMKV.getInt("status", -1) == -1) {
                    if (messageType != "supergroup" || messageThreadId.isEmpty()) {
                        Log.i(
                            Const.TAG,
                            "receive_handle: The conversation is not Private and does not prompt an error."
                        )
                        return
                    }
                }

                requestBody.text =
                    "${applicationContext.getString(R.string.system_message_head)}\n${
                        getString(
                            R.string.unknown_command
                        )
                    }"
            }
        }

        if (hasCommand) {
            setSmsSendStatusStandby()
        }
        if (!hasCommand && sendStatusMMKV.getInt("status", -1) != -1) {
            Log.i(
                Const.TAG,
                "receive_handle: Enter the interactive SMS sending mode."
            )
            var dualSim = ""
            val sendSlotTemp = sendStatusMMKV.getInt("slot", -1)
            if (sendSlotTemp != -1) {
                dualSim = "SIM" + (sendSlotTemp + 1) + " "
            }
            val head =
                "[" + dualSim + applicationContext.getString(R.string.send_sms_head) + "]"
            var resultSend = getString(R.string.failed_to_get_information)
            Log.d(
                Const.TAG,
                "Sending mode status: ${sendStatusMMKV.getInt("status", -1)}"
            )

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
                        sendStatusMMKV.putInt(
                            "status",
                            SEND_SMS_STATUS.WAITING_TO_SEND_STATUS
                        )
                    } else {
                        setSmsSendStatusStandby()
                        resultSend = getString(R.string.unable_get_phone_number)
                    }
                }

                SEND_SMS_STATUS.WAITING_TO_SEND_STATUS -> {
                    sendStatusMMKV.putString("content", requestMsg)
                    val keyboardMarkup = ReplyMarkupKeyboard.KeyboardMarkup()
                    val inlineKeyboardButtons =
                        ArrayList<ArrayList<ReplyMarkupKeyboard.InlineKeyboardButton>>()
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
        val body
                : RequestBody = gson.toJson(requestBody).toRequestBody(Const.JSON)
        Log.v(Const.TAG, "receive_handle: " + gson.toJson(requestBody))
        val sendRequest: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(sendRequest)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(Const.TAG, "Send reply failed: ${e.message}", e)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val responseString = try {
                    val body = response.body
                    body.string()
                } catch (e: IOException) {
                    Log.e(Const.TAG, "Failed to read response body: ${e.message}", e)
                    return
                } catch (e: NullPointerException) {
                    Log.e(Const.TAG, "Response body is null: ${e.message}", e)
                    return
                }

                if (response.code != 200) {
                    Log.e(
                        Const.TAG,
                        "Send reply failed: ${response.code} ${responseString}"
                    )
                    try {
                        response.close()
                    } catch (e: Exception) {
                        Log.w(Const.TAG, "Failed to close response: ${e.message}", e)
                    }
                    return
                }

                val resultObj: JsonObject
                try {
                    resultObj = JsonParser.parseString(responseString).asJsonObject
                } catch (e: Exception) {
                    Log.e(Const.TAG, "Failed to parse response JSON: ${e.message}", e)
                    try {
                        response.close()
                    } catch (e2: Exception) {
                        Log.w(
                            Const.TAG,
                            "Failed to close response: ${e2.message}", e2
                        )
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
                        Log.e(Const.TAG, "Failed to get message ID: ${e.message}", e)
                    }
                }

                if (commandValue == "/hotspot" && resultObj.has("result")) {
                    if (statusMMKV.getBoolean("hotspot_ip_update_needed", false)) {
                        val messageObj = resultObj["result"].asJsonObject
                        if (messageObj.has("message_id")) {
                            val hotspotMessageId = messageObj["message_id"].asLong
                            statusMMKV.putLong("hotspot_message_id", hotspotMessageId)
                            val tetherMode = statusMMKV.getInt(
                                "tether_mode",
                                TetherManager.TetherMode.TETHERING_WIFI
                            )
                            val editThread = Thread {
                                try {
                                    Thread.sleep(2000)
                                } catch (e: InterruptedException) {
                                    Log.w(
                                        Const.TAG,
                                        "Hotspot IP update thread interrupted: ${e.message}"
                                    )
                                    return@Thread
                                }

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
                                            Const.TAG,
                                            "Hotspot IP update thread interrupted: ${e.message}"
                                        )
                                        return@Thread
                                    } catch (e: Exception) {
                                        Log.e(
                                            Const.TAG,
                                            "Error getting hotspot IP address: ${e.message}", e
                                        )
                                        // 继续重试
                                    }
                                    if (i == maxRetries) {
                                        Log.w(
                                            Const.TAG,
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
                                    val body: RequestBody =
                                        requestBodyRaw.toRequestBody(Const.JSON)
                                    val requestUri = getUrl(botToken, "editMessageText")
                                    val request: Request =
                                        Request.Builder().url(requestUri).method("POST", body)
                                            .build()

                                    try {
                                        val client = getOkhttpObj()
                                        val editResponse = client.newCall(request).execute()
                                        try {
                                            Log.d(
                                                Const.TAG,
                                                "Hotspot IP update result: ${editResponse.code}"
                                            )
                                            if (editResponse.code != 200) {
                                                Log.e(
                                                    Const.TAG,
                                                    "Failed to update hotspot IP message. Status code: ${editResponse.code}"
                                                )
                                            }
                                        } finally {
                                            editResponse.close()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                            Const.TAG,
                                            "Failed to update hotspot IP message",
                                            e
                                        )
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
                        if (statusMMKV.getInt(
                                "tether_mode",
                                TetherManager.TetherMode.TETHERING_WIFI
                            ) == TetherManager.TetherMode.TETHERING_VPN
                        ) {
                            val wifiManager = checkNotNull(
                                applicationContext.getSystemService(
                                    WIFI_SERVICE
                                ) as WifiManager
                            )
                            VPNHotspot.disableVPNHotspot(wifiManager)
                        } else {
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
                }
                if (hasCommand && Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    when (commandValue) {
                        "/data" -> setData(
                            !getDataEnable(
                                applicationContext
                            )
                        )
                    }
                }

                // 确保response被正确关闭
                try {
                    response.close()
                } catch (e: Exception) {
                    Log.w(Const.TAG, "Failed to close response: ${e.message}", e)
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
        MMKV.setLogLevel(MMKVLogLevel.LevelWarning)
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
            Log.d(Const.TAG, "threadMainRunnable: thread main start")
            while (true) {
                if (terminalThread) {
                    Log.d(Const.TAG, "threadMainRunnable: thread Stop")
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
                requestBody.offset = offset
                requestBody.timeout = timeout
                val body: RequestBody =
                    RequestBody.create(Const.JSON, Gson().toJson(requestBody))
                val request: Request =
                    Request.Builder().url(requestUri).method("POST", body).build()
                val call = okhttpClientNew.newCall(request)
                var response: Response? = null
                try {
                    response = call.execute()
                    if (response.code == 200) {
                        var result: String
                        try {
                            result = response.body.string()
                            Log.v(Const.TAG, "Polling response: $result")
                        } catch (e: IOException) {
                            Log.e(
                                Const.TAG,
                                "Connection to the Telegram API service failed",
                                e
                            )
                            continue
                        }
                        try {
                            val resultObj = JsonParser.parseString(result).asJsonObject
                            if (resultObj["ok"].asBoolean) {
                                val resultArray = resultObj["result"].asJsonArray
                                for (item in resultArray) {
                                    try {
                                        if (item.asJsonObject.has("update_id")) {
                                            offset = item.asJsonObject["update_id"].asLong + 1
                                        }
                                        receiveHandle(item.asJsonObject)
                                    } catch (e: Exception) {
                                        Log.e(
                                            Const.TAG,
                                            "Error processing message: ${e.message}", e
                                        )
                                    }
                                }
                            } else {
                                if (resultObj.has("description")) {
                                    Log.e(
                                        Const.TAG,
                                        "Error Code: ${resultObj["error_code"]}, ${resultObj["description"]}"
                                    )
                                } else {
                                    Log.e(Const.TAG, "Error response code:" + response.code)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(
                                Const.TAG,
                                "Error parsing JSON response: ${e.message}",
                                e
                            )
                        }
                    } else {
                        Log.e(Const.TAG, "Error response code:" + response.code)
                        try {
                            Thread.sleep(5000L)
                        } catch (_: InterruptedException) {
                        }
                    }
                } catch (e: IOException) {
                    Log.e(
                        Const.TAG,
                        "Connection to the Telegram API service failed: ${e.message}", e
                    )
                    try {
                        Thread.sleep(1000L)
                    } catch (_: InterruptedException) {
                    }
                } catch (e: Exception) {
                    Log.e(
                        Const.TAG,
                        "Unexpected error in polling thread: ${e.message}", e
                    )
                    try {
                        Thread.sleep(1000L)
                    } catch (_: InterruptedException) {
                    }
                } finally {
                    try {
                        response?.close()
                    } catch (e: Exception) {
                        Log.w(Const.TAG, "Failed to close response: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun readLogcat(lines: Int): String {
        return try {
            val level = "I"
            // Use logcat with proper filtering and tail to get recent logs
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "logcat",
                    "${Const.TAG}:${level}",
                    "Telegram-RC.TetherManager:${level}",
                    "${ShizukuKit.TAG}:${level}",
                    "*:S",
                    "-d",
                    "-t",
                    lines.toString()
                )
            )

            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            val logList = mutableListOf<String>()
            var logLine: String?

            while (bufferedReader.readLine().also { logLine = it } != null) {
                logLine?.let {
                    if (it.isNotBlank() && !it.startsWith("---------")) {
                        logList.add(it)
                    }
                }
            }

            bufferedReader.close()
            process.waitFor()

            if (logList.isEmpty()) {
                getString(R.string.no_logs)
            } else {
                // Format logs with HTML code tag for better display in Telegram
                "<code>${logList.joinToString("\n")}</code>"
            }
        } catch (e: Exception) {
            Log.e(Const.TAG, "Error reading logcat: ${e.message}", e)
            "${getString(R.string.no_logs)}\n${getString(R.string.error_message)}${e.message}"
        }
    }
}

