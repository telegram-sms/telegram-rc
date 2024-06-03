package com.qwe7002.telegram_rc

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer
import com.google.gson.Gson
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

class QRCodeShowActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)
        val context = applicationContext
        val sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE)
        val config = config_list()
        config.bot_token = sharedPreferences.getString("bot_token", "")
        config.chat_id = sharedPreferences.getString("chat_id", "")
        config.trusted_phone_number = sharedPreferences.getString("trusted_phone_number", "")
        config.fallback_sms = sharedPreferences.getBoolean("fallback_sms", false)
        config.chat_command = sharedPreferences.getBoolean("chat_command", false)
        config.battery_monitoring_switch =
            sharedPreferences.getBoolean("battery_monitoring_switch", false)
        config.charger_status = sharedPreferences.getBoolean("charger_status", false)
        config.verification_code = sharedPreferences.getBoolean("verification_code", false)
        config.privacy_mode = sharedPreferences.getBoolean("privacy_mode", false)
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
        var bot_token: String? = ""
        var chat_id: String? = ""
        var trusted_phone_number: String? = ""
        var fallback_sms: Boolean = false
        var chat_command: Boolean = false
        var battery_monitoring_switch: Boolean = false
        var charger_status: Boolean = false
        var verification_code: Boolean = false
        var privacy_mode: Boolean = false
    }
}
