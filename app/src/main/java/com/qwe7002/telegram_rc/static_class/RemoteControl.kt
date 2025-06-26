package com.qwe7002.telegram_rc.static_class

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import com.fitc.wifihotspot.TetherManager
import com.qwe7002.telegram_rc.MdnsService
import com.qwe7002.telegram_rc.root_kit.ActivityManage.forceStopService
import com.qwe7002.telegram_rc.root_kit.ActivityManage.startForegroundService
import com.qwe7002.telegram_rc.root_kit.Networks.setWifi
import com.tencent.mmkv.MMKV

object RemoteControl {
    @JvmStatic
    fun disableVPNHotspot(wifiManager: WifiManager) {
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("VPNHotspot", false)
        forceStopService("be.mygod.vpnhotspot")
        setWifi(false)
        try {
            while (wifiManager.wifiState != WifiManager.WIFI_STATE_DISABLED) {
                Thread.sleep(100)
            }
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        setWifi(true)
        try {
            while (wifiManager.wifiState != WifiManager.WIFI_STATE_ENABLED) {
                Thread.sleep(100)
            }
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun enableVPNHotspot(wifiManager: WifiManager) {
        if (wifiManager.isWifiEnabled) {
            disableVPNHotspot(wifiManager)
        }
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("VPNHotspot", true)
        setWifi(true)
        try {
            while (wifiManager.wifiState != WifiManager.WIFI_STATE_ENABLED) {
                Thread.sleep(100)
            }
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        startForegroundService("be.mygod.vpnhotspot", "be.mygod.vpnhotspot.RepeaterService")
    }


    @JvmStatic
    fun enableHotspot(context: Context, mode: Int) {
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("tether", true)
        val manager = TetherManager(context)
        manager.startTethering(mode, null)
        val intent = Intent(context, MdnsService::class.java)
        context.startService(intent)
    }

    @JvmStatic
    fun disableHotspot(context: Context, mode: Int) {
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("tether", false)
        val manager = TetherManager(context)
        manager.stopTethering(mode)
        val intent = Intent(context, MdnsService::class.java)
        context.stopService(intent)
    }

    @JvmStatic
    fun isHotspotActive(context: Context): Boolean {
        val manager = TetherManager(context)
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("tether", manager.isTetherActive)
        return manager.isTetherActive
    }
    @JvmStatic
    fun isVPNHotspotActive(): Boolean {
        return MMKV.mmkvWithID(Const.STATUS_MMKV_ID).getBoolean("VPNHotspot", false)
    }

    @JvmStatic
    fun isVPNHotspotExist(context: Context): Boolean {
        var info: ApplicationInfo?
        try {
            info = context.packageManager.getApplicationInfo("be.mygod.vpnhotspot", 0)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            info = null
        }

        return info != null
    }
}
