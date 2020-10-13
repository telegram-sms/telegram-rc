package com.qwe7002.root_kit;

public class radio {
    public static boolean is_NR_Connected() {
        return shell.run_shell_command("dumpsys telephony.registry |grep mServiceState|grep nrState=CONNECTED");
    }

    public static boolean is_LTE_CA() {
        return shell.run_shell_command("dumpsys telephony.registry |grep mServiceState|grep \"mIsUsingCarrierAggregation = true\"");
    }
}
