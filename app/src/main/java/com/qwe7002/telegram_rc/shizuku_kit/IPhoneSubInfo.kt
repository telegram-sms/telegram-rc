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

    fun getIMSIWithShizuku(context: Context, slotIndex: Int): String {
        val subInfo = getIPhoneSubInfo()
        if (subInfo == null) {
            return ""
        }
        val subid = convertSlotIndexToSubId(context, slotIndex)

        return subInfo.getSubscriberIdForSubscriber(subid, "com.android.shell", null)
    }

    /**
     * 将slotIndex转换为subId
     * @param context 上下文
     * @param slotIndex 卡槽索引 (0 或 1)
     * @return subId，获取失败返回-1
     */
    fun convertSlotIndexToSubId(context: Context, slotIndex: Int): Int {
        return try {
            // 检查权限
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("convertSlotIndexToSubId", "READ_PHONE_STATE permission not granted")
                return -1
            }

            val subscriptionManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subscriptionInfo =
                subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex)
            subscriptionInfo?.subscriptionId ?: -1
        } catch (e: Exception) {
            Log.e("convertSlotIndexToSubId", "Error converting slotIndex to subId", e)
            -1
        }
    }
}
