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
import com.qwe7002.telegram_rc.data_structure.beacon_list;
import com.qwe7002.telegram_rc.static_class.remote_control_func;

import org.altbeacon.beacon.Beacon;

import java.util.ArrayList;

import io.paperdb.Paper;

public class beacon_config_activity extends AppCompatActivity {
    protected static final String TAG = "monitoring_activity";
    private final BroadcastReceiver flush_receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            flush_list_view();
        }
    };

    void flush_list_view() {
        ListView beacon_listview = findViewById(R.id.beacon_listview);
        if (beacon_list.beacons.size() > 0) {
            final ArrayList<BeaconModel> list = new ArrayList<>();
            for (Beacon beacon : beacon_list.beacons) {
                BeaconModel model = new BeaconModel();
                model.title = beacon.getBluetoothName();
                model.address = beacon.getBluetoothAddress();
                model.info = "Rssi: " + beacon.getRssi() + " dBm Power: " + beacon.getTxPower() + " dBm";
                list.add(model);
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
        flush_list_view();
        LocalBroadcastManager.getInstance(this).registerReceiver(flush_receiver,
                new IntentFilter("flush_view"));
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(flush_receiver);
        super.onDestroy();
    }

    static class BeaconModel {
        String title;
        String address;
        String info;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Context context = getApplicationContext();
        LayoutInflater inflater = this.getLayoutInflater();
        View dialog_view = inflater.inflate(R.layout.set_beacon_layout, null);
        SwitchMaterial enable = dialog_view.findViewById(R.id.beacon_enable_switch);
        SwitchMaterial use_vpn_switch = dialog_view.findViewById(R.id.beacon_use_vpn_hotspot_switch);
        EditText delay = dialog_view.findViewById(R.id.beacon_delay_editview);
        EditText disable_count = dialog_view.findViewById(R.id.beacon_disable_count_editview);
        EditText enable_count = dialog_view.findViewById(R.id.beacon_enable_count_editview);
        beacon config = Paper.book("beacon_config").read("config", new beacon());
        use_vpn_switch.setClickable(config.use_vpn_hotspot);
        if (!remote_control_func.is_vpn_hotsport_exist(context)) {
            use_vpn_switch.setClickable(false);
        }
        if (!Settings.System.canWrite(context) && remote_control_func.is_vpn_hotsport_exist(context)) {
            use_vpn_switch.setClickable(true);
        }
        if (Settings.System.canWrite(context) && remote_control_func.is_vpn_hotsport_exist(context)) {
            use_vpn_switch.setEnabled(true);
        }

        delay.setText(String.valueOf(config.delay));
        disable_count.setText(String.valueOf(config.disable_count));
        enable_count.setText(String.valueOf(config.enable_count));
        new AlertDialog.Builder(this).setTitle("Beacon configuration")
                .setView(dialog_view)
                .setPositiveButton(R.string.ok_button, (dialog, which) -> {
                    config.opposite = enable.isChecked();
                    config.use_vpn_hotspot = use_vpn_switch.isChecked();
                    config.delay = Long.parseLong(delay.getText().toString());
                    config.disable_count = Integer.parseInt(disable_count.getText().toString());
                    config.enable_count = Integer.parseInt(enable_count.getText().toString());
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

            title_view.setText(list.get(position).title);
            address_view.setText("Address: " + list.get(position).address);
            info_view.setText(list.get(position).info);
            if (listen_list.contains(list.get(position).address)) {
                check_box_view.setChecked(true);
            }
            check_box_view.setOnClickListener(v -> {
                String address = list.get(position).address;
                ArrayList<String> listen_list_temp = Paper.book("beacon_config").read("address", new ArrayList<>());
                if (check_box_view.isChecked()) {
                    if (!listen_list_temp.contains(address)) {
                        listen_list_temp.add(address);
                    }
                } else {
                    listen_list_temp.remove(address);
                }
                Log.d(TAG, "beacon_address: " + listen_list_temp);
                Paper.book("beacon_config").write("address", listen_list_temp);
                listen_list = listen_list_temp;
            });

            return view;
        }
    }
}


