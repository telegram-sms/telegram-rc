package com.qwe7002.telegram_rc.data_structure;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
@SuppressWarnings({"unused", "RedundantSuppression"})
public class replyMarkupKeyboard {
    public static ArrayList<InlineKeyboardButton> get_inline_keyboard_obj(String text, String callbackData) {
        replyMarkupKeyboard.InlineKeyboardButton button = new replyMarkupKeyboard.InlineKeyboardButton();
        button.text = text;
        button.callbackData = callbackData;
        ArrayList<replyMarkupKeyboard.InlineKeyboardButton> button_ArrayList = new ArrayList<>();
        button_ArrayList.add(button);
        return button_ArrayList;
    }

    public static class keyboard_markup {
        @SerializedName(value = "inline_keyboard")
        public ArrayList<ArrayList<InlineKeyboardButton>> inlineKeyboard;

        final boolean one_time_keyboard = true;
    }

    public static class InlineKeyboardButton {
        String text;
        @SerializedName(value = "callback_data")
        String callbackData;
    }
}

