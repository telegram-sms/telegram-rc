@file:Suppress("SameParameterValue")

package com.qwe7002.telegram_rc.shizuku_kit

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import android.util.Log
import com.qwe7002.telegram_rc.MMKV.Const
import com.tencent.mmkv.MMKV
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object VPNHotspot {
    private const val TAG = "VPNHotspot"

    @JvmStatic
    fun disableVPNHotspot(wifiManager: WifiManager) {
        forceStopService("be.mygod.vpnhotspot")
        SVC.setWifi(false)
        try {
            while (wifiManager.wifiState != WifiManager.WIFI_STATE_DISABLED) {
                Thread.sleep(100)
            }
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        SVC.setWifi(true)
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
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("tether", true)
        SVC.setWifi(true)
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
        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "Shizuku is not running")
            return false
        }

        // 检查Shizuku权限
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Shizuku permission not granted")
            return false
        }

        return try {
            val service: IShizukuService? = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            if (service == null) {
                Log.e(TAG, "Shizuku service not available")
                return false
            }

            val process = service.newProcess(
                arrayOf("dumpsys activity services | grep be.mygod.vpnhotspot/.RepeaterService"), null, null
            )
            val reader = BufferedReader(
                InputStreamReader(
                    ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
                )
            )
            val output = reader.readText()
            process.waitFor()

            output.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check VPNHotspot status: ${e.message}", e)
            false
        }
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

    private fun startForegroundService(packageName: String, serviceName: String): Boolean {
        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "Shizuku is not running")
            return false
        }

        // 检查Shizuku权限
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Shizuku permission not granted")
            return false
        }

        return try {
            val service: IShizukuService? = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            if (service == null) {
                Log.e(TAG, "Shizuku service not available")
                return false
            }

            val process = service.newProcess(
                arrayOf(
                    "am",
                    "start-foreground-service",
                    "-n",
                    "$packageName/$serviceName"
                ), null, null
            )
            val reader = BufferedReader(
                InputStreamReader(
                    ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
                )
            )
            val errorReader = BufferedReader(
                InputStreamReader(
                    ParcelFileDescriptor.AutoCloseInputStream(process.errorStream)
                )
            )

            process.waitFor()
            val output = reader.readText()
            val errorOutput = errorReader.readText()

            if (output.isNotEmpty()) {
                Log.i(TAG, "Command output: $output")
            }

            if (errorOutput.isNotEmpty()) {
                Log.e(TAG, "Command error: $errorOutput")
            }

            process.exitValue() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${e.message}", e)
            false
        }
    }

    private fun forceStopService(packageName: String): Boolean {
        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "Shizuku is not running")
            return false
        }

        // 检查Shizuku权限
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Shizuku permission not granted")
            return false
        }

        return try {
            val service: IShizukuService? = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            if (service == null) {
                Log.e(TAG, "Shizuku service not available")
                return false
            }

            val process = service.newProcess(arrayOf("am", "force-stop", packageName), null, null)
            val reader = BufferedReader(
                InputStreamReader(
                    ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
                )
            )
            val errorReader = BufferedReader(
                InputStreamReader(
                    ParcelFileDescriptor.AutoCloseInputStream(process.errorStream)
                )
            )

            process.waitFor()
            val output = reader.readText()
            val errorOutput = errorReader.readText()

            if (output.isNotEmpty()) {
                Log.i(TAG, "Command output: $output")
            }

            if (errorOutput.isNotEmpty()) {
                Log.e(TAG, "Command error: $errorOutput")
            }

            process.exitValue() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force stop service: ${e.message}", e)
            false
        }
    }
}
