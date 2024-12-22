package com.qwe7002.telegram_rc

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qwe7002.telegram_rc.config.beacon
import com.qwe7002.telegram_rc.data_structure.BeaconModel
import com.qwe7002.telegram_rc.data_structure.beaconItemName
import com.qwe7002.telegram_rc.static_class.RemoteControl.isVPNHotspotExist
import io.paperdb.Paper

class BeaconActivity : AppCompatActivity() {
    private val flushReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val gson = Gson()
            val arrayList = gson.fromJson<ArrayList<BeaconModel>>(
                intent.getStringExtra("beaconList"),
                object : TypeToken<ArrayList<BeaconModel>>() {}.type
            )!!
            flushListView(arrayList)
        }
    }

    fun flushListView(arrayList: ArrayList<BeaconModel>) {
        val beaconListview = findViewById<ListView>(R.id.beacon_listview)
        if (arrayList.isNotEmpty()) {
            val list = ArrayList<BeaconModel>()
            for (beacon in arrayList) {
                if (beacon.distance < 200.0) {
                    list.add(beacon)
                }
            }

            runOnUiThread {
                val adapter = CustomBeaconAdapter(list, this@BeaconActivity)
                beaconListview.adapter = adapter
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = applicationContext
        Paper.init(context)
        setContentView(R.layout.activity_beacon)
        flushListView(ArrayList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                flushReceiver,
                IntentFilter("flush_beacons_list"), RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(
                flushReceiver,
                IntentFilter("flush_beacons_list")
            )
        }
    }

    override fun onDestroy() {
        unregisterReceiver(flushReceiver)
        super.onDestroy()
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val context = applicationContext
        val inflater = this.layoutInflater
        val dialogView = inflater.inflate(R.layout.set_beacon_layout, null)
        val enable = dialogView.findViewById<SwitchMaterial>(R.id.beacon_enable_switch)
        val useVpnHotspotSwitch =
            dialogView.findViewById<SwitchMaterial>(R.id.beacon_use_vpn_hotspot_switch)
        val disableCount = dialogView.findViewById<EditText>(R.id.beacon_disable_count_editview)
        val enableCount = dialogView.findViewById<EditText>(R.id.beacon_enable_count_editview)
        val config = Paper.book("beacon").read("config", beacon())!!
        useVpnHotspotSwitch.isChecked = config.useVpnHotspot
        if (!isVPNHotspotExist(context)) {
            useVpnHotspotSwitch.isChecked = false
        }
        if (!Settings.System.canWrite(context) && isVPNHotspotExist(context)) {
            useVpnHotspotSwitch.isChecked = true
        }
        if (Settings.System.canWrite(context) && isVPNHotspotExist(context)) {
            useVpnHotspotSwitch.isEnabled = true
        }

        disableCount.setText(config.disableCount)
        enableCount.setText(config.enableCount)
        AlertDialog.Builder(this).setTitle("Beacon configuration")
            .setView(dialogView)
            .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                config.opposite = enable.isChecked
                config.useVpnHotspot = useVpnHotspotSwitch.isChecked
                config.disableCount = disableCount.text.toString().toInt()
                config.enableCount = enableCount.text.toString().toInt()
                Paper.book("beacon").write("config", config)
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent("reload_beacon_config"))
            }.show()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.beacon_menu, menu)
        return true
    }

    internal class CustomBeaconAdapter(var list: ArrayList<BeaconModel>, var context: Context) :
        BaseAdapter() {
        private var listenList: ArrayList<String>?

        init {
            listenList = Paper.book("beacon").read("address", ArrayList())
        }

        override fun getCount(): Int {
            return list.size
        }

        override fun getItem(position: Int): Any {
            return list[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        @SuppressLint("SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val inflater = LayoutInflater.from(context)
            @SuppressLint("ViewHolder", "InflateParams") val view =
                inflater.inflate(R.layout.item_beacon, null)

            val titleView = view.findViewById<TextView>(R.id.beacon_title_textview)
            val addressView = view.findViewById<TextView>(R.id.beacon_address_textview)
            val infoView = view.findViewById<TextView>(R.id.beacon_info_textview)
            val checkBoxView = view.findViewById<CheckBox>(R.id.beacon_select_checkbox)
            val beacon = list[position]
            titleView.text = beacon.uuid
            addressView.text =
                "Major: " + beacon.major + " Minor: " + beacon.minor + " Rssi: " + beacon.rssi + " dBm"
            infoView.text = "Distance: " + beacon.distance.toInt() + " meters"
            if (listenList!!.contains(beaconItemName(beacon.uuid, beacon.major, beacon.minor))) {
                checkBoxView.isChecked = true
            }
            checkBoxView.setOnClickListener {
                val address = beaconItemName(beacon.uuid, beacon.major, beacon.minor)
                val listenListTemp = Paper.book("beacon").read("address", ArrayList<String>())
                if (checkBoxView.isChecked) {
                    assert(listenListTemp != null)
                    if (!listenListTemp!!.contains(address)) {
                        listenListTemp.add(address)
                    }
                } else {
                    assert(listenListTemp != null)
                    listenListTemp!!.remove(address)
                }
                Log.d("monitoring_activity", "beacon_address: $listenListTemp")
                Paper.book("beacon").write("address", listenListTemp)
                listenList = listenListTemp
            }

            return view
        }
    }



}


