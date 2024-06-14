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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
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
import com.google.gson.reflect.TypeToken
import com.qwe7002.telegram_rc.config.beacon
import com.qwe7002.telegram_rc.data_structure.BeaconModel
import com.qwe7002.telegram_rc.data_structure.beaconItemName
import com.qwe7002.telegram_rc.data_structure.requestMessage
import com.qwe7002.telegram_rc.root_kit.Radio
import com.qwe7002.telegram_rc.static_class.Const
import com.qwe7002.telegram_rc.static_class.Network
import com.qwe7002.telegram_rc.static_class.Notify
import com.qwe7002.telegram_rc.static_class.Other
import com.qwe7002.telegram_rc.static_class.RemoteControl
import com.qwe7002.telegram_rc.static_class.Resend
import io.paperdb.Paper
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
    private val TAG = "beacon_receviver"
    private lateinit var wifiManager: WifiManager
    private var notFoundCount = 0
    private var detectCount = 0
    private lateinit var okhttpClient: OkHttpClient
    private lateinit var chatId: String
    private lateinit var requestUrl: String
    private lateinit var messageThreadId: String
    private lateinit var scanner: IScanner
    private lateinit var config: beacon
    private lateinit var wakelock: WakeLock
    private var isRoot = false
    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
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
            config = Paper.book("beacon").read("config", beacon())!!
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
        Paper.init(applicationContext)
        wakelock =
            (Objects.requireNonNull(applicationContext.getSystemService(POWER_SERVICE)) as PowerManager).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "beacon_receive"
            )
        wakelock.setReferenceCounted(false)
        if (!this.wakelock.isHeld) {
            this.wakelock.acquire()
        }
        config = Paper.book("beacon").read("config", beacon())!!
        registerReceiver(
            reloadConfigReceiver,
            IntentFilter("reload_beacon_config")
        )
        val sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        requestUrl =
            Network.getUrl(sharedPreferences.getString("bot_token", "").toString(), "SendMessage")
        chatId = sharedPreferences.getString("chat_id", "")!!
        messageThreadId = sharedPreferences.getString("message_thread_id", "")!!
        okhttpClient = Network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true))
        isRoot = sharedPreferences.getBoolean("root", false)

        val batchListener = IBeaconBatchListener { beacons ->
            val beacon = ArrayList<BeaconModel>()
            beacons.toList().map {
                try {
                    val current = it
                    val item = BeaconModel(
                        uuid = current.getIdentifierAsUuid(1).toString(),
                        major = current.getIdentifierAsInt(2),
                        minor = current.getIdentifierAsInt(3),
                        rssi = current.rssi.toInt(),
                        hardwareAddress = current.hardwareAddress,
                        distance = scanner.ranger.calculateDistance(current)
                    )
                    beacon.add(item)
                } catch (e: ConcurrentModificationException) {
                    Log.d(TAG, "onCreate: $e")
                } catch (e: NullPointerException) {
                    Log.d(TAG, "onCreate: $e")
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

        registerReceiver(
            flushReceiver,
            IntentFilter("flush_beacons_list")
        )
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

    private val flushReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val wifiIsEnableStatus: Boolean
            if (config.useVpnHotspot) {
                if (!RemoteControl.isVPNHotspotExist(context) && Settings.System.canWrite(
                        context
                    )
                ) {
                    config.useVpnHotspot = false
                }
                wifiIsEnableStatus = Paper.book("temp").read("wifi_open", false)!!
            } else {
                if (!Settings.System.canWrite(context) && RemoteControl.isVPNHotspotExist(
                        context
                    )
                ) {
                    config.useVpnHotspot = true
                }
                wifiIsEnableStatus = RemoteControl.isHotspotActive(context)
            }
            if (Paper.book().read("disable_beacon", false)!!) {
                notFoundCount = 0
                detectCount = 0
                return
            }
            val info = getBatteryInfo()
            if (!info.isCharging && info.batteryLevel < 25 && !wifiIsEnableStatus && !config.opposite) {
                notFoundCount = 0
                detectCount = 0
                Log.d(TAG, "onBeaconServiceConnect: Turn off beacon automatic activation")
                return
            }
            val listenBeaconList =
                Paper.book("beacon").read("address", ArrayList<String>())!!
            if (listenBeaconList.size == 0) {
                notFoundCount = 0
                detectCount = 0
                Log.i(TAG, "onBeaconServiceConnect: Watchlist is empty")
                return
            }
            val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
            if (!bluetoothAdapter!!.isEnabled) {
                notFoundCount = 0
                detectCount = 0
                Log.i(TAG, "Bluetooth has been turned off")
                return
            }
            var foundBeacon = false
            var detectBeacon: BeaconModel? = null
            val gson = Gson()
            val beacons = gson.fromJson<java.util.ArrayList<BeaconModel>>(
                intent.getStringExtra("beaconList"),
                object : TypeToken<java.util.ArrayList<BeaconModel?>?>() {}.type
            )
            for (beacon in beacons) {
                Log.d(
                    TAG,
                    "UUID: " + beacon.uuid + " Rssi: " + beacon.rssi
                )
                for (beaconAddress in listenBeaconList) {

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
            var beaconStatus = ""
            if (foundBeacon) {
                if (detectBeacon != null) {
                    val distance = detectBeacon.distance.toInt().toString()
                    beaconStatus = "\nBeacon Distance: $distance meter"
                    notFoundCount = 0
                    if (detectBeacon.distance >= 200.0) {
                        Log.i(TAG, "onBeaconServiceConnect: Signal is too weak, no operation")
                    } else {
                        ++detectCount
                    }
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
                if (!config.opposite) {
                    if (detectCount >= config.disableCount) {
                        detectCount = 0
                        notFoundCount = 0
                        if (config.useVpnHotspot) {
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
                    if (detectCount >= config.enableCount) {
                        detectCount = 0
                        notFoundCount = 0
                        if (config.useVpnHotspot) {
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
                if (!config.opposite) {
                    if (notFoundCount >= config.enableCount) {
                        detectCount = 0
                        notFoundCount = 0
                        if (config.useVpnHotspot) {
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
                    if (notFoundCount >= config.disableCount) {
                        detectCount = 0
                        notFoundCount = 0
                        if (config.useVpnHotspot) {
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

            var message: String? = null
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
            message?.let { networkHandle(it, chatId, okhttpClient) }

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
            requestMessage()
        requestBody.chatId = chatId1
        requestBody.messageThreadId = messageThreadId
        requestBody.text =
            message + "\n" + getString(R.string.current_battery_level) + getBatteryInfoMsg() + "\n" + getString(
                R.string.current_network_connection_status
            ) + networkType()
        val requestBodyJson = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyJson.toRequestBody(Const.JSON)
        val requestObj: Request = Request.Builder().url(requestUrl).method("POST", body).build()
        val call: Call = okhttpClient1.newCall(requestObj)
        call.enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Resend.addResendLoop(requestBody.text)
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "onResponse: " + response.body.string())
            }
        })
    }

    private fun getBatteryInfoMsg(): String {
        val batteryManager =
            (applicationContext.getSystemService(BATTERY_SERVICE) as BatteryManager)
        var batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (batteryLevel > 100) {
            Log.i(
                "get_battery_info",
                "The previous battery is over 100%, and the correction is 100%."
            )
            batteryLevel = 100
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = applicationContext.registerReceiver(null, filter)!!
        val chargeStatus = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val batteryStringBuilder = StringBuilder().append(batteryLevel).append("%")
        when (chargeStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> batteryStringBuilder.append(
                " ("
            ).append(applicationContext.getString(R.string.charging)).append(")")

            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> when (batteryStatus.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                -1
            )) {
                BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_PLUGGED_USB, BatteryManager.BATTERY_PLUGGED_WIRELESS -> batteryStringBuilder.append(
                    " ("
                ).append(applicationContext.getString(R.string.not_charging)).append(")")
            }
        }
        return batteryStringBuilder.toString()
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

    private fun networkType(): String? {
        var netType: String? = "Unknown"
        val connectManager =
            (applicationContext.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
        val telephonyManager = (applicationContext
            .getSystemService(TELEPHONY_SERVICE) as TelephonyManager)
        val networks = connectManager.allNetworks
        for (network in networks) {
            val networkCapabilities = connectManager.getNetworkCapabilities(network)!!
            if (!networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    netType = "WIFI"
                    break
                }
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.READ_PHONE_STATE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.i("get_network_type", "No permission.")
                        return netType
                    }
                    netType = checkCellularNetworkType(telephonyManager.dataNetworkType)
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

    private fun checkCellularNetworkType(type: Int): String {
        var netType = "Unknown"
        when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> netType = "NR"
            TelephonyManager.NETWORK_TYPE_LTE -> {
                netType = "LTE"
                if (isRoot) {
                    if (Radio.isLTECA) {
                        netType += "+"
                    }
                    if (Radio.isNRConnected) {
                        netType += " & NR"
                    }
                    if (Radio.isNRStandby) {
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
}

