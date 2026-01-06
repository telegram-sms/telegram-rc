package com.qwe7002.telegram_rc.data_structure.telegram

import com.google.gson.annotations.SerializedName
import com.qwe7002.telegram_rc.ChatService

@Suppress("unused")
class RequestMessage {
    @SerializedName(value = "disable_web_page_preview")
    val disableWebPagePreview: Boolean = true

    @SerializedName(value = "message_id")
    var messageId: Long = 0

    @SerializedName(value = "parse_mode")
    lateinit var parseMode: String

    @SerializedName(value = "chat_id")
    lateinit var chatId: String
    lateinit var text: String

    @SerializedName(value = "message_thread_id")
    var messageThreadId: String? = null

    @SerializedName(value = "reply_markup")
    var keyboardMarkup: ChatService.ReplyMarkupKeyboard.KeyboardMarkup? = null

    @SerializedName(value = "disable_notification")
    var disableNotification: Boolean = false
}

