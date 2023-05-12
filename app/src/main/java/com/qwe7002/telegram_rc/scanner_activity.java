package com.qwe7002.telegram_rc;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.budiyev.android.codescanner.CodeScanner;
import com.budiyev.android.codescanner.CodeScannerView;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.zxing.BarcodeFormat;
import com.qwe7002.telegram_rc.static_class.CONST;

import java.util.ArrayList;


public class scanner_activity extends Activity {

    private CodeScanner mCodeScanner;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_scanner);
        CodeScannerView scannerView = findViewById(R.id.scanner_view);
        mCodeScanner = new CodeScanner(this, scannerView);
        mCodeScanner.setFormats(new ArrayList<BarcodeFormat>() {{
            add(BarcodeFormat.QR_CODE);
        }});
        mCodeScanner.setDecodeCallback(result -> runOnUiThread(() -> {
            String TAG = "activity_scanner";
            Log.d(TAG, "format: " + result.getBarcodeFormat() + " content: " + result.getText());
            if (!json_validate(result.getText())) {
                Toast.makeText(this, "The QR code is not legal", Toast.LENGTH_SHORT).show();
                mCodeScanner.startPreview();
                return;
            }
            Intent intent = new Intent().putExtra("config_json", result.getText());
            setResult(CONST.RESULT_CONFIG_JSON, intent);
            finish();
        }));
        scannerView.setOnClickListener(view -> mCodeScanner.startPreview());
    }


    @Override
    public void onResume() {
        super.onResume();
        mCodeScanner.startPreview();
    }

    @Override
    public void onPause() {
        mCodeScanner.releaseResources();
        super.onPause();
    }

    boolean json_validate(String jsonStr) {
        JsonElement jsonElement;
        try {
            jsonElement = JsonParser.parseString(jsonStr);
        } catch (Exception e) {
            return false;
        }
        return jsonElement != null;
    }

}
