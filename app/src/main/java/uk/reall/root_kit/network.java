package uk.reall.root_kit;

@SuppressWarnings({"UnusedReturnValue"})
public class network {
    public static void restart_network() {
        root_kit.run_shell_command("settings put global airplane_mode_on 1 \nam broadcast -a android.intent.action.AIRPLANE_MODE --ez state true\n");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        root_kit.run_shell_command("settings put global airplane_mode_on 0\nam broadcast -a android.intent.action.AIRPLANE_MODE --ez state false\n");
    }

    public static boolean wifi_enabled() {
        return root_kit.run_shell_command("svc wifi enable\n");
    }

    public static boolean wifi_disable() {
        return root_kit.run_shell_command("svc wifi disable\n");
    }

    public static boolean data_enabled() {
        return root_kit.run_shell_command("svc data enable\n");
    }

    public static boolean data_disable() {
        return root_kit.run_shell_command("svc data disable\n");
    }
}
