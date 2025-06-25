@file:Suppress("DEPRECATION", "ClassName")

package com.qwe7002.telegram_rc

import aga.android.luch.BeaconScanner
import aga.android.luch.IBeaconBatchListener
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
import android.os.PowerManager.WakeLock
import android.os.Process
import android.provider.Settings
import android.util.Log
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
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Objects


class BeaconReceiverService : Service() {
    @Suppress("PrivatePropertyName")
    private val TAG = "beacon_receiver"
    private lateinit var wifiManager: WifiManager
    private var notFoundCount = 0
    private var detectCount = 0
    private lateinit var okhttpClient: OkHttpClient
    private lateinit var chatId: String
    private lateinit var requestUrl: String
    private lateinit var messageThreadId: String
    private lateinit var scanner: IScanner
    private lateinit var wakelock: WakeLock
    private var isRoot = false
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
            Notify.BEACON_SERVICE, notification.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

    }


    private val reloadConfigReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
        }
    }

    @SuppressLint("InvalidWakeLockTag", "WakelockTimeout", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "onCreate: permission denied")
        }
        wakelock =
            (Objects.requireNonNull(applicationContext.getSystemService(POWER_SERVICE)) as PowerManager).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "beacon_receive"
            )
        wakelock.setReferenceCounted(false)
        if (!this.wakelock.isHeld) {
            this.wakelock.acquire()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                reloadConfigReceiver,
                IntentFilter("reload_beacon_config"),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                reloadConfigReceiver,
                IntentFilter("reload_beacon_config")
            )
        }
        val preferences = MMKV.defaultMMKV()
        requestUrl =
            Network.getUrl(preferences.getString("bot_token", "").toString(), "SendMessage")
        chatId = preferences.getString("chat_id", "")!!
        messageThreadId = preferences.getString("message_thread_id", "")!!
        okhttpClient = Network.getOkhttpObj()
        isRoot = preferences.getBoolean("root", false)

        val batchListener = IBeaconBatchListener { beacons ->
            val beacon = ArrayList<BeaconModel.BeaconModel>()
            beacons.toList().map {
                try {
                    val current = it
                    val item = BeaconModel.BeaconModel(
                        uuid = current.getIdentifierAsUuid(1).toString(),
                        major = current.getIdentifierAsInt(2),
                        minor = current.getIdentifierAsInt(3),
                        rssi = current.rssi.toInt(),
                        hardwareAddress = current.hardwareAddress,
                        distance = scanner.ranger.calculateDistance(current)
                    )
                    beacon.add(item)
                } catch (e: ConcurrentModificationException) {
                    Log.d(TAG, "ConcurrentModificationException: $e")
                } catch (e: NullPointerException) {
                    Log.d(TAG, "NullPointerException: $e")
                }
            }
            val intents = Intent("flush_beacons_list")
            val gson = Gson()
            intents.putExtra("beaconList", gson.toJson(beacon))

            applicationContext.sendBroadcast(intents)
        }
        val beaconLayout =
            "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24" // search the Internet to find the layout string of your specific beacon

        val beaconParser = BeaconParserFactory.createFromLayout(beaconLayout)

        scanner = BeaconScanner.Builder(application)
            .setBeaconParser(beaconParser)
            .setBeaconExpirationDuration(30)
            .setScanDuration(
                ScanDuration.UNIFORM
            )
            .setRangingEnabled()
            .setBeaconBatchListener(batchListener)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                flushReceiver,
                IntentFilter("flush_beacons_list"),
                RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(
                flushReceiver,
                IntentFilter("flush_beacons_list")
            )
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(Const.BROADCAST_STOP_SERVICE)
        val broadcastReceiver = broadcastReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }
        scanner.start()

    }


    private inner class broadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("beaconReceiver", "onReceive: " + intent.action)
            assert(intent.action != null)
            if (Const.BROADCAST_STOP_SERVICE == intent.action) {
                Log.i("beaconReceiver", "Received stop signal, quitting now...")
                Process.killProcess(Process.myPid())
            }
        }
    }

    // Add a mutex lock object to protect flushReceiver
    private val flushReceiverLock = Object()

    private val flushReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        private val config: MMKV = MMKV.mmkvWithID("beacon")
        override fun onReceive(context: Context, intent: Intent) {
            // Use synchronized block to prevent concurrent execution
            synchronized(flushReceiverLock) {
                val wifiIsEnableStatus: Boolean
                if (config.getBoolean("useVpnHotspot", false) == true) {
                    if (!RemoteControl.isVPNHotspotExist(context) && Settings.System.canWrite(
                            context
                        )
                    ) {
                        config.putBoolean("useVpnHotspot", false)
                    }
                    wifiIsEnableStatus = RemoteControl.isVPNHotspotActive()
                } else {
                    if (!Settings.System.canWrite(context) && RemoteControl.isVPNHotspotExist(
                            context
                        )
                    ) {
                        config.putBoolean("useVpnHotspot", true)
                    }
                    wifiIsEnableStatus = RemoteControl.isHotspotActive(context)
                }
                if (config.getBoolean("beacon_enable", false) != true) {
                    notFoundCount = 0
                    detectCount = 0
                    return
                }
                val info = getBatteryInfo()
                if (!info.isCharging && info.batteryLevel < 25 && !wifiIsEnableStatus && !config.getBoolean(
                        "opposite",
                        false
                    )
                ) {
                    notFoundCount = 0
                    detectCount = 0
                    Log.d(TAG, "onBeaconServiceConnect: Turn off beacon automatic activation")
                    return
                }
                //val listenBeaconList =
                //Paper.book("beacon").read("address", ArrayList<String>())!!
                val listenBeaconList = config.decodeStringSet("address", emptySet())
                if (listenBeaconList != null) {
                    if (listenBeaconList.isEmpty()) {
                        notFoundCount = 0
                        detectCount = 0
                        Log.i(TAG, "onBeaconServiceConnect: Watchlist is empty")
                        return
                    }
                }
                val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (!bluetoothAdapter.isEnabled) {
                    notFoundCount = 0
                    detectCount = 0
                    Log.i(TAG, "Bluetooth has been turned off")
                    return
                }
                var foundBeacon = false
                lateinit var detectBeacon: BeaconModel.BeaconModel
                val gson = Gson()
                val beacons = gson.fromJson<java.util.ArrayList<BeaconModel.BeaconModel>>(
                    intent.getStringExtra("beaconList"),
                    object : TypeToken<java.util.ArrayList<BeaconModel.BeaconModel>>() {}.type
                )
                for (beacon in beacons) {
                    Log.d(
                        TAG,
                        "UUID: " + beacon.uuid + " Rssi: " + beacon.rssi
                    )
                    for (beaconAddress in listenBeaconList!!) {

                        if (beaconItemName(beacon.uuid, beacon.major, beacon.minor) == beaconAddress
                        ) {
                            foundBeacon = true
                            break
                        }
                    }
                    if (foundBeacon) {
                        detectBeacon = beacon
                        break
                    }
                }
                val beaconStatus: String
                if (foundBeacon) {
                    val distance = detectBeacon.distance.toInt().toString()
                    beaconStatus = "\nBeacon Distance: $distance meter"
                    notFoundCount = 0
                    if (detectBeacon.distance >= 200.0) {
                        Log.i(TAG, "onBeaconServiceConnect: Signal is too weak, no operation")
                    } else {
                        ++detectCount
                    }
                } else {
                    beaconStatus = "\nBeacon Not Found."
                    if (notFoundCount >= 5) {
                        detectCount = 0
                    }
                    ++notFoundCount
                }
                val STANDBY = -1
                val ENABLE_AP = 0
                val DISABLE_AP = 1
                var switchStatus = STANDBY
                Log.d(TAG, "detect_singal_count: $detectCount")
                Log.d(TAG, "not_found_count: $notFoundCount")
                if (wifiIsEnableStatus && foundBeacon) {
                    if (!config.getBoolean("opposite", false)) {
                        if (detectCount >= config.getInt("disableCount", 10)) {
                            detectCount = 0
                            notFoundCount = 0
                            if (config.getBoolean("useVpnHotspot", false)) {
                                RemoteControl.disableVPNHotspot(wifiManager)
                            } else {
                                RemoteControl.disableHotspot(
                                    context,
                                    TetherManager.TetherMode.TETHERING_WIFI
                                )
                            }
                            switchStatus = DISABLE_AP
                        }
                    } else {
                        if (detectCount >= config.getInt("enableCount", 10)) {
                            detectCount = 0
                            notFoundCount = 0
                            if (config.getBoolean("useVpnHotspot", false)) {
                                RemoteControl.enableVPNHotspot(wifiManager)
                            } else {
                                RemoteControl.enableHotspot(
                                    context,
                                    TetherManager.TetherMode.TETHERING_WIFI
                                )
                            }
                            switchStatus = ENABLE_AP
                        }
                    }
                }
                if (!wifiIsEnableStatus && !foundBeacon) {
                    if (!config.getBoolean("opposite", false)) {
                        if (notFoundCount >= config.getInt("enableCount", 10)) {
                            detectCount = 0
                            notFoundCount = 0
                            if (config.getBoolean("useVpnHotspot", false)) {
                                RemoteControl.enableVPNHotspot(wifiManager)
                            } else {
                                RemoteControl.enableHotspot(
                                    context,
                                    TetherManager.TetherMode.TETHERING_WIFI
                                )
                            }
                            switchStatus = ENABLE_AP
                        }
                    } else {
                        if (notFoundCount >= config.getInt("disableCount", 10)) {
                            detectCount = 0
                            notFoundCount = 0
                            if (config.getBoolean("useVpnHotspot", false)) {
                                RemoteControl.disableVPNHotspot(wifiManager)
                            } else {
                                RemoteControl.disableHotspot(
                                    context,
                                    TetherManager.TetherMode.TETHERING_WIFI
                                )
                            }
                            switchStatus = DISABLE_AP
                        }
                    }
                }

                lateinit var message: String
                when (switchStatus) {
                    ENABLE_AP -> message =
                        "${getString(R.string.system_message_head)}\n${getString(R.string.enable_wifi)}${
                            getString(R.string.action_success)
                        }$beaconStatus"

                    DISABLE_AP -> message =
                        "${getString(R.string.system_message_head)}\n${getString(R.string.disable_wifi)}${
                            getString(R.string.action_success)
                        }$beaconStatus"
                }

                CcSendJob.startJob(context, "Beacon Scan Service", message)
                networkHandle(message, chatId, okhttpClient)

            }
        }
    }

    override fun onDestroy() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            scanner.stop()
        }
        wakelock.release()
        this.stopForeground(true)
        super.onDestroy()
    }


    private fun networkHandle(
        message: String,
        chatId1: String,
        okhttpClient1: OkHttpClient
    ) {
        val requestBody =
            RequestMessage()
        requestBody.chatId = chatId1
        requestBody.messageThreadId = messageThreadId
        requestBody.text =
            message + "\n${getString(R.string.current_battery_level)}${
                Battery.getBatteryInfo(
                    applicationContext
                )
            }\n${
                getString(
                    R.string.current_network_connection_status
                )
            }${Network.getNetworkType(applicationContext)}"
        val requestBodyJson = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyJson.toRequestBody(Const.JSON)
        val requestObj: Request = Request.Builder().url(requestUrl).method("POST", body).build()
        val call: Call = okhttpClient1.newCall(requestObj)
        call.enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Resend.addResendLoop(applicationContext, requestBody.text)
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "onResponse: " + response.body.string())
            }
        })
    }


    private fun getBatteryInfo(): BatteryInfo {
        val info = BatteryInfo()
        val batteryManager =
            (applicationContext.getSystemService(BATTERY_SERVICE) as BatteryManager)
        info.batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val intent = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent = applicationContext.registerReceiver(null, intent)!!
        when (batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> info.isCharging =
                true

            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> when (batteryStatus.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                -1
            )) {
                BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_PLUGGED_USB, BatteryManager.BATTERY_PLUGGED_WIRELESS -> info.isCharging =
                    true
            }
        }
        return info
    }

    internal class BatteryInfo {
        var batteryLevel = 0
        var isCharging = false
    }

}
