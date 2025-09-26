package com.qwe7002.telegram_rc.shizuku_kit

import android.os.IBinder
import com.android.internal.telephony.IPhoneSubInfo
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

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

}
