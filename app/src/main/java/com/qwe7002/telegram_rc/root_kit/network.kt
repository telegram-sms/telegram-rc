package com.qwe7002.telegram_rc.root_kit

object network {
    @JvmStatic
    fun setWifi(enable: Boolean) {
        var state = "disable"
        if (enable) {
            state = "enable"
        }
        shell.runShellCommand("svc wifi $state")
    }

    @JvmStatic
    fun setData(enable: Boolean) {
        var state = "disable"
        if (enable) {
            state = "enable"
        }
        shell.runShellCommand("svc data $state")
    }


    @JvmStatic
    fun addDummyDevice(ip_addr: String) {
        shell.runShellCommand("ip link add dummy0 type dummy\nip addr add $ip_addr/32 dev dummy0")
    }

    @JvmStatic
    fun delDummyDevice() {
        shell.runShellCommand("ip link del dummy0")
    }
}
