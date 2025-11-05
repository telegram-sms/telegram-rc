package com.qwe7002.telegram_rc.static_class

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.fitc.wifihotspot.TetherManager
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.shizuku_kit.TetheringManagerShizuku
import com.tencent.mmkv.MMKV

object RemoteControl {
    @JvmStatic
    fun enableHotspot(context: Context, mode: Int) {
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("tether", true)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            val manager = TetherManager(context)
            manager.startTethering(mode, null)
        } else {
            TetheringManagerShizuku.startTethering(context, mode)
        }
    }

    @JvmStatic
    fun disableHotspot(context: Context, mode: Int) {
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("tether", false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            val manager = TetherManager(context)
            manager.stopTethering(mode)
        } else {
            TetheringManagerShizuku.stopTethering(context, mode)
        }
    }

    @JvmStatic
    fun isHotspotActive(context: Context): Boolean {
        val manager = TetherManager(context)
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("tether", manager.isTetherActive())
        return manager.isTetherActive()
    }
}
