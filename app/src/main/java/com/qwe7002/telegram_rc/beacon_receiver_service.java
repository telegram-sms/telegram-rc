package com.qwe7002.telegram_rc;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Region;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import io.paperdb.Paper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class beacon_receiver_service extends Service {

    private final static String TAG = "beacon_receviver";
    private Context context;
    private WifiManager wifi_manager;
    private int not_found_count = 0;
    private int detect_singal_count = 0;
    private OkHttpClient okhttp_client;
    private String chat_id;
    private String request_url;
    private BeaconManager beacon_manager;
    private long startup_time = 0;
    private beacon_config config;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = public_func.get_notification_obj(context, getString(R.string.beacon_receiver));
        startForeground(99, notification);
        if (Paper.book().read("disable_beacon", false)) {
            stopSelf();
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final BroadcastReceiver stop_beacon_service = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopSelf();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        Paper.init(context);
        config = Paper.book().read("beacon_config", new beacon_config());

        SharedPreferences sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE);
        request_url = public_func.get_url(sharedPreferences.getString("bot_token", ""), "SendMessage");
        chat_id = sharedPreferences.getString("chat_id", "");
        wifi_manager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        okhttp_client = public_func.get_okhttp_obj(sharedPreferences.getBoolean("doh_switch", true), Paper.book().read("proxy_config", new proxy_config()));
        beacon_service_consumer beacon_consumer = new beacon_service_consumer();
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

        beacon_manager.setBackgroundScanPeriod(config.delay);
        beacon_manager.setForegroundScanPeriod(config.delay);
        beacon_manager.setForegroundBetweenScanPeriod(config.delay);
        beacon_manager.setForegroundBetweenScanPeriod(config.delay);
        beacon_manager.bind(beacon_consumer);
        registerReceiver(stop_beacon_service, new IntentFilter(public_func.BROADCAST_STOP_BEACON_SERVICE));
        startup_time = System.currentTimeMillis();

    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        unregisterReceiver(stop_beacon_service);
        super.onDestroy();
    }

    class beacon_service_consumer implements BeaconConsumer {
        @Override
        public void onBeaconServiceConnect() {
            beacon_manager.addRangeNotifier((beacons, region) -> {
                if ((System.currentTimeMillis() - startup_time) < 10000L) {
                    return;
                }
                if (Paper.book().read("disable_beacon", false)) {
                    stopSelf();
                    return;
                }
                String message = getString(R.string.system_message_head) + "\n" + getString(R.string.open_wifi) + getString(R.string.action_success);
                boolean found_beacon = false;
                ArrayList<String> listen_beacon_list = Paper.book().read("beacon_address", new ArrayList<>());
                if (listen_beacon_list.size() == 0) {
                    Log.d(TAG, "onBeaconServiceConnect: Watchlist is empty");
                    stopSelf();
                }
                Beacon detect_beacon = null;
                for (Beacon beacon : beacons) {
                    Log.d(TAG, "Mac address: " + beacon.getBluetoothAddress() + " Rssi: " + beacon.getRssi() + " Power: " + beacon.getTxPower() + " Distance: " + beacon.getDistance());
                    for (String beacon_address : listen_beacon_list) {
                        if (beacon.getBluetoothAddress().equals(beacon_address)) {
                            not_found_count = 0;
                            found_beacon = true;
                            break;
                        }
                    }
                    if (found_beacon) {
                        detect_beacon = beacon;
                        break;
                    }
                }
                if (found_beacon) {
                    if (Paper.book().read("wifi_open", false)) {
                        Log.d(TAG, "close ap action count: " + detect_singal_count);
                        if (detect_singal_count >= config.disable_count) {
                            detect_singal_count = 0;
                            close_ap();
                            message = getString(R.string.system_message_head) + "\n" + getString(R.string.close_wifi) + getString(R.string.action_success);
                            network_progress_handle(message + "\nBeacon Rssi: " + detect_beacon.getRssi() + "dBm", chat_id, okhttp_client);
                        }
                        ++detect_singal_count;
                    }

                } else {
                    Log.d(TAG, "Beacon not found, beacons size:" + beacons.size());
                    if (not_found_count >= config.enable_count && !Paper.book().read("wifi_open", false)) {
                        not_found_count = 0;
                        open_ap();
                        network_progress_handle(message + "\nBeacon Not Found.", chat_id, okhttp_client);
                    }
                    Log.d(TAG, "onBeaconServiceConnect: " + not_found_count);
                    ++not_found_count;
                }
            });

            try {
                beacon_manager.startRangingBeaconsInRegion(new Region(getPackageName(), null, null, null));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        private void close_ap() {
            Paper.book().write("wifi_open", false);
            com.qwe7002.root_kit.network.wifi_set_enable(false);
            try {
                while (wifi_manager.getWifiState() != WifiManager.WIFI_STATE_DISABLED) {
                    //noinspection BusyWait
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        private void open_ap() {
            Paper.book().write("wifi_open", true);
            com.qwe7002.root_kit.network.wifi_set_enable(true);
            try {
                while (wifi_manager.getWifiState() != WifiManager.WIFI_STATE_ENABLED) {
                    //noinspection BusyWait
                    Thread.sleep(100);
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                com.qwe7002.root_kit.activity_manage.start_foreground_service(public_func.VPN_HOTSPOT_PACKAGE_NAME, public_func.VPN_HOTSPOT_PACKAGE_NAME + ".RepeaterService");
            } else {
                com.qwe7002.root_kit.activity_manage.start_service(public_func.VPN_HOTSPOT_PACKAGE_NAME, public_func.VPN_HOTSPOT_PACKAGE_NAME + ".RepeaterService");
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

    private void network_progress_handle(String message, String chat_id, OkHttpClient okhttp_client) {
        message_json request_body = new message_json();
        request_body.chat_id = chat_id;
        request_body.text = message + "\n" + context.getString(R.string.current_battery_level) + chat_command_service.get_battery_info(context) + "\n" + getString(R.string.current_network_connection_status) + chat_command_service.get_network_type(context);
        String request_body_json = new Gson().toJson(request_body);
        RequestBody body = RequestBody.create(request_body_json, public_func.JSON);
        Request request_obj = new Request.Builder().url(request_url).method("POST", body).build();
        Call call = okhttp_client.newCall(request_obj);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                Log.d(TAG, "onResponse: " + Objects.requireNonNull(response.body()).string());
            }
        });
    }
}
