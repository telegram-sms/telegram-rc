package uk.reall.root_kit;

@SuppressWarnings("ALL")
public class network {
    public static boolean set_airplane_mode(boolean enable) {
        int set = 0;
        String state = "true";
        if (enable) {
            set = 1;
            state = "false";
        }
        return shell.run_shell_command("settings put global airplane_mode_on " + set + " \nam broadcast -a android.intent.action.AIRPLANE_MODE --ez state " + state);
    }

    public static boolean wifi_set_enable(boolean enable) {
        String state = "disable";
        if (!enable) {
            state = "enable";
        }
        return shell.run_shell_command("svc wifi " + state);
    }

    public static boolean data_set_enable(boolean enable) {
        String state = "disable";
        if (!enable) {
            state = "enable";
        }
        return shell.run_shell_command("svc data " + enable);
    }
}
