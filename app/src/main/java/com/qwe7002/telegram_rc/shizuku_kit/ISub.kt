package com.qwe7002.telegram_rc.shizuku_kit

import android.annotation.SuppressLint
import android.os.IBinder
import android.util.Log
import android.os.ParcelFileDescriptor
import com.android.internal.telephony.ISub
import com.qwe7002.telegram_rc.MMKV.Const
import com.tencent.mmkv.MMKV
import moe.shizuku.server.IShizukuService
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

    fun setDefaultDataSubIdFallback(subId: Int) {
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
    }

}
