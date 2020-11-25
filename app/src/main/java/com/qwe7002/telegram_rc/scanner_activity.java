package com.qwe7002.telegram_rc;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.zxing.Result;
import com.qwe7002.telegram_rc.static_class.public_value;

import org.jetbrains.annotations.NotNull;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class scanner_activity extends Activity implements ZXingScannerView.ResultHandler {
    private ZXingScannerView scanner_view;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_scanner);
        Toolbar toolbar = findViewById(R.id.scan_toolbar);
        toolbar.setTitle(R.string.scan_title);
        toolbar.setTitleTextColor(Color.WHITE);
        ViewGroup contentFrame = findViewById(R.id.content_frame);
        scanner_view = new ZXingScannerView(this);
        contentFrame.addView(scanner_view);
    }


    @Override
    public void onResume() {
        super.onResume();
        scanner_view.setResultHandler(this);
        scanner_view.startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        scanner_view.stopCamera();
    }

    @Override
    public void handleResult(@NotNull Result rawResult) {
        String TAG = "activity_scanner";
        Log.d(TAG, "format: " + rawResult.getBarcodeFormat().toString() + " content: " + rawResult.getText());
        if (json_validate(rawResult.getText())) {
            Intent intent = new Intent().putExtra("config_json", rawResult.getText());
            setResult(public_value.RESULT_CONFIG_JSON, intent);
        }
        scanner_view.stopCamera();
        finish();
    }

    boolean json_validate(String jsonStr) {
        JsonElement jsonElement;
        try {
            jsonElement = JsonParser.parseString(jsonStr);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        if (jsonElement == null) {
            return false;
        }
        return jsonElement.isJsonObject();
    }
}
