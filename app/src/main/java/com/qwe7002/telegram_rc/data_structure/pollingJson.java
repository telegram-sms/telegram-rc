package com.qwe7002.telegram_rc.data_structure;
@SuppressWarnings({"unused", "RedundantSuppression"})
public class pollingJson {
    public final String[] allowed_updates = {"message", "channel_post", "callback_query"};
    public long offset;
    public int timeout;
}
