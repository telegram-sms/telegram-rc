package uk.reall.root_kit;

import android.util.Log;

public class nadb {

    public static boolean set_nadb(String port) {
        int port_number;
        try {
            port_number = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            Log.d("nadb", "set_nadb: [" + port + "]Not a correct port number.");
            return false;
        }
        if (port_number > 65535) {
            Log.i("nadb", "set_nadb: The port number is greater than 65535.");
            return false;
        }
        return root_kit.run_shell_command("setprop service.adb.tcp.port " + port + "\nstop adbd\nstart adbd");
    }




}
