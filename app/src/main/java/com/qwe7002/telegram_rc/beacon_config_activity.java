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
            flushListView();
        }
    };

    void flushListView() {
        ListView beacon_listview = findViewById(R.id.beacon_listview);
        if (beaconList.beacons.size() > 0) {
            final ArrayList<BeaconModel> list = new ArrayList<>(beaconList.beacons);
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
        flushListView();
        LocalBroadcastManager.getInstance(this).registerReceiver(flushReceiver,
                new IntentFilter("flush_beacons_list"));
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
        View dialog_view = inflater.inflate(R.layout.set_beacon_layout, null);
        SwitchMaterial enable = dialog_view.findViewById(R.id.beacon_enable_switch);
        SwitchMaterial use_vpn_switch = dialog_view.findViewById(R.id.beacon_use_vpn_hotspot_switch);
        EditText disable_count = dialog_view.findViewById(R.id.beacon_disable_count_editview);
        EditText enable_count = dialog_view.findViewById(R.id.beacon_enable_count_editview);
        beacon config = Paper.book("beacon_config").read("config", new beacon());
        assert config != null;
        use_vpn_switch.setChecked(config.useVpnHotspot);
        if (!remote_control.isVPNHotspotExist(context)) {
            use_vpn_switch.setChecked(false);
        }
        if (!Settings.System.canWrite(context) && remote_control.isVPNHotspotExist(context)) {
            use_vpn_switch.setChecked(true);
        }
        if (Settings.System.canWrite(context) && remote_control.isVPNHotspotExist(context)) {
            use_vpn_switch.setEnabled(true);
        }

        disable_count.setText(String.valueOf(config.disableCount));
        enable_count.setText(String.valueOf(config.enableCount));
        new AlertDialog.Builder(this).setTitle("Beacon configuration")
                .setView(dialog_view)
                .setPositiveButton(R.string.ok_button, (dialog, which) -> {
                    config.opposite = enable.isChecked();
                    config.useVpnHotspot = use_vpn_switch.isChecked();
                    config.disableCount = Integer.parseInt(disable_count.getText().toString());
                    config.enableCount = Integer.parseInt(enable_count.getText().toString());
                    Paper.book("beacon_config").write("config", config);
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
            listen_list = Paper.book("beacon_config").read("address", new ArrayList<>());
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

            TextView title_view = view.findViewById(R.id.beacon_title_textview);
            TextView address_view = view.findViewById(R.id.beacon_address_textview);
            TextView info_view = view.findViewById(R.id.beacon_info_textview);
            final CheckBox check_box_view = view.findViewById(R.id.beacon_select_checkbox);
            BeaconModel beacon = list.get(position);
            title_view.setText(beacon.getUuid());
            address_view.setText("Major: " + beacon.getMajor() + " Minor: " + beacon.getMinor() + " Rssi: " + beacon.getRssi() + " dBm");
            info_view.setText("Distance: " + (int) beacon.getDistance() + " meters");
            if (listen_list.contains(beaconList.beaconItemName(beacon.getUuid(), beacon.getMajor(), beacon.getMinor()))) {
                check_box_view.setChecked(true);
            }
            check_box_view.setOnClickListener(v -> {
                String address = beaconList.beaconItemName(beacon.getUuid(), beacon.getMajor(), beacon.getMinor());
                ArrayList<String> listenListTemp = Paper.book("beacon_config").read("address", new ArrayList<>());
                if (check_box_view.isChecked()) {
                    assert listenListTemp != null;
                    if (!listenListTemp.contains(address)) {
                        listenListTemp.add(address);
                    }
                } else {
                    assert listenListTemp != null;
                    listenListTemp.remove(address);
                }
                Log.d(TAG, "beacon_address: " + listenListTemp);
                Paper.book("beacon_config").write("address", listenListTemp);
                listen_list = listenListTemp;
            });

            return view;
        }
    }
}


