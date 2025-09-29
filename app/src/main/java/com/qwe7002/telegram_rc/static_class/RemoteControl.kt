package com.qwe7002.telegram_rc.static_class

import android.content.Context
import com.fitc.wifihotspot.TetherManager
import com.qwe7002.telegram_rc.MMKV.Const
import com.tencent.mmkv.MMKV

object RemoteControl {
    @JvmStatic
    fun enableHotspot(context: Context, mode: Int) {
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("tether", true)
        val manager = TetherManager(context)
        manager.startTethering(mode, null)
    }

    @JvmStatic
    fun disableHotspot(context: Context, mode: Int) {
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("tether", false)
        val manager = TetherManager(context)
        manager.stopTethering(mode)
    }

    @JvmStatic
    fun isHotspotActive(context: Context): Boolean {
        val manager = TetherManager(context)
        MMKV.mmkvWithID(Const.STATUS_MMKV_ID).putBoolean("tether", manager.isTetherActive())
        return manager.isTetherActive()
    }
}
