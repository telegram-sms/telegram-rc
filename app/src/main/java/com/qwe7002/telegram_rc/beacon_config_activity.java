package com.qwe7002.telegram_rc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.RemoteException;
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

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;

import io.paperdb.Paper;

public class beacon_config_activity extends AppCompatActivity {
    protected static final String TAG = "monitoring_activity";
    private beacon_consumer beacon_consumer_obj;
    private ListView beaconList;
    private BeaconManager beacon_manager;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Paper.init(getApplicationContext());
        setContentView(R.layout.activity_beacon);
        context = getApplicationContext();
        beacon_consumer_obj = new beacon_consumer();
        beaconList = findViewById(R.id.beacon_list);

        beacon_manager = BeaconManager.getInstanceForApplication(this);
        beacon_manager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        // Detect the main identifier (UID) frame:
        beacon_manager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout(BeaconParser.EDDYSTONE_UID_LAYOUT));

        // Detect the telemetry (TLM) frame:
        beacon_manager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout(BeaconParser.EDDYSTONE_TLM_LAYOUT));

        // Detect the URL frame:
        beacon_manager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout(BeaconParser.EDDYSTONE_URL_LAYOUT));
        beacon_manager.bind(beacon_consumer_obj);

    }


    @Override
    protected void onDestroy() {
        beacon_manager.unbind(beacon_consumer_obj);
        public_func.start_beacon_service(context);
        super.onDestroy();
    }

    static class BeaconModel {
        String title;
        String address;
        String info;
    }

    static class CustomBeaconAdapter extends BaseAdapter {
        ArrayList<BeaconModel> list;
        Context context;
        ArrayList<String> listen_list;

        public CustomBeaconAdapter(ArrayList<BeaconModel> list, Context context) {
            this.list = list;
            this.context = context;
            listen_list = Paper.book().read("beacon_address", new ArrayList<>());
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

            TextView title_view = view.findViewById(R.id.beacon_title);
            TextView address_view = view.findViewById(R.id.beacon_address);
            TextView info_view = view.findViewById(R.id.beacon_info);
            final CheckBox check_box_view = view.findViewById(R.id.beacon_select_checkbox);

            title_view.setText(list.get(position).title);
            address_view.setText("Address: " + list.get(position).address);
            info_view.setText(list.get(position).info);
            if (listen_list.contains(list.get(position).address)) {
                check_box_view.setChecked(true);
            }
            check_box_view.setOnClickListener(v -> {
                String address = list.get(position).address;
                ArrayList<String> listen_list_temp = Paper.book().read("beacon_address", new ArrayList<>());
                if (check_box_view.isChecked()) {
                    if (!listen_list_temp.contains(address)) {
                        listen_list_temp.add(address);
                    }
                } else {
                    listen_list_temp.remove(address);
                }
                Log.d(TAG, "beacon_address: " + listen_list_temp);
                Paper.book().write("beacon_address", listen_list_temp);
                listen_list = listen_list_temp;
            });

            return view;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.beacon_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        LayoutInflater inflater = this.getLayoutInflater();
        @SuppressLint("InflateParams") View dialog_view = inflater.inflate(R.layout.set_beacon_layout, null);
        EditText delay = dialog_view.findViewById(R.id.beacon_delay);
        EditText disable_count = dialog_view.findViewById(R.id.beacon_disable_count);
        EditText enable_count = dialog_view.findViewById(R.id.beacon_enable_count);
        beacon_config config = Paper.book().read("beacon_config", new beacon_config());
        delay.setText(String.valueOf(config.delay));
        disable_count.setText(config.disable_count);
        enable_count.setText(config.enable_count);
        new AlertDialog.Builder(this).setTitle("Beacon configuration")
                .setView(dialog_view)
                .setPositiveButton(R.string.ok_button, (dialog, which) -> {
                    config.delay = Long.parseLong(delay.getText().toString());
                    config.disable_count = Integer.parseInt(disable_count.getText().toString());
                    config.enable_count = Integer.parseInt(enable_count.getText().toString());
                    Paper.book().write("beacon_config", config);
                }).show();
        return true;
    }

    class beacon_consumer implements BeaconConsumer {
        @Override
        public void onBeaconServiceConnect() {
            beacon_manager.addRangeNotifier((beacons, region) -> {
                if (beacons.size() > 0) {
                    final ArrayList<BeaconModel> list = new ArrayList<>();

                    for (Beacon beacon : beacons) {
                        Log.d(TAG, "Mac address: " + beacon.getBluetoothAddress() + " Rssi: " + beacon.getRssi() + " Power: " + beacon.getTxPower() + " Distance: " + beacon.getDistance());
                        BeaconModel model = new BeaconModel();
                        model.title = beacon.getBluetoothName();
                        model.address = beacon.getBluetoothAddress();
                        model.info = "Rssi: " + beacon.getRssi() + " dBm Power: " + beacon.getTxPower() + " dBm";
                        list.add(model);
                    }
                    runOnUiThread(() -> {
                        CustomBeaconAdapter adapter = new CustomBeaconAdapter(list, beacon_config_activity.this);
                        beaconList.setAdapter(adapter);
                    });
                }
            });

            try {
                beacon_manager.startRangingBeaconsInRegion(new Region(getPackageName(), null, null, null));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public Context getApplicationContext() {
            return null;
        }

        @Override
        public void unbindService(ServiceConnection serviceConnection) {

        }

        @Override
        public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
            return false;
        }
    }
}
