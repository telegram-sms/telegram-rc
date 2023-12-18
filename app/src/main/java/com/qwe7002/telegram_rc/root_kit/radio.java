package com.qwe7002.telegram_rc.root_kit;


public class radio {
    public static boolean isNRConnected() {
        return shell.runShellCommand("dumpsys telephony.registry |grep mServiceState|grep nrState=CONNECTED");
    }

    public static boolean isNRStandby() {
        return shell.runShellCommand("dumpsys telephony.registry |grep mServiceState|grep -E \"nrState=NOT_RESTRICTED|nrState=RESTRICTED\"");
    }

    public static boolean isLTECA() {
        return shell.runShellCommand("dumpsys telephony.registry |grep mServiceState|grep \"mIsUsingCarrierAggregation = true\"");
    }
}
