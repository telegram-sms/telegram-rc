package com.qwe7002.telegram_rc.root_kit


object Radio {
    @JvmStatic
    val isNRConnected: Boolean
        get() = Shell.runShellCommand("dumpsys telephony.registry |grep mServiceState|grep nrState=CONNECTED")

    @JvmStatic
    val isNRStandby: Boolean
        get() = Shell.runShellCommand("dumpsys telephony.registry |grep mServiceState|grep -E \"nrState=NOT_RESTRICTED|nrState=RESTRICTED\"")

    @JvmStatic
    val isLTECA: Boolean
        get() = Shell.runShellCommand("dumpsys telephony.registry |grep mServiceState|grep \"mIsUsingCarrierAggregation = true\"")
}
