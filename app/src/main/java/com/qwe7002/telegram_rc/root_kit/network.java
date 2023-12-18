package com.qwe7002.telegram_rc.root_kit;

public class network {


    public static void setWifi(boolean enable) {
        String state = "disable";
        if (enable) {
            state = "enable";
        }
        shell.runShellCommand("svc wifi " + state);
    }

    public static void setData(boolean enable) {
        String state = "disable";
        if (enable) {
            state = "enable";
        }
        shell.runShellCommand("svc data " + state);
    }


    public static void addDummyDevice(String ip_addr) {
        shell.runShellCommand("ip link add dummy0 type dummy\nip addr add " + ip_addr + "/32 dev dummy0");
    }

    public static void delDummyDevice() {
        shell.runShellCommand("ip link del dummy0");
    }
}
