package com.qwe7002.telegram_rc.data_structure;

public class polling_json {
    @SuppressWarnings({"unused", "RedundantSuppression"})
    public final String[] allowed_updates = {"message", "channel_post", "callback_query"};
    public long offset;
    public int timeout;
}
