package com.qwe7002.telegram_rc.static_class;

import android.content.Context;
import android.content.Intent;

import com.qwe7002.telegram_rc.ResendService;

import java.util.ArrayList;

import io.paperdb.Paper;

public class resend {
    public static void addResendLoop(Context context, String message) {
        ArrayList<String> resend_list;
        resend_list = Paper.book().read("resend_list", new ArrayList<>());
        assert resend_list != null;
        resend_list.add(message);
        Paper.book().write("resend_list", resend_list);
        start_resend_service(context);
    }

    public static void start_resend_service(Context context) {
        Intent intent = new Intent(context, ResendService.class);
        context.startForegroundService(intent);
    }
}
