package com.qwe7002.telegram_rc

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import com.google.android.material.switchmaterial.SwitchMaterial
import com.qwe7002.telegram_rc.data_structure.BeaconModel
import com.qwe7002.telegram_rc.shizuku_kit.VPNHotspot
import com.qwe7002.telegram_rc.static_class.BeaconDataRepository
import com.qwe7002.telegram_rc.static_class.Const
import com.tencent.mmkv.MMKV
import rikka.shizuku.Shizuku

class BeaconActivity : AppCompatActivity() {
    private lateinit var beaconMMKV: MMKV
    private var customBeaconAdapter: CustomBeaconAdapter? = null
    private val beaconListObserver = Observer<ArrayList<BeaconModel.BeaconModel>> { arrayList ->
        flushListView(arrayList)
    }

    private val reloadConfigObserver = Observer<Boolean> { reload ->
        if (reload) {
            // Reload configuration if needed
            BeaconDataRepository.resetReloadConfig()
        }
    }

    fun flushListView(arrayList: ArrayList<BeaconModel.BeaconModel>) {
        val beaconListview = findViewById<ListView>(R.id.beacon_listview)
        if (arrayList.isNotEmpty()) {
            val list = ArrayList<BeaconModel.BeaconModel>()
            for (beacon in arrayList) {
                if (beacon.distance < 200.0) {
                    list.add(beacon)
                }
            }

            runOnUiThread {
                if (customBeaconAdapter == null) {
                    customBeaconAdapter = CustomBeaconAdapter(list, this@BeaconActivity)
                    beaconListview.adapter = customBeaconAdapter
                } else {
                    customBeaconAdapter?.updateList(list)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        beaconMMKV = MMKV.mmkvWithID(Const.BEACON_MMKV_ID)
        setContentView(R.layout.activity_beacon)
        flushListView(ArrayList())
        
        // Observe beacon list updates
        BeaconDataRepository.beaconList.observe(this, beaconListObserver)
        BeaconDataRepository.reloadConfig.observe(this, reloadConfigObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val inflater = this.layoutInflater
        val dialogView = inflater.inflate(R.layout.set_beacon_layout, null)
        val enable = dialogView.findViewById<SwitchMaterial>(R.id.beacon_enable_switch)
        val disableCount = dialogView.findViewById<EditText>(R.id.beacon_disable_count_editview)
        val enableCount = dialogView.findViewById<EditText>(R.id.beacon_enable_count_editview)
        val useVpnHotspotSwitch =
            dialogView.findViewById<SwitchMaterial>(R.id.beacon_use_vpn_hotspot_switch)
        disableCount.setText(beaconMMKV.getInt("disableCount", 10).toString())
        enableCount.setText(beaconMMKV.getInt("enableCount", 10).toString())
        if(Shizuku.pingBinder()&& Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            useVpnHotspotSwitch.isChecked = beaconMMKV.getBoolean("useVpnHotspot", false) &&
                    VPNHotspot.isVPNHotspotExist(applicationContext)
            useVpnHotspotSwitch.isEnabled =
                Settings.System.canWrite(applicationContext) && VPNHotspot.isVPNHotspotExist(
                    applicationContext
                )
        }
        
        // Check if we have background location permission
        val hasBackgroundLocationPermission = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // If we don't have background location permission, show a warning
        if (!hasBackgroundLocationPermission) {
            AlertDialog.Builder(this)
                .setTitle("Warning")
                .setMessage("Background location permission is required for beacon monitoring to work properly in the background")
                .setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int ->
                    // Continue with configuration
                    showBeaconConfigurationDialog(dialogView, enable, disableCount, enableCount, useVpnHotspotSwitch)
                }
                .setNegativeButton(android.R.string.cancel) { _: DialogInterface, _: Int -> }
                .show()
        } else {
            showBeaconConfigurationDialog(dialogView, enable, disableCount, enableCount, useVpnHotspotSwitch)
        }
        return true
    }
    
    private fun showBeaconConfigurationDialog(
        dialogView: View,
        enable: SwitchMaterial,
        disableCount: EditText,
        enableCount: EditText,
        useVpnHotspotSwitch: SwitchMaterial
    ) {
        AlertDialog.Builder(this).setTitle(R.string.beacon_receiver_configuration)
            .setView(dialogView)
            .setPositiveButton(R.string.ok_button) { _: DialogInterface, _: Int ->
                beaconMMKV.putBoolean("useVpnHotspot", useVpnHotspotSwitch.isChecked)
                beaconMMKV.putInt("disableCount", disableCount.text.toString().toIntOrNull() ?: 10)
                beaconMMKV.putInt("enableCount", enableCount.text.toString().toIntOrNull() ?: 10)
                beaconMMKV.putBoolean("opposite", enable.isChecked)
                BeaconDataRepository.triggerReloadConfig()
            }.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.beacon_menu, menu)
        return true
    }

    internal class CustomBeaconAdapter(
        var list: ArrayList<BeaconModel.BeaconModel>,
        var context: Context
    ) :
        BaseAdapter() {
        private var beaconMMKV: MMKV = MMKV.mmkvWithID(Const.BEACON_MMKV_ID)
        private var listenList: ArrayList<String> =
            beaconMMKV.decodeStringSet("address", setOf()).orEmpty().toCollection(ArrayList())

        fun updateList(newList: ArrayList<BeaconModel.BeaconModel>) {
            list = newList
            notifyDataSetChanged()
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
            if (listenList.contains(
                    BeaconModel.beaconItemName(
                        beacon.uuid,
                        beacon.major,
                        beacon.minor
                    )
                )
            ) {
                checkBoxView.isChecked = true
            }
            checkBoxView.setOnClickListener {
                val address = BeaconModel.beaconItemName(beacon.uuid, beacon.major, beacon.minor)
                val listenListTemp =
                    beaconMMKV.decodeStringSet("address", setOf()).orEmpty().toMutableList()
                if (checkBoxView.isChecked) {
                    if (!listenListTemp.contains(address)) {
                        listenListTemp.add(address)
                    }
                } else {
                    listenListTemp.remove(address)
                }
                Log.d("monitoring_activity", "beacon_address: $listenListTemp")
                beaconMMKV.encode("address", listenListTemp.toSet())
                listenList = listenListTemp as ArrayList<String>
            }

            return view
        }
    }


}
