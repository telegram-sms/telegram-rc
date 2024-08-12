package com.qwe7002.telegram_rc

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer
import com.google.gson.Gson
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.qwe7002.telegram_rc.data_structure.ScannerJson
import io.paperdb.Paper

class QRCodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)
        Paper.init(applicationContext)
        val preferences = Paper.book("preferences")

        val config = ScannerJson(
            botToken = preferences.read("bot_token", "").toString(),
            chatId = preferences.read("chat_id", "").toString(),
            trustedPhoneNumber = preferences.read("trusted_phone_number", "").toString(),
            fallbackSms = preferences.read("fallback_sms", false)!!,
            chatCommand = preferences.read("chat_command", false)!!,
            batteryMonitoringSwitch = preferences.read("battery_monitoring_switch", false)!!,
            chargerStatus = preferences.read("charger_status", false)!!,
            verificationCode = preferences.read("verification_code", false)!!,
            privacyMode = preferences.read("privacy_mode", false)!!,
            topicID = preferences.read("message_thread_id","")!!
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
