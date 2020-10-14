package com.qwe7002.root_kit;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class network {
    private static void set_airplane_mode(boolean enable) {
        int set = 0;
        String state = "true";
        if (enable) {
            set = 1;
            state = "false";
        }
        shell.run_shell_command("settings put global airplane_mode_on " + set + " \nam broadcast -a android.intent.action.AIRPLANE_MODE --ez state " + state);
    }

    public static void set_data_sim(int sub_id) {
        shell.run_shell_command("settings put global multi_sim_data_call " + sub_id + " \nam broadcast -a android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
    }

    public static void wifi_set_enable(boolean enable) {
        String state = "disable";
        if (enable) {
            state = "enable";
        }
        shell.run_shell_command("svc wifi " + state);
    }

    public static void data_set_enable(boolean enable) {
        String state = "disable";
        if (enable) {
            state = "enable";
        }
        shell.run_shell_command("svc data " + state);
    }

    public static void restart_network() {
        network.set_airplane_mode(true);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        network.set_airplane_mode(false);
    }

    public static void add_dummy_device(String ip_addr) {
        Pattern reg = Pattern.compile("^(10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})|(172\\.((1[6-9])|(2\\d)|(3[01]))\\.\\d{1,3}\\.\\d{1,3})|(192\\.168\\.\\d{1,3}\\.\\d{1,3})$");
        Matcher match = reg.matcher(ip_addr);
        if (match.find()) {
            shell.run_shell_command("ip link add dummy0 type dummy\nip addr add " + ip_addr + "/32 dev dummy0");
        }
    }

    public static void del_dummy_device() {
        shell.run_shell_command("ip link del dummy0");
    }
}
