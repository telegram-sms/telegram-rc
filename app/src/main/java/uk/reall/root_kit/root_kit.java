package uk.reall.root_kit;

import android.util.Log;

import java.io.DataOutputStream;
import java.util.Objects;

public class root_kit {
    static boolean run_shell_command(String command) {
        Process process;
        DataOutputStream os;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\nexit\n");
            os.flush();
            int exitValue = process.waitFor();
            os.close();
            process.destroy();
            Log.d("nadb", String.valueOf(exitValue));
            return exitValue == 0;
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("nadb", Objects.requireNonNull(e.getMessage()));
            return false;
        }
    }

    public static boolean check_root() {
        return root_kit.run_shell_command("");
    }
}
