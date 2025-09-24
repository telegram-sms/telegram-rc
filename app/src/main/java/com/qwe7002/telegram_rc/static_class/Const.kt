package com.qwe7002.telegram_rc.static_class

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType

object Const {
    const val SYSTEM_CONFIG_VERSION: Int = 1

    @JvmField
    val JSON: MediaType = "application/json; charset=utf-8".toMediaType()
    const val RESULT_CONFIG_JSON: Int = 1

    const val ROOT_MMKV_ID: String = "root"
    const val CHAT_INFO_MMKV_ID: String = "chat_info"
    const val BEACON_MMKV_ID: String = "beacon"
    const val STATUS_MMKV_ID: String = "status"
    const val PROXY_MMKV_ID: String = "proxy"
    const val RESEND_MMKV_ID: String = "resend"
    const val SPAM_MMKV_ID: String = "spam"
    const val UPGRADE_MMKV_ID: String = "upgrade"
    const val IMSI_MMKV_ID: String = "IMSI"

}
