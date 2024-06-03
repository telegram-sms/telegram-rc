package com.qwe7002.telegram_rc.root_kit;

import com.topjohnwu.superuser.Shell;

public class shell {
    public static boolean runShellCommand(String command) {
        Shell.Result result = Shell.cmd("su "+command).exec();
        return result.isSuccess();
    }

    public static boolean checkRoot() {
        return Shell.getShell().isRoot();
    }

}
