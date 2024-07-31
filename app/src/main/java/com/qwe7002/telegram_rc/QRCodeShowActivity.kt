package com.qwe7002.telegram_rc

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.qwe7002.telegram_rc.data_structure.ScannerJson

class QRCodeShowActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)
        val context = applicationContext
        val sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE)
        val config = ScannerJson(
            botToken = sharedPreferences.getString("bot_token", "").toString(),
            chatId = sharedPreferences.getString("chat_id", "").toString(),
            trustedPhoneNumber = sharedPreferences.getString("trusted_phone_number", "").toString(),
            fallbackSms = sharedPreferences.getBoolean("fallback_sms", false),
            chatCommand = sharedPreferences.getBoolean("chat_command", false),
            batteryMonitoringSwitch = sharedPreferences.getBoolean("battery_monitoring_switch", false),
            chargerStatus = sharedPreferences.getBoolean("charger_status", false),
            verificationCode = sharedPreferences.getBoolean("verification_code", false),
            privacyMode = sharedPreferences.getBoolean("privacy_mode", false)
        )
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



}
