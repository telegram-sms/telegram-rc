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
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.provider.Settings
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
import com.qwe7002.telegram_rc.static_class.CONST
import com.qwe7002.telegram_rc.static_class.network
import com.qwe7002.telegram_rc.static_class.notify
import com.qwe7002.telegram_rc.static_class.other
import com.qwe7002.telegram_rc.static_class.remote_control
import com.qwe7002.telegram_rc.static_class.resend
import io.paperdb.Paper
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response;
import java.io.IOException
import java.util.Objects


class beacon_receiver_service : Service() {
    private val TAG = "beacon_receviver"
    private lateinit var wifiManager: WifiManager
    private var notFoundCount = 0
    private var detectSingalCount = 0
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
                    // todo perfect example of Demeter's Law violation
                    distance = scanner.ranger.calculateDistance(it)
                )
                beaconList.beacons.add(item)
                Log.d(TAG, "onCreate: " + item)
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
        LocalBroadcastManager.getInstance(this).registerReceiver(
            flushReceiver,
            IntentFilter("flush_beacons_list")
        )
        scanner.start()

    }

    private val flushReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val wifiIsEnableStatus: Boolean
            if (config.use_vpn_hotspot) {
                if (!remote_control.isVPNHotspotExist(context) && Settings.System.canWrite(
                        context
                    )
                ) {
                    config.use_vpn_hotspot = false;
                }
                wifiIsEnableStatus = Paper.book("temp").read("wifi_open", false)!!;
            } else {
                if (!Settings.System.canWrite(context) && remote_control.isVPNHotspotExist(
                        context
                    )
                ) {
                    config.use_vpn_hotspot = true;
                }
                wifiIsEnableStatus = remote_control.isHotspotActive(context);
            }
            if (Paper.book().read("disable_beacon", false)!!) {
                notFoundCount = 0;
                detectSingalCount = 0;
                return;
            }
            val info = getBatteryInfo()
            if (!info.isCharging && info.batteryLevel < 25 && !wifiIsEnableStatus && !config.opposite) {
                notFoundCount = 0
                detectSingalCount = 0
                Log.d(TAG, "onBeaconServiceConnect: Turn off beacon automatic activation")
                return
            }
            val listen_beacon_list =
                Paper.book("beacon_config").read("address", ArrayList<String>())!!
            if (listen_beacon_list.size == 0) {
                notFoundCount = 0
                detectSingalCount = 0
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
                for (beacon_address in listen_beacon_list) {

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
            var beacon_status = ""
            if (foundBeacon) {
                if (detectBeacon != null) {
                    beacon_status = "\nBeacon Rssi: " + detectBeacon.rssi + "dBm"
                    notFoundCount = 0
                    if (detectBeacon.rssi < config.RSSI_strenght) {
                        Log.i(TAG, "onBeaconServiceConnect: Signal is too weak, no operation")
                    } else {
                        ++detectSingalCount
                    }
                };
            } else {
                beacon_status = "\nBeacon Not Found."
                if (notFoundCount > 1) {
                    detectSingalCount = 0
                }
                ++notFoundCount
            }
            val STATUS_STANDBY = -1
            val STATUS_ENABLE_AP = 0
            val STATUS_DISABLE_AP = 1
            var switchStatus = STATUS_STANDBY
            Log.d(TAG, "detect_singal_count: $detectSingalCount")
            Log.d(TAG, "not_found_count: $notFoundCount")
            if (wifiIsEnableStatus && foundBeacon) {
                if (!config.opposite) {
                    if (detectSingalCount >= config.disable_count) {
                        detectSingalCount = 0
                        notFoundCount = 0
                        if (config.use_vpn_hotspot) {
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
                    if (detectSingalCount >= config.enable_count) {
                        detectSingalCount = 0
                        notFoundCount = 0
                        if (config.use_vpn_hotspot) {
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
                    if (notFoundCount >= config.enable_count) {
                        detectSingalCount = 0
                        notFoundCount = 0
                        if (config.use_vpn_hotspot) {
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
                    if (notFoundCount >= config.disable_count) {
                        detectSingalCount = 0
                        notFoundCount = 0
                        if (config.use_vpn_hotspot) {
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
                STATUS_ENABLE_AP -> message =
                    """
     ${getString(R.string.system_message_head)}
     ${getString(R.string.enable_wifi)}${getString(R.string.action_success)}$beacon_status
     """.trimIndent()

                STATUS_DISABLE_AP -> message = """
     ${getString(R.string.system_message_head)}
     ${getString(R.string.disable_wifi)}${getString(R.string.action_success)}$beacon_status
     """.trimIndent()
            }
            message?.let { networkProgressHandle(it, chatId, okhttpClient) }

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


    private fun networkProgressHandle   (
        message: String,
        chat_id: String,
        okhttp_client: OkHttpClient
    ) {
        val request_body = request_message()
        request_body.chat_id = chat_id
        request_body.message_thread_id = messageThreadId
        request_body.text = """
                ${
            """
    $message
    ${getString(R.string.current_battery_level)}
    """.trimIndent()
        }${
            chat_command_service.get_battery_info(applicationContext)
        }
                ${getString(R.string.current_network_connection_status)}${
            chat_command_service.get_network_type(
                applicationContext
            )
        }
                """.trimIndent()
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

    private fun getBatteryInfo(): batteryInfo {
        val info = batteryInfo()
        val batteryManager =
            (applicationContext.getSystemService(BATTERY_SERVICE) as BatteryManager)
        info.batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val intentfilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent = applicationContext.registerReceiver(null, intentfilter)!!
        val chargeStatus = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        when (chargeStatus) {
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

    internal class batteryInfo {
        var batteryLevel = 0
        var isCharging = false
    }

}
