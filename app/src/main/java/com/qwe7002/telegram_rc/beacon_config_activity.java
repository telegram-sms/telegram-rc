package com.qwe7002.telegram_rc;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.qwe7002.telegram_rc.config.beacon;
import com.qwe7002.telegram_rc.data_structure.BeaconModel;
import com.qwe7002.telegram_rc.data_structure.beaconList;
import com.qwe7002.telegram_rc.static_class.remote_control;

import java.util.ArrayList;

import io.paperdb.Paper;

public class beacon_config_activity extends AppCompatActivity {
    protected static final String TAG = "monitoring_activity";
    private final BroadcastReceiver flushReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Gson gson = new Gson();
            ArrayList<BeaconModel> arrayList =gson.fromJson( intent.getStringExtra("beaconList"),new TypeToken<ArrayList<BeaconModel>>(){}.getType());
            assert arrayList != null;
            flushListView(arrayList);
        }
    };

    void flushListView(ArrayList<BeaconModel> arrayList) {
        ListView beacon_listview = findViewById(R.id.beacon_listview);
        if (!arrayList.isEmpty()) {
            ArrayList<BeaconModel> list = new ArrayList<>();
            for (BeaconModel beacon : arrayList) {
                if (beacon.getDistance() < 200.0) {
                    list.add(beacon);
                }
            }

            runOnUiThread(() -> {
                CustomBeaconAdapter adapter = new CustomBeaconAdapter(list, beacon_config_activity.this);
                beacon_listview.setAdapter(adapter);
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getApplicationContext();
        Paper.init(context);
        setContentView(R.layout.activity_beacon);
        flushListView(new ArrayList<>());
        registerReceiver(flushReceiver,
                new IntentFilter("flush_beacons_list"), Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(flushReceiver);
        super.onDestroy();
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Context context = getApplicationContext();
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.set_beacon_layout, null);
        SwitchMaterial enable = dialogView.findViewById(R.id.beacon_enable_switch);
        SwitchMaterial useVpnHotspotSwitch = dialogView.findViewById(R.id.beacon_use_vpn_hotspot_switch);
        EditText disableCount = dialogView.findViewById(R.id.beacon_disable_count_editview);
        EditText enableCount = dialogView.findViewById(R.id.beacon_enable_count_editview);
        beacon config = Paper.book("beacon").read("config", new beacon());
        assert config != null;
        useVpnHotspotSwitch.setChecked(config.useVpnHotspot);
        if (!remote_control.isVPNHotspotExist(context)) {
            useVpnHotspotSwitch.setChecked(false);
        }
        if (!Settings.System.canWrite(context) && remote_control.isVPNHotspotExist(context)) {
            useVpnHotspotSwitch.setChecked(true);
        }
        if (Settings.System.canWrite(context) && remote_control.isVPNHotspotExist(context)) {
            useVpnHotspotSwitch.setEnabled(true);
        }

        disableCount.setText(String.valueOf(config.disableCount));
        enableCount.setText(String.valueOf(config.enableCount));
        new AlertDialog.Builder(this).setTitle("Beacon configuration")
                .setView(dialogView)
                .setPositiveButton(R.string.ok_button, (dialog, which) -> {
                    config.opposite = enable.isChecked();
                    config.useVpnHotspot = useVpnHotspotSwitch.isChecked();
                    config.disableCount = Integer.parseInt(disableCount.getText().toString());
                    config.enableCount = Integer.parseInt(enableCount.getText().toString());
                    Paper.book("beacon").write("config", config);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("reload_beacon_config"));
                }).show();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.beacon_menu, menu);
        return true;
    }

    static class CustomBeaconAdapter extends BaseAdapter {
        ArrayList<BeaconModel> list;
        Context context;
        ArrayList<String> listen_list;

        public CustomBeaconAdapter(ArrayList<BeaconModel> list, Context context) {
            this.list = list;
            this.context = context;
            listen_list = Paper.book("beacon").read("address", new ArrayList<>());
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public Object getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(context);
            @SuppressLint({"ViewHolder", "InflateParams"}) View view = inflater.inflate(R.layout.item_beacon, null);

            TextView titleView = view.findViewById(R.id.beacon_title_textview);
            TextView addressView = view.findViewById(R.id.beacon_address_textview);
            TextView infoView = view.findViewById(R.id.beacon_info_textview);
            final CheckBox checkBoxView = view.findViewById(R.id.beacon_select_checkbox);
            BeaconModel beacon = list.get(position);
            titleView.setText(beacon.getUuid());
            addressView.setText("Major: " + beacon.getMajor() + " Minor: " + beacon.getMinor() + " Rssi: " + beacon.getRssi() + " dBm");
            infoView.setText("Distance: " + (int) beacon.getDistance() + " meters");
            if (listen_list.contains(beaconList.beaconItemName(beacon.getUuid(), beacon.getMajor(), beacon.getMinor()))) {
                checkBoxView.setChecked(true);
            }
            checkBoxView.setOnClickListener(v -> {
                String address = beaconList.beaconItemName(beacon.getUuid(), beacon.getMajor(), beacon.getMinor());
                ArrayList<String> listenListTemp = Paper.book("beacon").read("address", new ArrayList<>());
                if (checkBoxView.isChecked()) {
                    assert listenListTemp != null;
                    if (!listenListTemp.contains(address)) {
                        listenListTemp.add(address);
                    }
                } else {
                    assert listenListTemp != null;
                    listenListTemp.remove(address);
                }
                Log.d(TAG, "beacon_address: " + listenListTemp);
                Paper.book("beacon").write("address", listenListTemp);
                listen_list = listenListTemp;
            });

            return view;
        }
    }
}


