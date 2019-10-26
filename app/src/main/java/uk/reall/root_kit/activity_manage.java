package uk.reall.root_kit;

@SuppressWarnings("ALL")
public class activity_manage {
    public static boolean start_service(String package_name, String service_name) {
        return shell.run_shell_command("am startservice -n " + package_name + "/" + service_name);
    }

    public static boolean start_foreground_service(String package_name, String service_name) {
        return shell.run_shell_command("am start-foreground-service -n " + package_name + "/" + service_name);
    }

    public static boolean start_activity(String package_name, String activity_name) {
        return shell.run_shell_command("am start -n " + package_name + "/" + activity_name);
    }
}
