package uk.reall.root_kit;

import android.util.Log;

import java.io.DataOutputStream;
import java.util.Objects;

public class root_kit {
    static boolean run_shell_command(String command) {
        final String TAG = "root_kit";
        Process process;
        DataOutputStream os;
        try {
            Log.d(TAG, "run_shell_command: su \n" + command + "\nexit\n");
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\nexit\n");
            os.flush();
            int exitValue = process.waitFor();
            os.close();
            process.destroy();
            Log.d(TAG, "command exit code:" + exitValue);
            return exitValue == 0;
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "command run failed:" + Objects.requireNonNull(e.getMessage()));
            return false;
        }
    }

    public static boolean check_root() {
        return root_kit.run_shell_command("");
    }
}
