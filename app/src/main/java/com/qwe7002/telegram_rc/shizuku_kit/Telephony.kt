package com.qwe7002.telegram_rc.shizuku_kit

import android.os.IBinder
import android.content.Context
import android.util.Log
import com.android.internal.telephony.ITelephony
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

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
        if(powerUp){
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
}
