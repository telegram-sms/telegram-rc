package com.qwe7002.root_kit;


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

    public static void add_dummy_device() {
        shell.run_shell_command("ip link add dummy0 type dummy\nip addr add 10.250.0.1/32 dev dummy0");
    }
}
