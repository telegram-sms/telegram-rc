package com.qwe7002.telegram_rc.static_class

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType

object Const {
    const val SYSTEM_CONFIG_VERSION: Int = 1
    const val BROADCAST_STOP_SERVICE: String = "com.qwe7002.telegram_rc.stop_all"

    @JvmField
    val JSON: MediaType = "application/json; charset=utf-8".toMediaType()
    const val RESULT_CONFIG_JSON: Int = 1
}
