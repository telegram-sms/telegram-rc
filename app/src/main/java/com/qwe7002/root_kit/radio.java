package com.qwe7002.root_kit;


public class radio {
    public static boolean is_NR_connected() {
        return shell.run_shell_command("dumpsys telephony.registry |grep mServiceState|grep nrState=CONNECTED");
    }

    public static boolean is_NR_standby() {
        return shell.run_shell_command("dumpsys telephony.registry |grep mServiceState|grep -E \"nrState=NOT_RESTRICTED|nrState=RESTRICTED\"");
    }

    public static boolean is_LTE_CA() {
        return shell.run_shell_command("dumpsys telephony.registry |grep mServiceState|grep \"mIsUsingCarrierAggregation = true\"");
    }
}
