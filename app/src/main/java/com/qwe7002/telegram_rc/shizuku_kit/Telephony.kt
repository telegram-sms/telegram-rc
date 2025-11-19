package com.qwe7002.telegram_rc.shizuku_kit

import android.annotation.SuppressLint
import android.os.IBinder
import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.android.internal.telephony.ITelephony
import com.qwe7002.telegram_rc.MMKV.Const
import com.tencent.mmkv.MMKV
import moe.shizuku.server.IShizukuService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
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

    /**
     * Get ITelephony service interface with Shizuku privileges
     */
    @SuppressLint("PrivateApi")
    private fun getITelephonyService(): Any? {
        return try {
            HiddenApiBypass.addHiddenApiExemptions("")

            // Get the ITelephony service binder via Shizuku
            val binder = SystemServiceHelper.getSystemService(Context.TELEPHONY_SERVICE)
            if (binder == null) {
                Log.e(this::class.java.simpleName, "Failed to get ITelephony service binder")
                return null
            }

            // Wrap the binder with ShizukuBinderWrapper
            val wrappedBinder = ShizukuBinderWrapper(binder)

            // Use the ITelephony.Stub.asInterface pattern to get the service interface
            val iTelephonyStubClass =
                Class.forName("com.android.internal.telephony.ITelephony\$Stub")
            val asInterfaceMethod = iTelephonyStubClass.getMethod(
                "asInterface",
                Class.forName("android.os.IBinder")
            )

            val iTelephonyService = asInterfaceMethod.invoke(null, wrappedBinder)
            Log.d(this::class.java.simpleName, "Successfully obtained ITelephony service")
            iTelephonyService
        } catch (e: Exception) {
            Log.e(this::class.java.simpleName, "Failed to get ITelephony service: ${e.message}", e)
            null
        }
    }


    fun setSimPowerState(slotIndex: Int, powerUp: Boolean): Boolean {
        return try {
            val iTelephonyService = getITelephonyService()
            if (iTelephonyService == null) {
                Log.e(this::class.java.simpleName, "Failed to get ITelephony service")
                return false
            }

            // Try new signature with int state (Android 10+)
            // Signature: setSimPowerStateForSlot(int slotIndex, int state)
            try {
                val setSimPowerStateForSlotMethod = iTelephonyService.javaClass.getMethod(
                    "setSimPowerStateForSlot",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val state = if (powerUp) 1 else 0

                setSimPowerStateForSlotMethod.invoke(iTelephonyService, slotIndex, state)

                Log.i(
                    this::class.java.simpleName,
                    "Successfully set SIM power state for slot $slotIndex to state $state"
                )

                return true
            } catch (e: NoSuchMethodException) {
                Log.d(
                    this::class.java.simpleName,
                    "Method with int state not found, trying boolean signature: ${e.message}"
                )
            }
            // Fallback to old signature with boolean (Android 8-9)
            // Signature: setSimPowerStateForSlot(int slotIndex, boolean powerUp)
            val setSimPowerStateForSlotMethod = iTelephonyService.javaClass.getMethod(
                "setSimPowerStateForSlot",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )

            setSimPowerStateForSlotMethod.invoke(iTelephonyService, slotIndex, powerUp)

            Log.i(
                this::class.java.simpleName,
                "Successfully set SIM power state for slot $slotIndex to ${if (powerUp) "UP" else "DOWN"}"
            )
            true
        } catch (e: Exception) {
            Log.e(
                this::class.java.simpleName,
                "Exception during setSimPowerStateForSlot: ${e.message}",
                e
            )
            false
        }
    }
}
