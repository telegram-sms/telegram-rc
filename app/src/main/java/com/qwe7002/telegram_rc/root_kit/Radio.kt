package com.qwe7002.telegram_rc.root_kit


object Radio {
    val isNRConnected: Boolean
        get() = Shell.runShellCommand("dumpsys telephony.registry |grep mServiceState|grep nrState=CONNECTED")

    val isNRStandby: Boolean
        get() = Shell.runShellCommand("dumpsys telephony.registry |grep mServiceState|grep -E \"nrState=NOT_RESTRICTED|nrState=RESTRICTED\"")

    val isLTECA: Boolean
        get() = Shell.runShellCommand("dumpsys telephony.registry |grep mServiceState|grep \"mIsUsingCarrierAggregation = true\"")
}
