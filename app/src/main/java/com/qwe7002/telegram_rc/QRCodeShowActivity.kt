package com.qwe7002.telegram_rc

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

class QRCodeShowActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)
        val context = applicationContext
        val sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE)
        val config = config_list()
        config.botToken = sharedPreferences.getString("bot_token", "").toString()
        config.chatId = sharedPreferences.getString("chat_id", "").toString()
        config.trustedPhoneNumber = sharedPreferences.getString("trusted_phone_number", "").toString()
        config.fallbackSms = sharedPreferences.getBoolean("fallback_sms", false)
        config.chatCommand = sharedPreferences.getBoolean("chat_command", false)
        config.batteryMonitoringSwitch =
            sharedPreferences.getBoolean("battery_monitoring_switch", false)
        config.chargerStatus = sharedPreferences.getBoolean("charger_status", false)
        config.verificationCode = sharedPreferences.getBoolean("verification_code", false)
        config.privacyMode = sharedPreferences.getBoolean("privacy_mode", false)
        val imageview = findViewById<ImageView>(R.id.qr_imageview)
        imageview.setImageBitmap(
            AwesomeQrRenderer().genQRcodeBitmap(
                Gson().toJson(config),
                ErrorCorrectionLevel.H,
                1024,
                1024
            )
        )
    }


    private class config_list {
        @SerializedName(value = "bot_token")
        lateinit var botToken: String
        @SerializedName(value = "chat_id")
        lateinit var chatId: String
        @SerializedName(value = "trusted_phone_number")
        lateinit var trustedPhoneNumber: String
        @SerializedName(value = "fallback_sms")
        var fallbackSms: Boolean = false
        @SerializedName(value = "chat_command")
        var chatCommand: Boolean = false
        @SerializedName(value = "battery_monitoring_switch")
        var batteryMonitoringSwitch: Boolean = false
        @SerializedName(value = "charger_status")
        var chargerStatus: Boolean = false
        @SerializedName(value = "verification_code")
        var verificationCode: Boolean = false
        @SerializedName(value = "privacy_mode")
        var privacyMode: Boolean = false
    }
}
