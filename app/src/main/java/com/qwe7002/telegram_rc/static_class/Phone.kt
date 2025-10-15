package com.qwe7002.telegram_rc.static_class

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.shizuku_kit.IPhoneSubInfo
import com.qwe7002.telegram_rc.static_class.Other.getActiveCard
import com.tencent.mmkv.MMKV


object Phone {
    @RequiresPermission(allOf = [Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS])
    @JvmStatic
    fun getPhoneNumber(context: Context, slot: Int): String {
        val subscriptionManager =
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val subID = Other.getSubId(context, slot)
            return if (subID >= 0) {
                subscriptionManager.getPhoneNumber(subID)
            } else {
                subscriptionManager.getPhoneNumber(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID)
            }
        } else {
            val list = subscriptionManager.activeSubscriptionInfoList
            if (list != null && list.isNotEmpty()) {
                if (list.size <= slot) {
                    val info = list.getOrNull(0)
                    @Suppress("DEPRECATION")
                    return info?.number ?: ""
                }
                val info = list.getOrNull(slot)
                @Suppress("DEPRECATION")
                return info?.number ?: ""
            }
        }
        return ""
    }

    @JvmStatic
    fun getIMSICache(context: Context) {
        val imsiCache = MMKV.mmkvWithID(Const.IMSI_MMKV_ID)
        val phoneInfo = IPhoneSubInfo()
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val phoneCount = getActiveCard(context)
            Log.d("getIMSICache", "getIMSICache: $phoneCount")
            if (phoneCount == 1) {
                val phone = phoneInfo.getDefaultIMSIWithShizuku()
                Log.i("getIMSICache", "getIMSICache: $phone")
                if (phone.isNotEmpty()) {
                    imsiCache.putString(getPhoneNumber(context, 0), phone)
                } else {
                    throw Exception("Permission denied")
                }
            } else {
                for (i in 0 until phoneCount) {
                    val subid = Other.getSubId(context, i)
                    val phone = phoneInfo.getIMSIWithShizuku(subid)
                    Log.i("getIMSICache", "getIMSICache: $phone")
                    if (phone.isNotEmpty()) {
                        imsiCache.putString(getPhoneNumber(context, i), phone)
                    } else {
                        throw Exception("Permission denied")
                    }
                }
            }
        } else {
            throw Exception("Permission denied")
        }
    }

    @JvmStatic
    fun getIMSICacheFallback(context: Context) {
        val imsiCache = MMKV.mmkvWithID(Const.IMSI_MMKV_ID)
        val phoneInfo = IPhoneSubInfo()
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val phoneCount = getActiveCard(context)
            Log.d("getIMSICache", "getIMSICache: $phoneCount")
            if (phoneCount == 1) {
                val phone = phoneInfo.getDefaultIMSIFallbackWithShizuku()
                Log.i("getIMSICache", "getIMSICache: $phone")
                if (phone.isNotEmpty()) {
                    imsiCache.putString(getPhoneNumber(context, 0), phone)
                } else {
                    throw Exception("Permission denied")
                }
            } else {
                for (i in 0 until phoneCount) {
                    val subid = Other.getSubId(context, i)
                    val phone = phoneInfo.getIMSIFallbackWithShizuku(subid)
                    Log.i("getIMSICache", "getIMSICache: $phone")
                    if (phone.isNotEmpty()) {
                        imsiCache.putString(getPhoneNumber(context, i), phone)
                    } else {
                        throw Exception("Permission denied")
                    }
                }
            }
        } else {
            throw Exception("Permission denied")
        }
    }
}
