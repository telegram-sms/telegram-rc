package com.qwe7002.telegram_rc.root_kit

object Networks {
    @JvmStatic
    fun setWifi(enable: Boolean) {
        var state = "disable"
        if (enable) {
            state = "enable"
        }
        Shell.runShellCommand("svc wifi $state")
    }

    @JvmStatic
    fun setData(enable: Boolean) {
        var state = "disable"
        if (enable) {
            state = "enable"
        }
        Shell.runShellCommand("svc data $state")
    }


    @JvmStatic
    fun addDummyDevice(ipAddr: String) {
        Shell.runShellCommand("ip link add dummy0 type dummy\nip addr add $ipAddr/32 dev dummy0")
    }

    @JvmStatic
    fun delDummyDevice() {
        Shell.runShellCommand("ip link del dummy0")
    }
}
