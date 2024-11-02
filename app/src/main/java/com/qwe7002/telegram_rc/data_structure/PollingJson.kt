package com.qwe7002.telegram_rc.data_structure

import com.google.gson.annotations.SerializedName

@Suppress("unused")
class PollingJson {
    @SerializedName(value = "allowed_updates")
    val allowedUpdates: Array<String> = arrayOf("message", "channel_post", "callback_query")
    var offset: Long = 0
    var timeout: Int = 0
}
