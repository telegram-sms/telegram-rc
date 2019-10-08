package uk.reall.NADB;

import android.util.Log;

import java.io.DataOutputStream;
import java.util.Objects;

public class NADB {
    public NADB() {

    }

    private boolean run_shell_command(String command) {
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
            Log.d("NADB", String.valueOf(exitValue));
            return exitValue == 0;
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("NADB", Objects.requireNonNull(e.getMessage()));
            return false;
        }
    }

    public boolean set_NADB(String port) {
        return run_shell_command("setprop service.adb.tcp.port " + port + "\nstop adbd\nstart adbd");
    }

    public boolean check_root() {

        return run_shell_command("");
    }


}
