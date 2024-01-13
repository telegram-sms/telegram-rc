@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_rc

import aga.android.luch.BeaconScanner
import aga.android.luch.IBeaconBatchListener
import aga.android.luch.IScanner
import aga.android.luch.ScanDuration
import aga.android.luch.parsers.BeaconParserFactory
import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fitc.wifihotspot.TetherManager
import com.google.gson.Gson
import com.qwe7002.telegram_rc.config.beacon
import com.qwe7002.telegram_rc.data_structure.BeaconModel
import com.qwe7002.telegram_rc.data_structure.beaconList
import com.qwe7002.telegram_rc.data_structure.beaconList.beacons
import com.qwe7002.telegram_rc.data_structure.request_message
import com.qwe7002.telegram_rc.root_kit.radio
import com.qwe7002.telegram_rc.static_class.CONST
import com.qwe7002.telegram_rc.static_class.network
import com.qwe7002.telegram_rc.static_class.notify
import com.qwe7002.telegram_rc.static_class.other
import com.qwe7002.telegram_rc.static_class.remote_control
import com.qwe7002.telegram_rc.static_class.resend
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


class beacon_receiver_service : Service() {
    private val TAG = "beacon_receviver"
    private lateinit var wifiManager: WifiManager
    private var notFoundCount = 0
    private var detectCount = 0
    private lateinit var okhttpClient: OkHttpClient
    private lateinit var chatId: String
    private lateinit var requestUrl: String
    lateinit var messageThreadId: String
    private lateinit var scanner: IScanner
    private lateinit var config: beacon
    private lateinit var wakelock: WakeLock
    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private val reloadConfigReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            config = Paper.book("beacon_config").read("config", beacon())!!
        }
    }

    @SuppressLint("InvalidWakeLockTag", "WakelockTimeout")
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
            return
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
        config = Paper.book("beacon_config").read("config", beacon())!!
        LocalBroadcastManager.getInstance(this).registerReceiver(
            reloadConfigReceiver,
            IntentFilter("reload_beacon_config")
        )
        val sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        requestUrl = network.getUrl(sharedPreferences.getString("bot_token", ""), "SendMessage")
        chatId = sharedPreferences.getString("chat_id", "")!!
        messageThreadId = sharedPreferences.getString("message_thread_id", "")!!
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        okhttpClient = network.getOkhttpObj(sharedPreferences.getBoolean("doh_switch", true))
        val notification =
            other.getNotificationObj(applicationContext, getString(R.string.beacon_receiver))
        startForeground(notify.BEACON_SERVICE, notification)

        val batchListener = IBeaconBatchListener { beacons ->
            beaconList.beacons = ArrayList()
            beacons.toList().map {
                val item = BeaconModel(
                    uuid = it.getIdentifierAsUuid(1).toString(),
                    major = it.getIdentifierAsInt(2),
                    minor = it.getIdentifierAsInt(3),
                    rssi = it.rssi.toInt(),
                    hardwareAddress = it.hardwareAddress,
                    distance = scanner.ranger.calculateDistance(it)
                )
                beaconList.beacons.add(item)
                Log.d(TAG, "onCreate: $item")
            }
            LocalBroadcastManager.getInstance(applicationContext)
                .sendBroadcast(Intent("flush_beacons_list"));
        }
        val beaconLayout =
            "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24" // search the Internet to find the layout string of your specific beacon

        val beaconParser = BeaconParserFactory.createFromLayout(beaconLayout)

        scanner = BeaconScanner.Builder(application)
            .setBeaconParser(beaconParser)
            .setBeaconExpirationDuration(10)
            .setScanDuration(
                ScanDuration.UNIFORM
            )
            .setRangingEnabled()
            .setBeaconBatchListener(batchListener)
            .build()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            flushReceiver,
            IntentFilter("flush_beacons_list")
        )
        scanner.start()

    }

    private val flushReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val wifiIsEnableStatus: Boolean
            if (config.useVpnHotspot) {
                if (!remote_control.isVPNHotspotExist(context) && Settings.System.canWrite(
                        context
                    )
                ) {
                    config.useVpnHotspot = false;
                }
                wifiIsEnableStatus = Paper.book("temp").read("wifi_open", false)!!;
            } else {
                if (!Settings.System.canWrite(context) && remote_control.isVPNHotspotExist(
                        context
                    )
                ) {
                    config.useVpnHotspot = true;
                }
                wifiIsEnableStatus = remote_control.isHotspotActive(context);
            }
            if (Paper.book().read("disable_beacon", false)!!) {
                notFoundCount = 0;
                detectCount = 0;
                return;
            }
            val info = getBatteryInfo()
            if (!info.isCharging && info.batteryLevel < 25 && !wifiIsEnableStatus && !config.opposite) {
                notFoundCount = 0
                detectCount = 0
                Log.d(TAG, "onBeaconServiceConnect: Turn off beacon automatic activation")
                return
            }
            val listenBeaconList =
                Paper.book("beacon_config").read("address", ArrayList<String>())!!
            if (listenBeaconList.size == 0) {
                notFoundCount = 0
                detectCount = 0
                Log.i(TAG, "onBeaconServiceConnect: Watchlist is empty")
                return
            }
            var foundBeacon = false
            var detectBeacon: BeaconModel? = null
            for (beacon in beacons) {
                Log.d(
                    TAG,
                    "UUID: " + beacon.uuid + " Rssi: " + beacon.rssi
                )
                for (beacon_address in listenBeaconList) {

                    if (beaconList.beaconItemName(beacon.uuid, beacon.major, beacon.minor)
                            .equals(beacon_address)
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
                    ++detectCount
                };
            } else {
                beaconStatus = "\nBeacon Not Found."
                if (notFoundCount > 1) {
                    detectCount = 0
                }
                ++notFoundCount
            }
            val STATUS_STANDBY = -1
            val STATUS_ENABLE_AP = 0
            val STATUS_DISABLE_AP = 1
            var switchStatus = STATUS_STANDBY
            Log.d(TAG, "detect_singal_count: $detectCount")
            Log.d(TAG, "not_found_count: $notFoundCount")
            if (wifiIsEnableStatus && foundBeacon) {
                if (!config.opposite) {
                    if (detectCount >= config.disableCount) {
                        detectCount = 0
                        notFoundCount = 0
                        if (config.useVpnHotspot) {
                            remote_control.disableVPNHotspot(wifiManager)
                        } else {
                            remote_control.disableHotspot(
                                context,
                                TetherManager.TetherMode.TETHERING_WIFI
                            )
                        }
                        switchStatus = STATUS_DISABLE_AP
                    }
                } else {
                    if (detectCount >= config.enableCount) {
                        detectCount = 0
                        notFoundCount = 0
                        if (config.useVpnHotspot) {
                            remote_control.enableVPNHotspot(wifiManager)
                        } else {
                            remote_control.enableHotspot(
                                context,
                                TetherManager.TetherMode.TETHERING_WIFI
                            )
                        }
                        switchStatus = STATUS_ENABLE_AP
                    }
                }
            }
            if (!wifiIsEnableStatus && !foundBeacon) {
                if (!config.opposite) {
                    if (notFoundCount >= config.enableCount) {
                        detectCount = 0
                        notFoundCount = 0
                        if (config.useVpnHotspot) {
                            remote_control.enableVPNHotspot(wifiManager)
                        } else {
                            remote_control.enableHotspot(
                                context,
                                TetherManager.TetherMode.TETHERING_WIFI
                            )
                        }
                        switchStatus = STATUS_ENABLE_AP
                    }
                } else {
                    if (notFoundCount >= config.disableCount) {
                        detectCount = 0
                        notFoundCount = 0
                        if (config.useVpnHotspot) {
                            remote_control.disableVPNHotspot(wifiManager)
                        } else {
                            remote_control.disableHotspot(
                                context,
                                TetherManager.TetherMode.TETHERING_WIFI
                            )
                        }
                        switchStatus = STATUS_DISABLE_AP
                    }
                }
            }

            var message: String? = null
            when (switchStatus) {
                STATUS_ENABLE_AP -> message = "${getString(R.string.system_message_head)}\n${getString(R.string.enable_wifi)}${getString(R.string.action_success)}$beaconStatus"

                STATUS_DISABLE_AP -> message = "${getString(R.string.system_message_head)}\n${getString(R.string.disable_wifi)}${getString(R.string.action_success)}$beaconStatus"
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
        chat_id: String,
        okhttp_client: OkHttpClient
    ) {
        val request_body = request_message()
        request_body.chat_id = chat_id
        request_body.message_thread_id = messageThreadId
        request_body.text = message + "\n" + getString(R.string.current_battery_level) + getBatteryInfoMsg() + "\n" + getString(R.string.current_network_connection_status) +networkType();
        val requestBodyJson = Gson().toJson(request_body)
        val body: RequestBody = requestBodyJson.toRequestBody(CONST.JSON)
        val requestObj: Request = Request.Builder().url(requestUrl).method("POST", body).build()
        val call: Call = okhttp_client.newCall(requestObj)
        call.enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                resend.addResendLoop(applicationContext, request_body.text)
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "onResponse: " + (response.body?.string() ?: ""))
            }
        })
    }

    fun getBatteryInfoMsg(): String {
        val batteryManager = (applicationContext.getSystemService(BATTERY_SERVICE) as BatteryManager)
        var battery_level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (battery_level > 100) {
            Log.i(
                "get_battery_info",
                "The previous battery is over 100%, and the correction is 100%."
            )
            battery_level = 100
        }
        val intentfilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = applicationContext.registerReceiver(null, intentfilter)!!
        val charge_status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val battery_string_builder = StringBuilder().append(battery_level).append("%")
        when (charge_status) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> battery_string_builder.append(
                " ("
            ).append(applicationContext.getString(R.string.charging)).append(")")

            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> when (batteryStatus.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                -1
            )) {
                BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_PLUGGED_USB, BatteryManager.BATTERY_PLUGGED_WIRELESS -> battery_string_builder.append(
                    " ("
                ).append(applicationContext.getString(R.string.not_charging)).append(")")
            }
        }
        return battery_string_builder.toString()
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
                if (radio.isLTECA()) {
                    netType += "+"
                }
                if (radio.isNRConnected()) {
                    netType += " & NR"
                }
                if (radio.isNRStandby()) {
                    netType += " (NR Standby)"
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

