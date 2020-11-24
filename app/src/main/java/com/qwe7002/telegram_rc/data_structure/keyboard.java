package com.qwe7002.telegram_rc.data_structure;

import java.util.ArrayList;

public class keyboard {
    public static ArrayList<InlineKeyboardButton> get_inline_keyboard_obj(String text, String callback_data) {
        keyboard.InlineKeyboardButton button = new keyboard.InlineKeyboardButton();
        button.text = text;
        button.callback_data = callback_data;
        ArrayList<keyboard.InlineKeyboardButton> button_ArrayList = new ArrayList<>();
        button_ArrayList.add(button);
        return button_ArrayList;
    }

    public static class keyboard_markup {
        public ArrayList<ArrayList<InlineKeyboardButton>> inline_keyboard;
        @SuppressWarnings({"unused", "RedundantSuppression"})
        boolean one_time_keyboard = true;
    }

    public static class InlineKeyboardButton {
        String text;
        String callback_data;
    }
}

