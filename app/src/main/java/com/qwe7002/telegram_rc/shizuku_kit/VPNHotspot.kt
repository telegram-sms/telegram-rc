@file:Suppress("SameParameterValue")

package com.qwe7002.telegram_rc.shizuku_kit

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import android.util.Log
import com.qwe7002.telegram_rc.MMKV.STATUS_MMKV_ID
import com.tencent.mmkv.MMKV
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object VPNHotspot {

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
            Log.e(ShizukuKit.TAG, "disableVPNHotspot: ${e.message}",e )
        }
        SVC.setWifi(true)
        try {
            while (wifiManager.wifiState != WifiManager.WIFI_STATE_ENABLED) {
                Thread.sleep(100)
            }
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            Log.e(ShizukuKit.TAG, "disableVPNHotspot: ${e.message}",e )
        }
    }

    @JvmStatic
    fun enableVPNHotspot(wifiManager: WifiManager) {
        if (wifiManager.isWifiEnabled) {
            disableVPNHotspot(wifiManager)
        }
        MMKV.mmkvWithID(STATUS_MMKV_ID).putBoolean("tether", true)
        SVC.setWifi(true)
        try {
            while (wifiManager.wifiState != WifiManager.WIFI_STATE_ENABLED) {
                Thread.sleep(100)
            }
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            Log.e(ShizukuKit.TAG, "enableVPNHotspot: ${e.message}",e )
        }

        startForegroundService("be.mygod.vpnhotspot", "be.mygod.vpnhotspot.RepeaterService")
    }

    @JvmStatic
    fun isVPNHotspotActive(): Boolean {
        if (!Shizuku.pingBinder()) {
            Log.e(ShizukuKit.TAG, "Shizuku is not running")
            return false
        }

        // 检查Shizuku权限
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e(ShizukuKit.TAG, "Shizuku permission not granted")
            return false
        }

        return try {
            val service: IShizukuService? = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            if (service == null) {
                Log.e(ShizukuKit.TAG, "Shizuku service not available")
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

            // 使用線程來實現超時
            val processThread = Thread {
                try {
                    process.waitFor()
                } catch (e: Exception) {
                    Log.e(ShizukuKit.TAG, "Process wait error: ${e.message}")
                }
            }
            processThread.start()
            processThread.join(10000) // 10秒超時

            if (processThread.isAlive) {
                process.destroy()
                processThread.interrupt()
                Log.e(ShizukuKit.TAG, "isVPNHotspotActive command timed out")
                return false
            }

            output.isNotEmpty()
        } catch (e: Exception) {
            Log.e(ShizukuKit.TAG, "Failed to check VPNHotspot status: ${e.message}", e)
            false
        }
    }

    @JvmStatic
    fun isVPNHotspotExist(context: Context): Boolean {
        var info: ApplicationInfo?
        try {
            info = context.packageManager.getApplicationInfo("be.mygod.vpnhotspot", 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(ShizukuKit.TAG, "isVPNHotspotExist: ${e.message}",e)
            info = null
        }

        return info != null
    }

    private fun startForegroundService(packageName: String, serviceName: String): Boolean {
        if (!Shizuku.pingBinder()) {
            Log.e(ShizukuKit.TAG, "Shizuku is not running")
            return false
        }

        // 检查Shizuku权限
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e(ShizukuKit.TAG, "Shizuku permission not granted")
            return false
        }

        return try {
            val service: IShizukuService? = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            if (service == null) {
                Log.e(ShizukuKit.TAG, "Shizuku service not available")
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

            // 使用線程來實現超時
            val processThread = Thread {
                try {
                    process.waitFor()
                } catch (e: Exception) {
                    Log.e(ShizukuKit.TAG, "Process wait error: ${e.message}")
                }
            }
            processThread.start()
            processThread.join(10000) // 10秒超時

            if (processThread.isAlive) {
                process.destroy()
                processThread.interrupt()
                Log.e(ShizukuKit.TAG, "startForegroundService command timed out")
                return false
            }

            val output = reader.readText()
            val errorOutput = errorReader.readText()

            if (output.isNotEmpty()) {
                Log.i(ShizukuKit.TAG, "Command output: $output")
            }

            if (errorOutput.isNotEmpty()) {
                Log.e(ShizukuKit.TAG, "Command error: $errorOutput")
            }

            process.exitValue() == 0
        } catch (e: Exception) {
            Log.e(ShizukuKit.TAG, "Failed to start service: ${e.message}", e)
            false
        }
    }

    private fun forceStopService(packageName: String): Boolean {
        if (!Shizuku.pingBinder()) {
            Log.e(ShizukuKit.TAG, "Shizuku is not running")
            return false
        }

        // 检查Shizuku权限
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e(ShizukuKit.TAG, "Shizuku permission not granted")
            return false
        }

        return try {
            val service: IShizukuService? = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            if (service == null) {
                Log.e(ShizukuKit.TAG, "Shizuku service not available")
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

            // 使用線程來實現超時
            val processThread = Thread {
                try {
                    process.waitFor()
                } catch (e: Exception) {
                    Log.e(ShizukuKit.TAG, "Process wait error: ${e.message}",e)
                }
            }
            processThread.start()
            processThread.join(10000) // 10秒超時

            if (processThread.isAlive) {
                process.destroy()
                processThread.interrupt()
                Log.e(ShizukuKit.TAG, "forceStopService command timed out")
                return false
            }

            val output = reader.readText()
            val errorOutput = errorReader.readText()

            if (output.isNotEmpty()) {
                Log.i(ShizukuKit.TAG, "Command output: $output")
            }

            if (errorOutput.isNotEmpty()) {
                Log.e(ShizukuKit.TAG, "Command error: $errorOutput")
            }

            process.exitValue() == 0
        } catch (e: Exception) {
            Log.e(ShizukuKit.TAG, "Failed to force stop service: ${e.message}", e)
            false
        }
    }
}
