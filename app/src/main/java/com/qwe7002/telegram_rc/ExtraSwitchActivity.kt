package com.qwe7002.telegram_rc

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.qwe7002.telegram_rc.MMKV.Const
import com.tencent.mmkv.MMKV
import rikka.shizuku.Shizuku

class ExtraSwitchActivity : AppCompatActivity() {
    private lateinit var beaconMMKV: MMKV
    private lateinit var autoSwitch: SwitchMaterial
    private lateinit var shizukuStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extra_setting)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.beacon_auto_switch_layout)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.beacon_auto_switch_setting)

        beaconMMKV = MMKV.mmkvWithID(Const.BEACON_MMKV_ID)
        autoSwitch = findViewById(R.id.beacon_auto_switch)
        shizukuStatusText = findViewById(R.id.shizuku_status_text)

        // Check Shizuku status
        updateShizukuStatus()

        // Load current setting
        autoSwitch.isChecked = beaconMMKV.getBoolean("beacon_enable", false)

        // Save setting when changed
        autoSwitch.setOnCheckedChangeListener { _, isChecked ->
            beaconMMKV.putBoolean("beacon_enable", isChecked)
            // Also check Shizuku status when changing setting
            updateShizukuStatus()
        }
    }

    private fun updateShizukuStatus() {
        val isShizukuAvailable = Shizuku.pingBinder()
        val hasShizukuPermission = isShizukuAvailable && 
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        when {
            hasShizukuPermission -> {
                shizukuStatusText.text = getString(R.string.shizuku_available)
                shizukuStatusText.setTextColor(getColor(R.color.green))
            }
            isShizukuAvailable -> {
                shizukuStatusText.text = getString(R.string.shizuku_no_permission)
                shizukuStatusText.setTextColor(getColor(R.color.orange))
            }
            else -> {
                shizukuStatusText.text = getString(R.string.shizuku_not_running)
                shizukuStatusText.setTextColor(getColor(R.color.red))
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}