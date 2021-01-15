package com.qwe7002.telegram_rc.root_kit;

import androidx.annotation.RequiresApi;

public class activity_manage {
    @SuppressWarnings({"unused", "RedundantSuppression"})
    public static void start_service(String package_name, String service_name) {
        shell.run_shell_command("am startservice -n " + package_name + "/" + service_name);
    }

    @SuppressWarnings({"unused", "RedundantSuppression"})
    @RequiresApi(26)
    public static void start_foreground_service(String package_name, String service_name) {
        shell.run_shell_command("am start-foreground-service -n " + package_name + "/" + service_name);
    }

    @SuppressWarnings({"unused", "RedundantSuppression"})
    public static boolean start_activity(String package_name, String activity_name) {
        return shell.run_shell_command("am start -n " + package_name + "/" + activity_name);
    }

    @SuppressWarnings({"unused", "RedundantSuppression"})
    public static void force_stop_package(String package_name) {
        shell.run_shell_command("am force-stop -n " + package_name);
    }

    @SuppressWarnings({"unused", "RedundantSuppression"})
    public static boolean check_service_is_running(String package_name, String service_name) {
        return shell.run_shell_command("dumpsys activity services | grep " + package_name + "/" + service_name);
    }
}

