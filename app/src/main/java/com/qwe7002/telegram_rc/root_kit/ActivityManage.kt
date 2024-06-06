package com.qwe7002.telegram_rc.root_kit

object ActivityManage {
    @Suppress("unused")
    fun startService(packageName: String, serviceName: String) {
        Shell.runShellCommand("am startservice -n $packageName/$serviceName")
    }

    @JvmStatic
    @Suppress("unused")
    fun startForegroundService(packageName: String, serviceName: String) {
        Shell.runShellCommand("am start-foreground-service -n $packageName/$serviceName")
    }

    @Suppress("unused")
    fun startActivity(packageName: String, activityName: String): Boolean {
        return Shell.runShellCommand("am start -n $packageName/$activityName")
    }

    @JvmStatic
    @Suppress("unused")
    fun forceStopService(packageName: String) {
        Shell.runShellCommand("am force-stop -n $packageName")
    }

    @JvmStatic
    @Suppress("unused")
    fun checkServiceIsRunning(packageName: String, serviceName: String): Boolean {
        return Shell.runShellCommand("dumpsys activity services | grep $packageName/$serviceName")
    }
}

