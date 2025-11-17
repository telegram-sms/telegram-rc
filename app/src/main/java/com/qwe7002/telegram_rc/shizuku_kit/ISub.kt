package com.qwe7002.telegram_rc.shizuku_kit

import android.annotation.SuppressLint
import android.content.Context
import android.os.IBinder
import android.util.Log
import android.os.ParcelFileDescriptor
import com.android.internal.telephony.ISub
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.LogManage
import com.tencent.mmkv.MMKV
import moe.shizuku.server.IShizukuService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
import java.io.InputStreamReader


class ISub {
    @SuppressLint("PrivateApi")
    fun setDefaultDataSubIdWithShizuku(subId: Int) {
        try {
            val binder: IBinder = SystemServiceHelper.getSystemService("isub")
            val wrapper = ShizukuBinderWrapper(binder)
            val isub = ISub.Stub.asInterface(wrapper)
            isub.setDefaultDataSubId(subId)
            Log.d("setDefaultDataSubId", "Default Data SubId has been set to: $subId")
        } catch (e: Exception) {
            Log.e("setDefaultDataSubId", "Call failed: " + e.message)
        }
    }

    /**
     * Get ISub service interface with Shizuku privileges
     */
    @SuppressLint("PrivateApi")
    private fun getISubService(): Any? {
        return try {
            HiddenApiBypass.addHiddenApiExemptions("")

            // Get the ISub service binder via Shizuku
            val binder = SystemServiceHelper.getSystemService("isub")
            if (binder == null) {
                Log.e(this::class.java.simpleName, "Failed to get ISub service binder")
                return null
            }

            // Wrap the binder with ShizukuBinderWrapper
            val wrappedBinder = ShizukuBinderWrapper(binder)

            // Use the ISub.Stub.asInterface pattern to get the service interface
            val iSubStubClass = Class.forName("com.android.internal.telephony.ISub\$Stub")
            val asInterfaceMethod =
                iSubStubClass.getMethod("asInterface", Class.forName("android.os.IBinder"))

            val iSubService = asInterfaceMethod.invoke(null, wrappedBinder)
            Log.d(this::class.java.simpleName, "Successfully obtained ISub service")
            iSubService
        } catch (e: Exception) {
            Log.e(this::class.java.simpleName, "Failed to get ISub service: ${e.message}", e)
            null
        }
    }

    fun setDefaultDataSubIdFallback(subId: Int): Boolean {
        return try {
            val iSubService = getISubService()
            if (iSubService == null) {
                Log.e(this::class.java.simpleName, "Failed to get ISub service")
                return false
            }

            // Use reflection to call setDefaultDataSubId method
            val setDefaultDataSubIdMethod = iSubService.javaClass.getMethod(
                "setDefaultDataSubId",
                Int::class.javaPrimitiveType
            )

            setDefaultDataSubIdMethod.invoke(iSubService, subId)
            Log.i(this::class.java.simpleName, "Successfully set default data subId to: $subId")
            true
        } catch (e: Exception) {
            Log.e(
                this::class.java.simpleName,
                "Exception during setDefaultDataSubId: ${e.message}",
                e
            )
            false
        }
    }
    /*fun setDefaultDataSubIdFallback(subId: Int) {
        val shizukuMMKV = MMKV.mmkvWithID(Const.SHIZUKU_MMKV_ID)
        val TAG = "setDefaultDataSubId"
        val service: IShizukuService? = IShizukuService.Stub.asInterface(Shizuku.getBinder())
        if (service == null) {
            Log.e(TAG, "Shizuku service not available")
            return
        }

        val process = service.newProcess(
            arrayOf("service", "call", "isub", shizukuMMKV.getString("setDefaultDataSubId", "31"), "i32", subId.toString()),
            null,
            null
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
    }*/

}
