package com.qwe7002.telegram_rc.root_kit

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import com.qwe7002.telegram_rc.root_kit.ActivityManage.forceStopService
import com.qwe7002.telegram_rc.root_kit.ActivityManage.startForegroundService
import com.qwe7002.telegram_rc.root_kit.Networks.setWifi
import com.qwe7002.telegram_rc.static_class.Const
import com.tencent.mmkv.MMKV
import com.topjohnwu.superuser.Shell

object VPNHotspot {
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
    fun isVPNHotspotActive(): Boolean {
        val packageName = "be.mygod.vpnhotspot"
        val serviceName = ".RepeaterService"
        return Shell.getShell().newJob()
            .add("dumpsys activity services | grep $packageName/$serviceName")
            .exec()
            .isSuccess
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
