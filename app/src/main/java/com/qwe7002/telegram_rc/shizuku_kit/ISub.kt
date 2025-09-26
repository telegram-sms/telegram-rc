package com.qwe7002.telegram_rc.shizuku_kit

import android.annotation.SuppressLint
import android.os.IBinder
import android.util.Log
import com.android.internal.telephony.ISub
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper


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

}
