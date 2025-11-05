@file:Suppress("DEPRECATION", "ClassName")

package com.qwe7002.telegram_rc

import aga.android.luch.BeaconScanner
import aga.android.luch.IScanner
import aga.android.luch.ScanDuration
import aga.android.luch.parsers.BeaconParserFactory
import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import com.fitc.wifihotspot.TetherManager
import com.google.gson.Gson
import com.qwe7002.telegram_rc.data_structure.BeaconModel
import com.qwe7002.telegram_rc.data_structure.BeaconModel.beaconItemName
import com.qwe7002.telegram_rc.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_rc.shizuku_kit.VPNHotspot
import com.qwe7002.telegram_rc.static_class.Battery
import com.qwe7002.telegram_rc.static_class.BeaconDataRepository
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.ArfcnConverter
import com.qwe7002.telegram_rc.static_class.DataUsage
import com.qwe7002.telegram_rc.static_class.LogManage
import com.qwe7002.telegram_rc.static_class.Network
import com.qwe7002.telegram_rc.static_class.Network.requestUpdatedCellInfo
import com.qwe7002.telegram_rc.static_class.Notify
import com.qwe7002.telegram_rc.static_class.Other
import com.qwe7002.telegram_rc.static_class.Other.getActiveCard
import com.qwe7002.telegram_rc.static_class.Other.getSubId
import com.qwe7002.telegram_rc.static_class.Phone
import com.qwe7002.telegram_rc.static_class.Hotspot
import com.qwe7002.telegram_rc.static_class.Resend
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rikka.shizuku.Shizuku
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock

class BeaconReceiverService : Service() {

    private val logTag = "beacon_receiver"
    private lateinit var wifiManager: WifiManager
    private lateinit var okhttpClient: OkHttpClient
    private lateinit var chatId: String
    private lateinit var messageThreadId: String
    private lateinit var scanner: IScanner
    private lateinit var wakelock: PowerManager.WakeLock
    private lateinit var preferences: MMKV
    private lateinit var beaconConfig: MMKV
    private var detectCount = 0
    private var notFoundCount = 0
    private val flushReceiverLock = ReentrantLock()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Observer for beacon data
    @SuppressLint("MissingPermission")
    private val beaconDataObserver =
        Observer<ArrayList<BeaconModel.BeaconModel>> @androidx.annotation.RequiresPermission(
            android.Manifest.permission.READ_PHONE_STATE
        ) { beaconList ->
            flushReceiverLock.lock()
            try {
                if (!beaconConfig.getBoolean("beacon_enable", false)) {
                    resetCounters()
                    //Log.d(logTag, "processBeaconList: disable")
                    return@Observer
                }

                if (getBatteryLevel() < 25 && !isWifiEnabled() && !beaconConfig.getBoolean(
                        "opposite",
                        false
                    )
                ) {
                    resetCounters()
                    Log.d(logTag, "Battery level too low, skipping beacon processing")
                    return@Observer
                }

                val listenBeaconList =
                    beaconConfig.decodeStringSet("address", emptySet()) ?: emptySet()
                if (listenBeaconList.isEmpty()) {
                    resetCounters()
                    Log.i(logTag, "Watchlist is empty")
                    return@Observer
                }

                if (!isBluetoothEnabled()) {
                    resetCounters()
                    Log.i(logTag, "Bluetooth is disabled")
                    return@Observer
                }

                val foundBeacon = findMatchingBeacon(beaconList, listenBeaconList)

                updateCounters(foundBeacon != null)

                val switchStatus = determineSwitchStatus(foundBeacon != null, isWifiEnabled())
                if (switchStatus == STANDBY) {
                    //Log.d(TAG, "processBeaconList: standby")
                    return@Observer
                }
                
                // Use coroutine to handle network operations off the main thread
                serviceScope.launch {
                    val message = buildMessage(switchStatus, foundBeacon)
                    Log.d(logTag, "processBeaconList: $message")
                    val requestBody = RequestMessage()
                    requestBody.chatId = chatId
                    
                    // Get battery info and network type asynchronously
                    val batteryInfo = Battery.getBatteryInfo(applicationContext)
                    val networkType = withContext(Dispatchers.IO) {
                        Network.getNetworkType(applicationContext)
                    }
                    
                    requestBody.text = "$message\n${getString(R.string.current_battery_level)}${
                        batteryInfo
                    }\n${getString(R.string.current_network_connection_status)}${
                        networkType
                    }"
                    
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
                                (applicationContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager)
                            val info =
                                subscriptionManager.getActiveSubscriptionInfo(SubscriptionManager.getDefaultDataSubscriptionId())
                            val phone1Number =
                                Phone.getPhoneNumber(applicationContext, info.simSlotIndex)
                            val imsiCache = MMKV.mmkvWithID(Const.IMSI_MMKV_ID)
                            val phone1DataUsage = DataUsage.getDataUsageForSim(
                                applicationContext,
                                imsiCache.getString(phone1Number, null)
                            )
                            if(getActiveCard(applicationContext) == 2){
                                requestBody.text += "\n${getString(R.string.current_data_card)}: SIM" + (info.simSlotIndex + 1)
                            }
                            requestBody.text += "\nData Usage: $phone1DataUsage"
                            if (ActivityCompat.checkSelfPermission(
                                    applicationContext,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val subId = getSubId(applicationContext, info.simSlotIndex)
                                val telephonyManager =
                                    applicationContext.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
                                val tm = telephonyManager.createForSubscriptionId(subId)
                                
                                // Execute cell info request on IO thread
                                val cellInfoList = withContext(Dispatchers.IO) {
                                    requestUpdatedCellInfo(applicationContext, tm)
                                }
                                
                                if (cellInfoList.isNotEmpty()) {
                                    val registeredCell = cellInfoList.find { it.isRegistered }
                                    if (registeredCell != null) {
                                        val cellDetails =
                                            ArfcnConverter.getCellInfoDetails(registeredCell)
                                        requestBody.text += "\nSignal: $cellDetails"
                                    }
                                }
                            }
                        }
                    }
                    requestBody.messageThreadId = messageThreadId

                    val requestBodyJson = gson.toJson(requestBody)
                    val body = requestBodyJson.toRequestBody(Const.JSON)
                    val requestUrl =
                        Network.getUrl(preferences.getString("bot_token", "")!!, "SendMessage")
                    val request = Request.Builder().url(requestUrl).method("POST", body).build()
                    okhttpClient.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            try {
                                Log.d(logTag, "onFailure: " + e.message)
                                Resend.addResendLoop(applicationContext, requestBody.text)
                                e.printStackTrace()
                            } catch (ioException: Exception) {
                                Log.e(
                                    logTag,
                                    "Error in onFailure handler: ${ioException.message}",
                                    ioException
                                )
                            }
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val responseString = response.body.string()
                            Log.d(logTag, "onResponse: $responseString")
                            try {
                                // 如果需要更新热点IP地址，则启动更新线程
                                if (beaconConfig.getBoolean("need_update_hotspot_ip", false)) {
                                    // 获取messageId
                                    val messageId = Other.getMessageId(responseString)

                                    val updateThread = Thread {
                                        // 尝试多次获取IP地址
                                        var newIp = "Unknown"
                                        val maxRetries = 10
                                        val retryDelay = 1000L
                                        for (i in 1..maxRetries) {
                                            try {
                                                newIp =
                                                    Network.getHotspotIpAddress(TetherManager.TetherMode.TETHERING_WIFI)
                                                if (newIp != "Unknown") {
                                                    break
                                                }
                                                Thread.sleep(retryDelay)
                                            } catch (e: InterruptedException) {
                                                Log.w(
                                                    logTag,
                                                    "Hotspot IP update thread interrupted: ${e.message}"
                                                )
                                                LogManage.writeLog(applicationContext, "Hotspot IP update thread interrupted: ${e.message}")
                                                return@Thread
                                            } catch (e: Exception) {
                                                Log.e(
                                                    logTag,
                                                    "Error getting hotspot IP address: ${e.message}"
                                                )
                                                LogManage.writeLog(applicationContext, "Error getting hotspot IP address: ${e.message}")
                                                // 继续重试
                                            }
                                            if (i == maxRetries) {
                                                Log.w(
                                                    logTag,
                                                    "Failed to get hotspot IP after $maxRetries attempts"
                                                )
                                                LogManage.writeLog(applicationContext, "Failed to get hotspot IP after $maxRetries attempts")
                                            }
                                        }

                                        // 如果获取到新IP，编辑消息
                                        if (newIp != "Unknown") {
                                            val editRequest = RequestMessage()
                                            editRequest.chatId = chatId
                                            editRequest.messageId = messageId
                                            editRequest.messageThreadId = messageThreadId

                                            // 构建更新后的消息内容
                                            val originalMessage = requestBody.text
                                            editRequest.text = originalMessage.replace(
                                                "Gateway IP: Unknown",
                                                "Gateway IP: $newIp"
                                            )

                                            val gson = Gson()
                                            val requestBodyRaw = gson.toJson(editRequest)
                                            val body = requestBodyRaw.toRequestBody(Const.JSON)
                                            val requestUri = Network.getUrl(
                                                preferences.getString("bot_token", "")!!,
                                                "editMessageText"
                                            )
                                            val request =
                                                Request.Builder().url(requestUri).method("POST", body)
                                                    .build()

                                            try {
                                                val client = Network.getOkhttpObj()
                                                val editResponse = client.newCall(request).execute()
                                                try {
                                                    Log.d(
                                                        logTag,
                                                        "Hotspot IP update result: ${editResponse.code}"
                                                    )
                                                    if (editResponse.code != 200) {
                                                        Log.e(
                                                            logTag,
                                                            "Failed to update hotspot IP message. Status code: ${editResponse.code}"
                                                        )
                                                    }
                                                } finally {
                                                    editResponse.close()
                                                }
                                            } catch (e: Exception) {
                                                Log.e(logTag, "Failed to update hotspot IP message", e)
                                            } finally {
                                                // 更新完成后清除标记
                                                beaconConfig.putBoolean("need_update_hotspot_ip", false)
                                            }
                                        }
                                    }
                                    updateThread.isDaemon = true
                                    updateThread.start()
                                }
                            } catch (e: Exception) {
                                Log.e(logTag, "Error processing response: ${e.message}", e)
                            } finally {
                                response.close()
                            }
                        }
                    })
                }
            } catch (e: Exception) {
                when (e) {
                    is IllegalArgumentException -> {
                        Log.e(logTag, "Invalid beacon list data: ${e.message}")
                    }

                    is IllegalStateException -> {
                        Log.e(logTag, "Beacon processing in illegal state: ${e.message}")
                    }

                    is IOException -> {
                        Log.e(logTag, "IO error processing beacon list: ${e.message}")
                    }

                    is InterruptedException -> {
                        Log.e(logTag, "Interrupted while processing beacon list: ${e.message}")
                        Thread.currentThread().interrupt()
                    }

                    else -> {
                        Log.e(logTag, "Error processing beacon list: ${e.message}", e)
                    }
                }
                resetCounters()
            } finally {
                flushReceiverLock.unlock()
            }
        }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notification =
            Other.getNotificationObj(applicationContext, getString(R.string.beacon_receiver))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notify.BEACON_SERVICE,
                notification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(
                Notify.BEACON_SERVICE,
                notification.build()
            )
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(applicationContext)
        if (!hasLocationPermissions()) {
            Log.i(logTag, "onCreate: permission denied")
            return
        }
        okhttpClient = Network.getOkhttpObj()

        initializeWakeLock()
        loadPreferences()
        initializeBeaconScanner()
        // Observe beacon data from repository
        BeaconDataRepository.beaconList.observeForever(beaconDataObserver)
        scanner.start()
    }

    private fun hasLocationPermissions(): Boolean {
        var hasPermission = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        // For Android 10+, also check background location permission
        hasPermission = hasPermission && ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return hasPermission
    }

    @SuppressLint("InvalidWakeLockTag", "WakelockTimeout")
    private fun initializeWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "beacon_receive")
        wakelock.setReferenceCounted(false)
        if (!wakelock.isHeld) {
            wakelock.acquire()
        }
    }

    private fun loadPreferences() {
        preferences = MMKV.defaultMMKV()
        beaconConfig = MMKV.mmkvWithID(Const.BEACON_MMKV_ID)
        chatId = preferences.getString("chat_id", "")!!
        messageThreadId = preferences.getString("message_thread_id", "")!!
    }

    private val gson = Gson()

    private fun initializeBeaconScanner() {
        val beaconLayout = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"
        val beaconParser = BeaconParserFactory.createFromLayout(beaconLayout)
        scanner = BeaconScanner.Builder(application)
            .setBeaconParser(beaconParser)
            .setBeaconExpirationDuration(30)
            .setScanDuration(ScanDuration.UNIFORM)
            .setRangingEnabled()
            .setBeaconBatchListener { beacons ->
                val beaconList = beacons.mapNotNull { beacon ->
                    try {
                        val distance = if (::scanner.isInitialized) {
                            try {
                                scanner.ranger.calculateDistance(beacon)
                            } catch (e: Exception) {
                                Log.d(logTag, "Error calculating beacon distance: ${e.message}")
                                -1.0
                            }
                        } else {
                            -1.0
                        }
                        BeaconModel.BeaconModel(
                            uuid = beacon.getIdentifierAsUuid(1).toString(),
                            major = beacon.getIdentifierAsInt(2),
                            minor = beacon.getIdentifierAsInt(3),
                            rssi = beacon.rssi.toInt(),
                            hardwareAddress = beacon.hardwareAddress,
                            distance = distance
                        )
                    } catch (e: IllegalArgumentException) {
                        Log.d(logTag, "Error processing beacon data: ${e.message}")
                        null
                    } catch (e: IllegalStateException) {
                        Log.d(logTag, "Error calculating beacon distance: ${e.message}")
                        null
                    } catch (e: Exception) {
                        Log.d(logTag, "Error processing beacon: ${e.message}")
                        null
                    }
                }
                if (beaconList.isEmpty()) {
                    //Log.d(TAG, "No beacons found")
                    return@setBeaconBatchListener
                }
                //Log.d(TAG, "Beacons found: ${beaconList.size}")
                BeaconDataRepository.updateBeaconList(ArrayList(beaconList))
            }
            .build()
    }

    private fun resetCounters() {
        detectCount = 0
        notFoundCount = 0
    }

    private fun isWifiEnabled(): Boolean {
        return if (beaconConfig.getBoolean("useVpnHotspot", false)) {
            VPNHotspot.isVPNHotspotActive()
        } else {
            Hotspot.isHotspotActive(applicationContext)
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return bluetoothAdapter?.isEnabled == true
    }

    private fun findMatchingBeacon(
        beacons: ArrayList<BeaconModel.BeaconModel>,
        listenBeaconList: Set<String>
    ): BeaconModel.BeaconModel? {
        return beacons.find { beacon ->
            listenBeaconList.contains(beaconItemName(beacon.uuid, beacon.major, beacon.minor))
        }
    }

    private fun updateCounters(foundBeacon: Boolean) {
        if (foundBeacon) {
            notFoundCount = 0
            detectCount++
        } else {
            detectCount = 0
            notFoundCount++
        }
    }

    private fun determineSwitchStatus(foundBeacon: Boolean, wifiEnabled: Boolean): Int {
        val opposite = beaconConfig.getBoolean("opposite", false)
        // 确保配置值至少为1，避免无效的计数阈值
        val enableCount = maxOf(1, beaconConfig.getInt("enableCount", 10))
        val disableCount = maxOf(1, beaconConfig.getInt("disableCount", 10))

        return if (wifiEnabled && foundBeacon) {
            if (!opposite && detectCount >= disableCount) {
                resetCounters()
                toggleWifiHotspot(false)
                DISABLE_AP
            } else if (opposite && detectCount >= enableCount) {
                resetCounters()
                toggleWifiHotspot(true)
                ENABLE_AP
            } else {
                STANDBY
            }
        } else if (!wifiEnabled && !foundBeacon) {
            if (!opposite && notFoundCount >= enableCount) {
                resetCounters()
                toggleWifiHotspot(true)
                ENABLE_AP
            } else if (opposite && notFoundCount >= disableCount) {
                resetCounters()
                toggleWifiHotspot(false)
                DISABLE_AP
            } else {
                STANDBY
            }
        } else {
            STANDBY
        }
    }

    private fun toggleWifiHotspot(enable: Boolean) {
        if(Build.VERSION.SDK_INT>=36){
            if(!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED){
                Log.d(this::class.java.simpleName, "toggleWifiHotspot: shizuku Not work")
                 return
            }
        }
        if (beaconConfig.getBoolean("useVpnHotspot", false)) {
            if (enable) {
                VPNHotspot.enableVPNHotspot(wifiManager)
            } else {
                VPNHotspot.disableVPNHotspot(wifiManager)
            }
        } else {
            val tetherMode = TetherManager.TetherMode.TETHERING_WIFI
            if (enable) {
                Hotspot.enableHotspot(applicationContext, tetherMode)
            } else {
                Hotspot.disableHotspot(applicationContext, tetherMode)
            }
        }
    }


    private fun buildMessage(switchStatus: Int, foundBeacon: BeaconModel.BeaconModel?): String {
        val beaconStatus = if (foundBeacon != null) {
            val distance = foundBeacon.distance.toInt().toString()
            "\nBeacon Distance: $distance meter"
        } else {
            "\nBeacon Not Found."
        }
        if (switchStatus == ENABLE_AP) {
            Thread.sleep(500)
        }
        return when (switchStatus) {
            ENABLE_AP -> {
                val hotspotIp = Network.getHotspotIpAddress(TetherManager.TetherMode.TETHERING_WIFI)
                val ipText = if (hotspotIp == "Unknown") {
                    // 标记需要更新IP地址
                    beaconConfig.putBoolean("need_update_hotspot_ip", true)
                    "Gateway IP: Unknown"
                } else {
                    beaconConfig.putBoolean("need_update_hotspot_ip", false)
                    "Gateway IP: $hotspotIp"
                }
                "${getString(R.string.system_message_head)}\n${getString(R.string.enable_wifi)}${
                    getString(
                        R.string.action_success
                    )
                }\n$ipText$beaconStatus"
            }

            DISABLE_AP -> "${getString(R.string.system_message_head)}\n${getString(R.string.disable_wifi)}${
                getString(
                    R.string.action_success
                )
            }$beaconStatus"

            else -> ""
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onDestroy() {
        if (hasLocationPermissions()) {
            scanner.stop()
        }
        // Remove observer
        BeaconDataRepository.beaconList.removeObserver(beaconDataObserver)
        wakelock.release()
        stopForeground(true)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return batteryLevel
    }

    companion object {
        private const val STANDBY = -1
        private const val ENABLE_AP = 0
        private const val DISABLE_AP = 1
    }
}
