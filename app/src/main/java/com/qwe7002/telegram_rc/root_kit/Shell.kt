package com.qwe7002.telegram_rc.root_kit

import com.topjohnwu.superuser.Shell

object Shell {
    fun runShellCommand(command: String): Boolean {
        val result = Shell.cmd("su $command").exec()
        return result.isSuccess
    }

    @JvmStatic
    fun checkRoot(): Boolean {
        return Shell.getShell().isRoot
    }
}
