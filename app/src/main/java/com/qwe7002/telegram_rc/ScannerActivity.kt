package com.qwe7002.telegram_rc

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScannerView
import com.budiyev.android.codescanner.DecodeCallback
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import com.qwe7002.telegram_rc.MMKV.Const


class ScannerActivity : Activity() {
    private lateinit var mCodeScanner: CodeScanner

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_scanner)
        // Handle window insets for edge-to-edge on ScrollView
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scanner_container)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val scannerView = findViewById<CodeScannerView>(R.id.scanner_view)
        mCodeScanner = CodeScanner(this, scannerView)
        object : ArrayList<BarcodeFormat?>() {
            init {
                add(BarcodeFormat.QR_CODE)
            }
        }.also { this.mCodeScanner.formats = it }
        DecodeCallback { result: Result ->
            runOnUiThread {
                val logTag = this::class.java.simpleName
                Log.d(logTag, "format: " + result.barcodeFormat + " content: " + result.text)
                if (!jsonValidate(result.text)) {
                    Toast.makeText(this, "The QR code is not legal", Toast.LENGTH_SHORT).show()
                    mCodeScanner.startPreview()
                    return@runOnUiThread
                }
                val intent = Intent().putExtra("config_json", result.text)
                setResult(Const.RESULT_CONFIG_JSON, intent)
                finish()
            }
        }.also { mCodeScanner.decodeCallback = it }
        scannerView.setOnClickListener { mCodeScanner.startPreview() }
    }


    public override fun onResume() {
        super.onResume()
        mCodeScanner.startPreview()
    }

    public override fun onPause() {
        mCodeScanner.releaseResources()
        super.onPause()
    }

    private fun jsonValidate(jsonStr: String): Boolean {
        val jsonElement: JsonElement?
        try {
            jsonElement = JsonParser.parseString(jsonStr)
        } catch (e: Exception) {
            Log.d("jsonValidate", "jsonValidate: $e")
            return false
        }
        return jsonElement != null
    }
}
