package uk.reall.root_kit;

public class activity_manage {
    @SuppressWarnings("SpellCheckingInspection")
    public static void start_service(String package_name, String service_name) {
        root_kit.run_shell_command("am startservice -n " + package_name + "/" + service_name);
    }
}
