package com.qwe7002.telegram_rc

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.sumimakito.awesomeqrcode.AwesomeQrRenderer
import com.google.gson.Gson
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.qwe7002.telegram_rc.data_structure.ScannerJson
import com.tencent.mmkv.MMKV

class QRCodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)
        
        // Handle window insets for edge-to-edge
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        FakeStatusBar().fakeStatusBar(this, window)
        
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
