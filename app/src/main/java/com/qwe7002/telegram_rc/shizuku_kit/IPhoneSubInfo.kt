package com.qwe7002.telegram_rc.shizuku_kit

import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.android.internal.telephony.IPhoneSubInfo
import com.qwe7002.telegram_rc.MMKV.Const
import com.tencent.mmkv.MMKV
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
import java.io.InputStreamReader

class IPhoneSubInfo {
    private fun getIPhoneSubInfo(): IPhoneSubInfo? {
        val binder: IBinder =
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService("iphonesubinfo"))
        return IPhoneSubInfo.Stub.asInterface(binder)
    }

    fun getDefaultIMSIWithShizuku(): String {
        val subInfo = getIPhoneSubInfo()

        if (subInfo == null) {
            return ""
        }
        return subInfo.getSubscriberId("com.android.shell")
    }

    fun getIMSIWithShizuku(subid: Int): String {
        val subInfo = getIPhoneSubInfo()
        if (subInfo == null) {
            return ""
        }

        return subInfo.getSubscriberIdForSubscriber(subid, "com.android.shell", null)
    }

    fun getDefaultIMSIFallbackWithShizuku(): String {
        val TAG = "getDefaultIMSIFallback"
        val shizukuMMKV = MMKV.mmkvWithID(Const.SHIZUKU_MMKV_ID)
        val service: IShizukuService? = IShizukuService.Stub.asInterface(Shizuku.getBinder())
        if (service == null) {
            Log.e(TAG, "Shizuku service not available")
            return ""
        }
        val process = service.newProcess(
            arrayOf(
                "service",
                "call",
                "iphonesubinfo",
                shizukuMMKV.getString("getSubscriberId","8"),
                "s16",
                "com.android.shell",
            ),
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
        return ParcelParser.parseParcelData(output)
    }

    fun getIMSIFallbackWithShizuku(subid: Int): String {
        val shizukuMMKV = MMKV.mmkvWithID(Const.SHIZUKU_MMKV_ID)
        val TAG = "getIMSIFallback"
        val service: IShizukuService? = IShizukuService.Stub.asInterface(Shizuku.getBinder())
        if (service == null) {
            Log.e(TAG, "Shizuku service not available")
            return ""
        }
        val process = service.newProcess(
            arrayOf(
                "service",
                "call",
                "iphonesubinfo",
                shizukuMMKV.getString("getSubscriberIdForSubscriber","10"),
                "i32",
                subid.toString(),
                "s16",
                "com.android.shell",
                "s16",
                "null"
            ),
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
        return ParcelParser.parseParcelData(output)
    }
}
