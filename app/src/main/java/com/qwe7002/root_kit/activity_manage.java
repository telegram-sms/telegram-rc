package com.qwe7002.root_kit;

import android.annotation.TargetApi;
import android.os.Build;

@SuppressWarnings("ALL")
public class activity_manage {
    public static boolean start_service(String package_name, String service_name) {
        return shell.run_shell_command("am startservice -n " + package_name + "/" + service_name);
    }

    @TargetApi(Build.VERSION_CODES.O)
    public static boolean start_foreground_service(String package_name, String service_name) {
        return shell.run_shell_command("am start-foreground-service -n " + package_name + "/" + service_name);
    }

    public static boolean start_activity(String package_name, String activity_name) {
        return shell.run_shell_command("am start -n " + package_name + "/" + activity_name);
    }

    public static boolean check_service_is_running(String package_name, String service_name) {
        return shell.run_shell_command("dumpsys activity services | grep " + package_name + "/" + service_name);
    }
}
