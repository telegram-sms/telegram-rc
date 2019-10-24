package uk.reall.root_kit;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class activity_manage {
    public static void start_service(String package_name, String service_name) {
        root_kit.run_shell_command("am startservice -n " + package_name + "/" + service_name);
    }

    public static void start_foreground_service(String package_name, String service_name) {
        root_kit.run_shell_command("am start-foreground-service -n " + package_name + "/" + service_name);
    }

    public static void start_activity(String package_name, String activity_name) {
        root_kit.run_shell_command("am start -n " + package_name + "/" + activity_name);

    }
}
