package com.qwe7002.telegram_rc.static_class

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.BATTERY_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.ParcelFileDescriptor
import android.util.Log
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.R
import moe.shizuku.server.IShizukuService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object Battery {
    fun getBatteryInfo(context: Context): String {
        val batteryManager =
            checkNotNull(context.getSystemService(BATTERY_SERVICE) as BatteryManager)
        var batteryLevel =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (batteryLevel > 100) {
            Log.i(
                Const.TAG,
                "The previous battery is over 100%, and the correction is 100%."
            )
            batteryLevel = 100
        }
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = checkNotNull(context.registerReceiver(null, intentFilter))
        val chargeStatus = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val batteryStringBuilder = StringBuilder().append(batteryLevel).append("%")
        when (chargeStatus) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> batteryStringBuilder.append(
                " ("
            ).append(context.getString(R.string.charging)).append(")")

            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> when (batteryStatus.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                -1
            )) {
                BatteryManager.BATTERY_PLUGGED_AC, BatteryManager.BATTERY_PLUGGED_USB, BatteryManager.BATTERY_PLUGGED_WIRELESS -> batteryStringBuilder.append(
                    " ("
                ).append(context.getString(R.string.not_charging)).append(")")
            }
        }
        return batteryStringBuilder.toString()
    }

    @SuppressLint("PrivateApi")
    fun getBatteryCapacity(context: Context): Double {
        HiddenApiBypass.addHiddenApiExemptions("")
        val ppClass = Class.forName("com.android.internal.os.PowerProfile")
        val ctor = ppClass.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
        val pp = ctor.newInstance(context)
        val mAh = HiddenApiBypass.invoke(ppClass, pp, "getBatteryCapacity") as Double
        return mAh
    }

    @JvmStatic
    fun getLearnedBatteryCapacity(): String? {
        return try {
            val service: IShizukuService? = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            if (service == null) {
                Log.e(Const.TAG, "Shizuku service not available")
                return null
            }
            Log.d(Const.TAG, "Shizuku service obtained successfully")

            // Use grep to filter output directly in the shell command
            val process = service.newProcess(
                arrayOf("sh", "-c", "dumpsys batterystats | grep 'Max learned battery capacity'"),
                null,
                null
            )

            val reader = BufferedReader(
                InputStreamReader(
                    ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
                )
            )

            val line = reader.readLine()
            reader.close()

            val learnedCapacity =
                line?.trim()?.replace("Max learned battery capacity: ", "")?.replace(" mAh", "")

            process.waitFor()
            learnedCapacity
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(Const.TAG, "Exception occurred: ${e.message}", e)
            null
        }
    }

}
