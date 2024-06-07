package com.qwe7002.telegram_rc.static_class

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import com.fitc.wifihotspot.TetherManager
import com.qwe7002.telegram_rc.root_kit.ActivityManage.forceStopService
import com.qwe7002.telegram_rc.root_kit.ActivityManage.startForegroundService
import com.qwe7002.telegram_rc.root_kit.Networks.setWifi
import io.paperdb.Paper

object RemoteControl {
    @JvmStatic
    fun disableVPNHotspot(wifiManager: WifiManager) {
        Paper.book("temp").write("wifi_open", false)
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
        Paper.book("temp").write("wifi_open", true)
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
        Paper.book("temp").write("tether_open", true)
        val manager = TetherManager(context)
        manager.startTethering(mode, null)
    }

    @JvmStatic
    fun disableHotspot(context: Context, mode: Int) {
        Paper.book("temp").write("tether_open", false)
        val manager = TetherManager(context)
        manager.stopTethering(mode)
    }

    @JvmStatic
    fun isHotspotActive(context: Context): Boolean {
        val manager = TetherManager(context)
        Paper.book("temp").write("tether_open", manager.isTetherActive)
        return manager.isTetherActive
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
