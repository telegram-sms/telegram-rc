package com.qwe7002.telegram_rc.data_structure

import com.google.gson.annotations.SerializedName
import com.qwe7002.telegram_rc.data_structure.ReplyMarkupKeyboard.KeyboardMarkup

@Suppress("unused")
class RequestMessage {
    @SerializedName(value = "disable_web_page_preview")
    val disableWebPagePreview: Boolean = true

    @SerializedName(value = "message_id")
    var messageId: Long = 0

    @SerializedName(value = "parse_mode")
    var parseMode: String? = null

    @SerializedName(value = "chat_id")
    var chatId: String? = null
    var text: String? = null

    @SerializedName(value = "message_thread_id")
    var messageThreadId: String? = null

    @SerializedName(value = "reply_markup")
    var keyboardMarkup: KeyboardMarkup? = null
}

