package com.qwe7002.telegram_rc.static_class

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.fitc.wifihotspot.TetherManager
import com.qwe7002.telegram_rc.MMKV.Const
import com.tencent.mmkv.MMKV

object RemoteControl {
    @JvmStatic
    fun enableHotspot(context: Context, mode: Int) {
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("tether", true)
        if (Build.VERSION.SDK_INT < 36) {
            val manager = TetherManager(context)
            manager.startTethering(mode, null)
        } else {
            Log.i(this::class.java.simpleName, "enableHotspot: use New API")
            val intent = Intent().apply {
                setClassName("dev.shadoe.delta", "dev.shadoe.delta.SoftApBroadcastReceiver")
                action = "dev.shadoe.delta.action.START_SOFT_AP"
            }
            context.sendBroadcast(intent)
        }
    }

    @JvmStatic
    fun disableHotspot(context: Context, mode: Int) {
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("tether", false)
        if (Build.VERSION.SDK_INT < 36) {
            val manager = TetherManager(context)
            manager.stopTethering(mode)
        } else {
            Log.i(this::class.java.simpleName, "disableHotspot: use New API")
            val intent = Intent().apply {
                setClassName("dev.shadoe.delta", "dev.shadoe.delta.SoftApBroadcastReceiver")
                action = "dev.shadoe.delta.action.STOP_SOFT_AP"
            }
            context.sendBroadcast(intent)
        }
    }

    @JvmStatic
    fun isHotspotActive(context: Context): Boolean {
        val manager = TetherManager(context)
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("tether", manager.isTetherActive())
        return manager.isTetherActive()
    }

    @JvmStatic
    fun isDeltaExist(context: Context): Boolean {
        var info: ApplicationInfo?
        try {
            info = context.packageManager.getApplicationInfo("dev.shadoe.delta", 0)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            info = null
        }

        return info != null
    }
}
