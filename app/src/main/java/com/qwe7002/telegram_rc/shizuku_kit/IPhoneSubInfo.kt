package com.qwe7002.telegram_rc.shizuku_kit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.android.internal.telephony.IPhoneSubInfo
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class IPhoneSubInfo {
    private fun getIPhoneSubInfo(): IPhoneSubInfo? {
        val binder: IBinder =
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService("iphonesubinfo"))

// 转换为 IPhoneSubInfo 接口
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

}
