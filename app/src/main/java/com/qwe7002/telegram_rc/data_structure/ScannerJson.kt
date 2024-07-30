package com.qwe7002.telegram_rc.data_structure

import com.google.gson.annotations.SerializedName


class ScannerJson {
    @SerializedName("bot_token")
    val botToken: String = ""

    @SerializedName("chat_id")
    val chatId: String = ""

    @SerializedName("trusted_phone_number")
    val trustedPhoneNumber: String = ""

    @SerializedName("battery_monitoring_switch")
    val batteryMonitoringSwitch = false

    @SerializedName("charger_status")
    val chargerStatus = false

    @SerializedName("chat_command")
    val chatCommand = false

    @SerializedName("fallback_sms")
    val fallbackSms = false

    @SerializedName("privacy_mode")
    val privacyMode = false

    @SerializedName("verification_code")
    val verificationCode = false

}
