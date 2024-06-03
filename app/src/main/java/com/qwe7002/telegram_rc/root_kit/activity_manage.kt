package com.qwe7002.telegram_rc.root_kit

object activity_manage {
    @Suppress("unused")
    fun startService(package_name: String, service_name: String) {
        shell.runShellCommand("am startservice -n $package_name/$service_name")
    }

    @JvmStatic
    @Suppress("unused")
    fun startForegroundService(package_name: String, service_name: String) {
        shell.runShellCommand("am start-foreground-service -n $package_name/$service_name")
    }

    @Suppress("unused")
    fun startActivity(package_name: String, activity_name: String): Boolean {
        return shell.runShellCommand("am start -n $package_name/$activity_name")
    }

    @JvmStatic
    @Suppress("unused")
    fun forceStopService(package_name: String) {
        shell.runShellCommand("am force-stop -n $package_name")
    }

    @JvmStatic
    @Suppress("unused")
    fun checkServiceIsRunning(package_name: String, service_name: String): Boolean {
        return shell.runShellCommand("dumpsys activity services | grep $package_name/$service_name")
    }
}

