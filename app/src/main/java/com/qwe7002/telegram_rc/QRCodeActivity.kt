package com.qwe7002.telegram_rc

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer
import com.google.gson.Gson
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.qwe7002.telegram_rc.data_structure.ScannerJson
import com.tencent.mmkv.MMKV

class QRCodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)
        val preferences = MMKV.defaultMMKV()

        val config = ScannerJson(
            botToken = preferences.getString("bot_token", "").toString(),
            chatId = preferences.getString("chat_id", "").toString(),
            trustedPhoneNumber = preferences.getString("trusted_phone_number", "").toString(),
            fallbackSms = preferences.getBoolean("fallback_sms", false),
            chatCommand = preferences.getBoolean("chat_command", false),
            batteryMonitoringSwitch = preferences.getBoolean("battery_monitoring_switch", false),
            chargerStatus = preferences.getBoolean("charger_status", false),
            verificationCode = preferences.getBoolean("verification_code", false),
            topicID = preferences.getString("message_thread_id","")!!
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
