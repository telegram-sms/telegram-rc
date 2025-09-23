package com.qwe7002.telegram_rc.static_class

import android.Manifest
import android.content.Context
import android.telephony.SubscriptionManager
import androidx.annotation.RequiresPermission


object Phone {
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    @JvmStatic
    public fun getPhoneNumber(context: Context, slot: Int): String {
        val sm =
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val list = sm.activeSubscriptionInfoList
        if (list != null && !list.isEmpty()) {
            val phoneNumber = list[slot]!!.number
            return phoneNumber
        }
        return ""
    }
}
