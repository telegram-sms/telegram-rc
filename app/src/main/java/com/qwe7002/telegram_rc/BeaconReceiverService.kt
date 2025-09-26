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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.fitc.wifihotspot.TetherManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qwe7002.telegram_rc.data_structure.BeaconModel
import com.qwe7002.telegram_rc.data_structure.BeaconModel.beaconItemName
import com.qwe7002.telegram_rc.data_structure.RequestMessage
import com.qwe7002.telegram_rc.shizuku_kit.VPNHotspot
import com.qwe7002.telegram_rc.static_class.Battery
import com.qwe7002.telegram_rc.static_class.Const
import com.qwe7002.telegram_rc.static_class.Network
import com.qwe7002.telegram_rc.static_class.Notify
import com.qwe7002.telegram_rc.static_class.Other
import com.qwe7002.telegram_rc.static_class.RemoteControl
import com.qwe7002.telegram_rc.static_class.Resend
import com.tencent.mmkv.MMKV
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock

class BeaconReceiverService : Service() {

    private val TAG = "beacon_receiver"
    private lateinit var wifiManager: WifiManager
    private lateinit var okhttpClient: OkHttpClient
    private lateinit var chatId: String
    private lateinit var requestUrl: String
    private lateinit var messageThreadId: String
    private lateinit var scanner: IScanner
    private lateinit var wakelock: PowerManager.WakeLock
    private lateinit var config: MMKV
    private val flushReceiverLock = ReentrantLock()

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
            Log.i(TAG, "onCreate: permission denied")
            return
        }
        okhttpClient = Network.getOkhttpObj()

        initializeWakeLock()
        registerReloadConfigReceiver()
        loadPreferences()
        initializeBeaconScanner()
        registerFlushReceiver()
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReloadConfigReceiver() {
        val intentFilter = IntentFilter("reload_beacon_config")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(reloadConfigReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(reloadConfigReceiver, intentFilter)
        }
    }

    private val reloadConfigReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Reload configuration if needed
        }
    }

    private fun loadPreferences() {
        val preferences = MMKV.defaultMMKV()
        config = MMKV.mmkvWithID(Const.BEACON_MMKV_ID)
        requestUrl = Network.getUrl(preferences.getString("bot_token", "")!!, "SendMessage")
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
                                Log.d(TAG, "Error calculating beacon distance: ${e.message}")
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
                        Log.d(TAG, "Error processing beacon data: ${e.message}")
                        null
                    } catch (e: IllegalStateException) {
                        Log.d(TAG, "Error calculating beacon distance: ${e.message}")
                        null
                    } catch (e: Exception) {
                        Log.d(TAG, "Error processing beacon: ${e.message}")
                        null
                    }
                }
                if (beaconList.isEmpty()) {
                    Log.d(TAG, "No beacons found")
                    return@setBeaconBatchListener
                }
                Log.d(TAG, "Beacons found: ${beaconList.size}")
                val intent = Intent("flush_beacons_list")
                intent.putExtra("beaconList", gson.toJson(beaconList))
                applicationContext.sendBroadcast(intent)
            }
            .build()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerFlushReceiver() {
        val intentFilter = IntentFilter("flush_beacons_list")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(flushReceiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(flushReceiver, intentFilter)
        }
    }

    private val flushReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            flushReceiverLock.lock()
            try {
                processBeaconList(intent)
            } catch (e: Exception) {
                when (e) {
                    is IllegalArgumentException -> {
                        Log.e(TAG, "Invalid beacon list data: ${e.message} for intent ${intent.action}")
                    }
                    is IllegalStateException -> {
                        Log.e(TAG, "Beacon processing in illegal state: ${e.message} for intent ${intent.action}")
                    }
                    is IOException -> {
                        Log.e(TAG, "IO error processing beacon list: ${e.message} for intent ${intent.action}")
                    }
                    is InterruptedException -> {
                        Log.e(TAG, "Interrupted while processing beacon list: ${e.message} for intent ${intent.action}")
                        Thread.currentThread().interrupt()
                    }
                    else -> {
                        Log.e(TAG, "Error processing beacon list: ${e.message} for intent ${intent.action}", e)
                    }
                }
                resetCounters()
            } finally {
                flushReceiverLock.unlock()
            }
        }

        private fun processBeaconList(intent: Intent) {
            if (!config.getBoolean("beacon_enable", false)) {
                resetCounters()
                Log.d(TAG, "processBeaconList: disable")
                return
            }

            val batteryInfo = getBatteryInfo()
            if (!batteryInfo.isCharging && batteryInfo.batteryLevel < 25 && !isWifiEnabled() && !config.getBoolean(
                    "opposite",
                    false
                )
            ) {
                resetCounters()
                Log.d(TAG, "Battery level too low, skipping beacon processing")
                return
            }

            val listenBeaconList = config.decodeStringSet("address", emptySet()) ?: emptySet()
            if (listenBeaconList.isEmpty()) {
                resetCounters()
                Log.i(TAG, "Watchlist is empty")
                return
            }

            if (!isBluetoothEnabled()) {
                resetCounters()
                Log.i(TAG, "Bluetooth is disabled")
                return
            }

            val beacons = parseBeaconList(intent.getStringExtra("beaconList"))
            val foundBeacon = findMatchingBeacon(beacons, listenBeaconList)

            updateCounters(foundBeacon != null)

            val switchStatus = determineSwitchStatus(foundBeacon != null, isWifiEnabled())
            if (switchStatus == STANDBY) {
                Log.d(TAG, "processBeaconList: standby")
                return
            }
            val message = buildMessage(switchStatus, foundBeacon)
            Log.d(TAG, "processBeaconList: $message")
            val requestBody = RequestMessage()
            requestBody.chatId = chatId
            requestBody.text = "$message\n${getString(R.string.current_battery_level)}${
                Battery.getBatteryInfo(applicationContext)
            }\n${getString(R.string.current_network_connection_status)}${
                Network.getNetworkType(
                    applicationContext
                )
            }"
            requestBody.messageThreadId = messageThreadId

            val requestBodyJson = gson.toJson(requestBody)
            val body = requestBodyJson.toRequestBody(Const.JSON)
            val request = Request.Builder().url(requestUrl).method("POST", body).build()
            okhttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    try {
                        Log.d(TAG, "onFailure: " + e.message)
                        Resend.addResendLoop(applicationContext, requestBody.text)
                        e.printStackTrace()
                    } catch (ioException: Exception) {
                        Log.e(TAG, "Error in onFailure handler: ${ioException.message}", ioException)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseString = response.body.string()
                        Log.d(TAG, "onResponse: $responseString")
                        
                        // 如果需要更新热点IP地址，则启动更新线程
                        if (config.getBoolean("need_update_hotspot_ip", false)) {
                            // 获取messageId
                            val messageId = Other.getMessageId(responseString)
                            
                            val updateThread = Thread {
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
                                        newIp = Network.getHotspotIpAddress(TetherManager.TetherMode.TETHERING_WIFI)
                                        if (newIp != "Unknown") {
                                            break
                                        }
                                        Thread.sleep(retryDelay)
                                    } catch (e: InterruptedException) {
                                        Log.w(TAG, "Hotspot IP update thread interrupted: ${e.message}")
                                        return@Thread
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error getting hotspot IP address: ${e.message}")
                                        // 继续重试
                                    }
                                    if (i == maxRetries) {
                                        Log.w(TAG, "Failed to get hotspot IP after $maxRetries attempts")
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
                                    editRequest.text = originalMessage.replace("Gateway IP: Unknown", "Gateway IP: $newIp")

                                    val gson = Gson()
                                    val requestBodyRaw = gson.toJson(editRequest)
                                    val body = requestBodyRaw.toRequestBody(Const.JSON)
                                    val requestUri = Network.getUrl(config.getString("bot_token", "")!!, "editMessageText")
                                    val request = Request.Builder().url(requestUri).method("POST", body).build()

                                    try {
                                        val client = Network.getOkhttpObj()
                                        val editResponse = client.newCall(request).execute()
                                        try {
                                            Log.d(TAG, "Hotspot IP update result: ${editResponse.code}")
                                            if (editResponse.code != 200) {
                                                Log.e(TAG, "Failed to update hotspot IP message. Status code: ${editResponse.code}")
                                            }
                                        } finally {
                                            editResponse.close()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to update hotspot IP message", e)
                                    } finally {
                                        // 更新完成后清除标记
                                        config.putBoolean("need_update_hotspot_ip", false)
                                    }
                                }
                            }
                            updateThread.isDaemon = true
                            updateThread.start()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing response: ${e.message}", e)
                    } finally {
                        response.close()
                    }
                }
            })
        }

        private fun resetCounters() {
            config.remove("notFoundCount")
            config.remove("detectCount")
        }

        private fun isWifiEnabled(): Boolean {
            return if (config.getBoolean("useVpnHotspot", false)) {
                VPNHotspot.isVPNHotspotActive()
            } else {
                RemoteControl.isHotspotActive(applicationContext)
            }
        }

        private fun isBluetoothEnabled(): Boolean {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            return bluetoothAdapter?.isEnabled == true
        }

        private fun parseBeaconList(beaconListJson: String?): List<BeaconModel.BeaconModel> {
            return if (beaconListJson != null) {
                Gson().fromJson(
                    beaconListJson,
                    object : TypeToken<List<BeaconModel.BeaconModel>>() {}.type
                )
            } else {
                emptyList()
            }
        }

        private fun findMatchingBeacon(
            beacons: List<BeaconModel.BeaconModel>,
            listenBeaconList: Set<String>
        ): BeaconModel.BeaconModel? {
            return beacons.find { beacon ->
                listenBeaconList.contains(beaconItemName(beacon.uuid, beacon.major, beacon.minor))
            }
        }

        private fun updateCounters(foundBeacon: Boolean) {
            if (foundBeacon) {
                config.putInt("notFoundCount", 0)
                config.putInt("detectCount", config.getInt("detectCount", 0) + 1)
            } else {
                config.putInt("detectCount", 0)
                config.putInt("notFoundCount", config.getInt("notFoundCount", 0) + 1)
            }
        }

        private fun determineSwitchStatus(foundBeacon: Boolean, wifiEnabled: Boolean): Int {
            val opposite = config.getBoolean("opposite", false)
            // 确保配置值至少为1，避免无效的计数阈值
            val enableCount = maxOf(1, config.getInt("enableCount", 10))
            val disableCount = maxOf(1, config.getInt("disableCount", 10))

            return if (wifiEnabled && foundBeacon) {
                val detectCount = config.getInt("detectCount", 0)
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
                val notFoundCount = config.getInt("notFoundCount", 0)
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
            if (config.getBoolean("useVpnHotspot", false)) {
                if (enable) {
                    VPNHotspot.enableVPNHotspot(applicationContext,wifiManager)
                } else {
                    VPNHotspot.disableVPNHotspot(wifiManager)
                }
            } else {
                val tetherMode = TetherManager.TetherMode.TETHERING_WIFI
                if (enable) {
                    RemoteControl.enableHotspot(applicationContext, tetherMode)
                } else {
                    RemoteControl.disableHotspot(applicationContext, tetherMode)
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
                Thread.sleep(300)
            }
            return when (switchStatus) {
                ENABLE_AP -> {
                    val hotspotIp = Network.getHotspotIpAddress(TetherManager.TetherMode.TETHERING_WIFI)
                    val ipText = if (hotspotIp == "Unknown") {
                        // 标记需要更新IP地址
                        config.putBoolean("need_update_hotspot_ip", true)
                        "Gateway IP: Unknown"
                    } else {
                        config.putBoolean("need_update_hotspot_ip", false)
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

    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onDestroy() {
        if (hasLocationPermissions()) {
            scanner.stop()
        }
        wakelock.release()
        unregisterReceiver(flushReceiver)
        unregisterReceiver(reloadConfigReceiver)
        stopForeground(true)
        super.onDestroy()
    }

    private fun getBatteryInfo(): BatteryInfo {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val intent = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, intent)!!
        val isCharging = when (batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> {
                when (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_PLUGGED_USB, BatteryManager.BATTERY_PLUGGED_WIRELESS -> true
                    else -> false
                }
            }

            else -> false
        }
        return BatteryInfo(batteryLevel, isCharging)
    }

    data class BatteryInfo(val batteryLevel: Int, val isCharging: Boolean)

    companion object {
        private const val STANDBY = -1
        private const val ENABLE_AP = 0
        private const val DISABLE_AP = 1
    }
}
