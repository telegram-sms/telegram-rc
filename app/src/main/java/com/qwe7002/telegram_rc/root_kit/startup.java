package com.qwe7002.telegram_rc.root_kit;

import android.os.Build;

public class startup {
    public static boolean start_termux_script(String file_name) {
        String script = "am startservice";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            script = "am start-foreground-service";
        }
        script += " -n com.termux/.app.TermuxService -a com.termux.service_execute " +
                "-d com.termux.file:/data/data/com.termux/files/home/.termux/boot/" + file_name +
                " -e com.termux.execute.background true";
        return shell.run_shell_command(script);
    }

}
