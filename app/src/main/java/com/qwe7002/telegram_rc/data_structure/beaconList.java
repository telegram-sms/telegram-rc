package com.qwe7002.telegram_rc.data_structure;

import java.util.ArrayList;

public class beaconList {
    public static ArrayList<BeaconModel> beacons;

    public static String beaconItemName(String uuid,int major, int minor) {
        return "UUID:"+uuid+"Major:"+major+"Minor:"+minor;
    }
}
