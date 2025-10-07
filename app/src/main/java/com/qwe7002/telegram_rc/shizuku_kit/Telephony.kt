package com.qwe7002.telegram_rc.shizuku_kit

import android.os.IBinder
import android.content.Context
import android.util.Log
import com.android.internal.telephony.ITelephony
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class Telephony {
    companion object {
        private const val TAG = "TelephonyManager"
    }

    private fun getITelephony(): ITelephony? {
        return try {
            val binder: IBinder = ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService(Context.TELEPHONY_SERVICE)
            )
            ITelephony.Stub.asInterface(binder)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get ITelephony", e)
            null
        }
    }

    fun setSimPowerState(slotIndex: Int, powerUp: Boolean): Boolean {
        var state = 0
        if (powerUp) {
            state = 1
        }
        try {
            val telephony = getITelephony()
            if (telephony != null) {
                telephony.setSimPowerStateForSlot(slotIndex, state)
                Log.d(TAG, "Successfully set SIM power state for slot $slotIndex to $powerUp")
                return true
            } else {
                Log.e(TAG, "ITelephony is null, cannot set SIM power state")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set SIM power state for slot $slotIndex to $powerUp", e)
            return false
        }
    }

    fun setSimPowerStateFallBack(slotIndex: Int, powerUp: Boolean): Boolean {
        val TAG = "setSimPowerStateFallBack"
        val service: IShizukuService? = IShizukuService.Stub.asInterface(Shizuku.getBinder())
        if (service == null) {
            Log.e(TAG, "Shizuku service not available")
            return false
        }
        val power = if (powerUp) {
            1
        } else {
            0
        }
        val process = service.newProcess(
            arrayOf(
                "service",
                "call",
                "phone",
                "185",
                "i32",
                slotIndex.toString(),
                "i32",
                power.toString()
            ),
            null,
            null
        )
        val reader = BufferedReader(InputStreamReader(process.inputStream as InputStream?))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream as InputStream?))

        process.waitFor()
        val output = reader.readText()
        val errorOutput = errorReader.readText()

        if (output.isNotEmpty()) {
            Log.i(TAG, "Command output: $output")
        }

        if (errorOutput.isNotEmpty()) {
            Log.e(TAG, "Command error: $errorOutput")
        }

        return process.exitValue() == 0
    }
}
