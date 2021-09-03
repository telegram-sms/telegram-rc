package com.qwe7002.telegram_rc.root_kit;

public class startup {
    public static void start_termux_script(String file_name) {
        String script;
        script = "am start-foreground-service";
        script += " -n com.termux/.app.TermuxService -a com.termux.service_execute " +
                "-d com.termux.file:/data/data/com.termux/files/home/.termux/boot/" + file_name +
                " -e com.termux.execute.background true && am start --user 0 -n com.termux/com.termux.app.TermuxActivity";
        shell.run_shell_command(script);
    }

}
