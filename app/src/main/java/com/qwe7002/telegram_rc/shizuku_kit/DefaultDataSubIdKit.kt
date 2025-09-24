package com.qwe7002.telegram_rc.shizuku_kit

import android.annotation.SuppressLint
import android.os.IBinder
import android.util.Log
import com.android.internal.telephony.ISub
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper


class DefaultDataSubIdKit {
    @SuppressLint("PrivateApi")
    fun setDefaultDataSubIdWithShizuku(subId: Int) {
        try {
            // 获取 SubscriptionManager 的 Binder
            val binder: IBinder = SystemServiceHelper.getSystemService("isub")

            // 包装 Binder 以提升权限
            val wrapper = ShizukuBinderWrapper(binder)
            
            // 通过 Stub 获取 ISub 实例
            val isub = ISub.Stub.asInterface(wrapper)
            
            // 调用 setDefaultDataSubId 方法
            isub.setDefaultDataSubId(subId)

            Log.d("Shizuku", "Default Data SubId has been set to: $subId")
        } catch (e: Exception) {
            Log.e("Shizuku", "Call failed: " + e.message)
        }
    }

}
