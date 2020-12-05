package com.qwe7002.telegram_rc;

import android.util.Log;

import com.qwe7002.telegram_rc.config.beacon;
import com.qwe7002.telegram_rc.config.proxy;

import java.util.ArrayList;
import java.util.List;

import io.paperdb.Paper;

public class update_to_version1 {
    public void update() {
        Log.i("from v1", "onReceive: Start the configuration file conversion");
        List<String> notify_listen_list = Paper.book().read("notify_listen_list", new ArrayList<>());
        ArrayList<String> black_keyword_list = Paper.book().read("black_keyword_list", new ArrayList<>());
        com.qwe7002.telegram_rc.beacon_config beacon_config_item = Paper.book().read("beacon_config", new com.qwe7002.telegram_rc.beacon_config());
        ArrayList<String> beacon_listen_list = Paper.book().read("beacon_address", new ArrayList<>());
        com.qwe7002.telegram_rc.proxy_config outdated_proxy_item;
        boolean is_convert = Paper.book("system_config").contains("convert");
        if (is_convert) {
            outdated_proxy_item = Paper.book("system_config").read("proxy_config", new com.qwe7002.telegram_rc.proxy_config());
        } else {
            outdated_proxy_item = Paper.book().read("proxy_config", new com.qwe7002.telegram_rc.proxy_config());
        }
        //Replacement object
        proxy proxy_item = new proxy();
        proxy_item.dns_over_socks5 = outdated_proxy_item.dns_over_socks5;
        proxy_item.enable = outdated_proxy_item.enable;
        proxy_item.password = outdated_proxy_item.password;
        proxy_item.username = outdated_proxy_item.username;
        proxy_item.host = outdated_proxy_item.proxy_host;
        proxy_item.port = outdated_proxy_item.proxy_port;
        beacon beacon_item = new beacon();
        beacon_item.delay = beacon_config_item.delay;
        beacon_item.disable_count = beacon_config_item.disable_count;
        beacon_item.enable_count = beacon_config_item.enable_count;
        beacon_item.opposite = beacon_config_item.opposite;
        beacon_item.RSSI_strenght = beacon_config_item.RSSI_strenght;
        beacon_item.use_vpn_hotspot = beacon_config_item.use_vpn_hotspot;
        if (is_convert) {
            Paper.book("system_config").delete("convert");
            Paper.book("system_config").write("proxy_config", proxy_item);
            Paper.book("beacon_config").write("config", beacon_config_item).write("address", beacon_listen_list);
        } else {
            Paper.book("system_config").write("notify_listen_list", notify_listen_list).write("block_keyword_list", black_keyword_list).write("proxy_config", proxy_item);
            Paper.book("beacon_config").write("config", beacon_item).write("address", beacon_listen_list);
        }
        Paper.book("system_config").write("version", 1);
        Paper.book().destroy();
    }
}
