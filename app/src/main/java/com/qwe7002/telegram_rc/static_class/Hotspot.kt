package com.qwe7002.telegram_rc.static_class

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.fitc.wifihotspot.TetherManager
import com.qwe7002.telegram_rc.MMKV.STATUS_MMKV_ID
import com.qwe7002.telegram_rc.shizuku_kit.TetheringManagerShizuku
import com.tencent.mmkv.MMKV
import org.lsposed.hiddenapibypass.HiddenApiBypass

object Hotspot {
    private const val WIFI_AP_STATE_ENABLING = 12
    private const val WIFI_AP_STATE_ENABLED = 13

    @JvmStatic
    fun enableHotspot(context: Context, mode: Int) {
        MMKV.mmkvWithID(STATUS_MMKV_ID).putBoolean("tether", true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            val manager = TetherManager(context)
            manager.startTethering(mode, null)
        } else {
            TetheringManagerShizuku.startTethering(context, mode)
        }
    }

    @JvmStatic
    fun disableHotspot(context: Context, mode: Int) {
        MMKV.mmkvWithID(STATUS_MMKV_ID).putBoolean("tether", false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            val manager = TetherManager(context)
            manager.stopTethering(mode)
        } else {
            TetheringManagerShizuku.stopTethering(context, mode)
        }
    }

    @JvmStatic
    fun isHotspotActive(context: Context): Boolean {
        val active = isWifiApEnabled(context)
        MMKV.mmkvWithID(STATUS_MMKV_ID).putBoolean("tether", active)
        return active
    }

    @JvmStatic
    @SuppressLint("PrivateApi")
    fun isWifiApEnabled(context: Context): Boolean {
        return try {
            HiddenApiBypass.addHiddenApiExemptions("")
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val state = HiddenApiBypass.invoke(
                WifiManager::class.java, wifiManager, "getWifiApState"
            ) as Int
            state == WIFI_AP_STATE_ENABLING || state == WIFI_AP_STATE_ENABLED
        } catch (e: Exception) {
            Log.w("Hotspot", "getWifiApState failed, falling back to getTetheredIfaces", e)
            TetherManager(context).isTetherActive()
        }
    }
}
