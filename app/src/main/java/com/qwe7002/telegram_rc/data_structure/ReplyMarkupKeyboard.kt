package com.qwe7002.telegram_rc.data_structure

import com.google.gson.annotations.SerializedName

@Suppress("unused")
object ReplyMarkupKeyboard {
    fun getInlineKeyboardObj(
        text: String,
        callbackData: String
    ): ArrayList<InlineKeyboardButton> {
        val button = InlineKeyboardButton()
        button.text = text
        button.callbackData = callbackData
        val buttonArraylist = ArrayList<InlineKeyboardButton>()
        buttonArraylist.add(button)
        return buttonArraylist
    }

    class KeyboardMarkup {
        @SerializedName(value = "inline_keyboard")
        lateinit var inlineKeyboard: ArrayList<ArrayList<InlineKeyboardButton>>

        val oneTimeKeyboard: Boolean = true
    }

    class InlineKeyboardButton {
        lateinit var text: String

        @SerializedName(value = "callback_data")
        lateinit var callbackData: String
    }
}

