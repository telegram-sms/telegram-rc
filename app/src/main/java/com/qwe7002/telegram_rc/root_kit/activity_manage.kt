package com.qwe7002.telegram_rc.root_kit;

public class activity_manage {
    @SuppressWarnings({"unused", "RedundantSuppression"})
    public static void startService(String package_name, String service_name) {
        shell.runShellCommand("am startservice -n " + package_name + "/" + service_name);
    }

    @SuppressWarnings({"unused", "RedundantSuppression"})
    public static void startForegroundService(String package_name, String service_name) {
        shell.runShellCommand("am start-foreground-service -n " + package_name + "/" + service_name);
    }

    @SuppressWarnings({"unused", "RedundantSuppression"})
    public static boolean startActivity(String package_name, String activity_name) {
        return shell.runShellCommand("am start -n " + package_name + "/" + activity_name);
    }

    @SuppressWarnings({"unused", "RedundantSuppression"})
    public static void forceStopService(String package_name) {
        shell.runShellCommand("am force-stop -n " + package_name);
    }

    @SuppressWarnings({"unused", "RedundantSuppression"})
    public static boolean checkServiceIsRunning(String package_name, String service_name) {
        return shell.runShellCommand("dumpsys activity services | grep " + package_name + "/" + service_name);
    }
}

