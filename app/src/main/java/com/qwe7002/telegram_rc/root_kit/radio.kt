package com.qwe7002.telegram_rc.root_kit


object radio {
    @JvmStatic
    val isNRConnected: Boolean
        get() = shell.runShellCommand("dumpsys telephony.registry |grep mServiceState|grep nrState=CONNECTED")

    @JvmStatic
    val isNRStandby: Boolean
        get() = shell.runShellCommand("dumpsys telephony.registry |grep mServiceState|grep -E \"nrState=NOT_RESTRICTED|nrState=RESTRICTED\"")

    @JvmStatic
    val isLTECA: Boolean
        get() = shell.runShellCommand("dumpsys telephony.registry |grep mServiceState|grep \"mIsUsingCarrierAggregation = true\"")
}
