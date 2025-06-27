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
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.fitc.wifihotspot.TetherManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qwe7002.telegram_rc.data_structure.BeaconModel
import com.qwe7002.telegram_rc.data_structure.BeaconModel.beaconItemName
import com.qwe7002.telegram_rc.data_structure.RequestMessage
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
    private var notFoundCount = 0
    private var detectCount = 0
    private lateinit var okhttpClient: OkHttpClient
    private lateinit var chatId: String
    private lateinit var requestUrl: String
    private lateinit var messageThreadId: String
    private lateinit var scanner: IScanner
    private lateinit var wakelock: PowerManager.WakeLock
    private lateinit var config: MMKV
    private var isRoot = false
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
        startForeground(
            Notify.BEACON_SERVICE,
            notification.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(applicationContext)
        if (!hasLocationPermissions()) {
            Log.d(TAG, "onCreate: permission denied")
            return
        }
        initializeWakeLock()
        registerReloadConfigReceiver()
        loadPreferences()
        initializeOkHttpClient()
        initializeBeaconScanner()
        registerFlushReceiver()
        registerStopServiceReceiver()
        scanner.start()
    }

    private fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
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
        isRoot = preferences.getBoolean("root", false)
    }

    private fun initializeOkHttpClient() {
        okhttpClient = Network.getOkhttpObj()
    }

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
                        BeaconModel.BeaconModel(
                            uuid = beacon.getIdentifierAsUuid(1).toString(),
                            major = beacon.getIdentifierAsInt(2),
                            minor = beacon.getIdentifierAsInt(3),
                            rssi = beacon.rssi.toInt(),
                            hardwareAddress = beacon.hardwareAddress,
                            distance = scanner.ranger.calculateDistance(beacon)
                        )
                    } catch (e: Exception) {
                        Log.d(TAG, "Error processing beacon: ${e.message}")
                        null
                    }
                }
                val intent = Intent("flush_beacons_list")
                intent.putExtra("beaconList", Gson().toJson(beaconList))
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerStopServiceReceiver() {
        val intentFilter = IntentFilter(Const.BROADCAST_STOP_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopServiceReceiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(stopServiceReceiver, intentFilter)
        }
    }

    private val stopServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Const.BROADCAST_STOP_SERVICE) {
                Log.i(TAG, "Received stop signal, quitting now...")
                Process.killProcess(Process.myPid())
            }
        }
    }

    private val flushReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            flushReceiverLock.lock()
            try {
                processBeaconList(intent)
            } finally {
                flushReceiverLock.unlock()
            }
        }

        private fun processBeaconList(intent: Intent) {
            if (!config.getBoolean("beacon_enable", false)) {
                resetCounters()
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

            val message = buildMessage(switchStatus, foundBeacon)
            CcSendJob.startJob(applicationContext, "Beacon Scan Service", message)
            sendNetworkRequest(message)
        }

        private fun resetCounters() {
            notFoundCount = 0
            detectCount = 0
        }

        private fun isWifiEnabled(): Boolean {
            return if (config.getBoolean("useVpnHotspot", false)) {
                RemoteControl.isVPNHotspotActive()
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
                notFoundCount = 0
                detectCount++
            } else {
                detectCount = 0
                notFoundCount++
            }
        }

        private fun determineSwitchStatus(foundBeacon: Boolean, wifiEnabled: Boolean): Int {
            val opposite = config.getBoolean("opposite", false)
            val enableCount = config.getInt("enableCount", 10)
            val disableCount = config.getInt("disableCount", 10)

            if (wifiEnabled && foundBeacon) {
                if (!opposite && detectCount >= disableCount) {
                    resetCounters()
                    toggleWifiHotspot(false)
                    return DISABLE_AP
                } else if (opposite && detectCount >= enableCount) {
                    resetCounters()
                    toggleWifiHotspot(true)
                    return ENABLE_AP
                }
            } else if (!wifiEnabled && !foundBeacon) {
                if (!opposite && notFoundCount >= enableCount) {
                    resetCounters()
                    toggleWifiHotspot(true)
                    return ENABLE_AP
                } else if (opposite && notFoundCount >= disableCount) {
                    resetCounters()
                    toggleWifiHotspot(false)
                    return DISABLE_AP
                }
            }
            return STANDBY
        }

        private fun toggleWifiHotspot(enable: Boolean) {
            if (config.getBoolean("useVpnHotspot", false)) {
                if (enable) {
                    RemoteControl.enableVPNHotspot(wifiManager)
                } else {
                    RemoteControl.disableVPNHotspot(wifiManager)
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

            return when (switchStatus) {
                ENABLE_AP -> {
                    Thread.sleep(300)
                    "${getString(R.string.system_message_head)}\n${getString(R.string.enable_wifi)}${
                        getString(
                            R.string.action_success
                        )
                    }\nGateway IP: ${Network.getHotspotIpAddress()}$beaconStatus"
                }

                DISABLE_AP -> "${getString(R.string.system_message_head)}\n${getString(R.string.disable_wifi)}${
                    getString(
                        R.string.action_success
                    )
                }$beaconStatus"

                else -> ""
            }
        }

        private fun sendNetworkRequest(message: String) {
            val requestBody = RequestMessage().apply {
                text = "$message\n${getString(R.string.current_battery_level)}${
                    Battery.getBatteryInfo(applicationContext)
                }\n${getString(R.string.current_network_connection_status)}${
                    Network.getNetworkType(
                        applicationContext
                    )
                }"
            }
            val requestBodyJson = Gson().toJson(requestBody)
            val body = requestBodyJson.toRequestBody(Const.JSON)
            val request = Request.Builder().url(requestUrl).method("POST", body).build()
            okhttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Resend.addResendLoop(applicationContext, requestBody.text)
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d(TAG, "onResponse: ${response.body.string()}")
                }
            })
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onDestroy() {
        if (hasLocationPermissions()) {
            scanner.stop()
        }
        wakelock.release()
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
