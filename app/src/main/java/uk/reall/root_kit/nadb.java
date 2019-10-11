package uk.reall.root_kit;

public class nadb {

    public static boolean set_nadb(String port) {
        return root_kit.run_shell_command("setprop service.adb.tcp.port " + port + "\nstop adbd\nstart adbd");
    }




}
