package com.qwe7002.telegram_rc.data_structure;

import com.google.gson.annotations.SerializedName;

@SuppressWarnings({"unused", "RedundantSuppression"})
public class requestMessage {
    public final boolean disable_web_page_preview = true;
    @SerializedName(value = "message_id")
    public long messageId;
    @SerializedName(value = "parse_mode")
    public String parseMode;
    @SerializedName(value = "chat_id")
    public String chatId;
    public String text;
    @SerializedName(value = "message_thread_id")
    public String messageThreadId;
    @SerializedName(value = "reply_markup")
    public replyMarkupKeyboard.keyboardMarkup keyboardMarkup;
}

