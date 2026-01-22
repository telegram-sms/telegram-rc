package com.qwe7002.telegram_rc

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.qwe7002.telegram_rc.data_structure.CcSendService
import com.qwe7002.telegram_rc.value.RESULT_CONFIG_JSON

import com.qwe7002.telegram_rc.value.TAG
import com.tencent.mmkv.MMKV

class CcActivity : AppCompatActivity() {
    private lateinit var listAdapter: ArrayAdapter<CcSendService>
    private lateinit var serviceList: ArrayList<CcSendService>
    private lateinit var preferences: MMKV
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_cc)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        FakeStatusBar().fakeStatusBar(this, window)
        preferences = MMKV.defaultMMKV()
        val inflater = this.layoutInflater
        val fab = findViewById<FloatingActionButton>(R.id.cc_fab)
        val ccList = findViewById<ListView>(R.id.cc_list)
        val serviceListJson = preferences.getString("cc_service_list", "[]")
        val gson = Gson()
        val type = object : TypeToken<ArrayList<CcSendService>>() {}.type
        serviceList = gson.fromJson(serviceListJson, type)
        listAdapter =
            object :
                ArrayAdapter<CcSendService>(this, R.layout.list_item_with_subtitle, serviceList) {
                @SuppressLint("SetTextI18n")
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: layoutInflater.inflate(
                        R.layout.list_item_with_subtitle,
                        parent,
                        false
                    )
                    val item = getItem(position)

                    val title = view.findViewById<TextView>(R.id.title)
                    val subtitle = view.findViewById<TextView>(R.id.subtitle)

                    title.text =
                        CcSendJob.options[item?.method!!] + item.enabled.let { if (it) " (Enabled)" else " (Disabled)" }
                    subtitle.text = item.webhook

                    return view
                }
            }
        ccList.adapter = listAdapter
        ccList.onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                val dialog = inflater.inflate(R.layout.set_cc_layout, null)
                val spinner = dialog.findViewById<Spinner>(R.id.spinner_options)
                val adapter =
                    ArrayAdapter(this, android.R.layout.simple_spinner_item, CcSendJob.options)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter
                spinner.setSelection(serviceList[position].method)
                spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        Log.d(TAG, position.toString())
                        when (position) {
                            0 -> dialog.findViewById<EditText>(R.id.body_editview).isEnabled = false
                            1 -> dialog.findViewById<EditText>(R.id.body_editview).isEnabled = true
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        Log.d(TAG, "nothing")
                    }
                }
                val webhook =
                    dialog.findViewById<EditText>(R.id.webhook_editview)
                webhook.setText(serviceList[position].webhook)
                webhook.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }

                    override fun afterTextChanged(s: Editable?) {
                        val text = s.toString()
                        if (!isValidUrl(text)) {
                            webhook.error = "Invalid URL"
                        }
                    }
                })
                val body =
                    dialog.findViewById<EditText>(R.id.body_editview)
                body.setText(serviceList[position].body)
                body.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }

                    override fun afterTextChanged(s: Editable?) {
                        val text = s.toString()
                        if (!isValidJson(text)) {
                            body.error = "Invalid JSON"
                        }
                    }
                })
                val header =
                    dialog.findViewById<EditText>(R.id.header_editview)
                header.setText(serviceList[position].header)
                header.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }

                    override fun afterTextChanged(s: Editable?) {
                        val text = s.toString()
                        if (!isValidJson(text)) {
                            header.error = "Invalid JSON"
                        }
                    }
                })
                val switch =
                    dialog.findViewById<SwitchMaterial>(R.id.cc_enable_switch)
                switch.isChecked = serviceList[position].enabled
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.edit_cc_service))
                    .setView(dialog)
                    .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                        CcSendService(
                            method = spinner.selectedItemPosition,
                            webhook = webhook.text.toString(),
                            body = body.text.toString(),
                            header = header.text.toString(),
                            enabled = switch.isChecked
                        ).also { serviceList[position] = it }
                        saveAndFlush(serviceList, listAdapter)
                    }
                    .setNeutralButton(R.string.cancel_button, null)
                    .setNegativeButton(
                        R.string.delete_button,
                        ((DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
                            serviceList.removeAt(position)
                            saveAndFlush(serviceList, listAdapter)
                        }))
                    )
                    .show()
            }

        fab.setOnClickListener {
            val dialog = inflater.inflate(R.layout.set_cc_layout, null)
            val spinner = dialog.findViewById<Spinner>(R.id.spinner_options)

            val adapter =
                ArrayAdapter(this, android.R.layout.simple_spinner_item, CcSendJob.options)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    Log.d(TAG, position.toString())
                    when (position) {
                        0 -> dialog.findViewById<EditText>(R.id.body_editview).isEnabled = false
                        1 -> dialog.findViewById<EditText>(R.id.body_editview).isEnabled = true
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    Log.d(TAG, "nothing")
                }
            }
            val webhook = dialog.findViewById<EditText>(R.id.webhook_editview)
            webhook.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString()
                    if (!isValidUrl(text)) {
                        webhook.error = "Invalid URL"
                    }
                }
            })
            val body =
                dialog.findViewById<EditText>(R.id.body_editview)
            body.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString()
                    if (!isValidJson(text)) {
                        body.error = "Invalid JSON"
                    }
                }
            })
            val header =
                dialog.findViewById<EditText>(R.id.header_editview)
            header.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val text = s.toString()
                    if (!isValidJson(text)) {
                        header.error = "Invalid JSON"
                    }
                }
            })
            AlertDialog.Builder(this).setTitle(getString(R.string.add_cc_service))
                .setView(dialog)
                .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                    CcSendService(
                        method = spinner.selectedItemPosition,
                        webhook = webhook.text.toString(),
                        body = body.text.toString(),
                        header = header.text.toString(),
                        enabled = dialog.findViewById<SwitchMaterial>(R.id.cc_enable_switch).isChecked
                    ).also { serviceList.add(it) }
                    saveAndFlush(serviceList, listAdapter)
                }
                .setNeutralButton(R.string.cancel_button, null)
                .show()
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("https://")
    }

    @SuppressLint("CheckResult")
    fun isValidJson(json: String): Boolean {
        return try {
            JsonParser.parseString(json)
            true
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "isValidJson: " + e.message)
            false
        }
    }

    private fun saveAndFlush(
        serviceList: ArrayList<CcSendService>,
        listAdapter: ArrayAdapter<CcSendService>
    ) {
        Log.d(TAG, serviceList.toString())
        preferences.putString("cc_service_list", Gson().toJson(serviceList))
        listAdapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.cc_menu, menu)
        return true
    }

    @SuppressLint("NonConstantResourceId", "SetTextI18n")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.scan_menu_item -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0)
                return true
            }

            R.id.send_test_menu_item -> {
                if (serviceList.isEmpty()) {
                    Snackbar.make(
                        findViewById(R.id.send_test_menu_item),
                        "No service available.",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    return true
                }
                CcSendJob.startJob(
                    applicationContext,
                    getString(R.string.app_name),
                    getString(R.string.system_message_head) + "\n Test message.",
                    getString(R.string.system_message_head) + "\n Test message."
                )
                Snackbar.make(
                    findViewById(R.id.send_test_menu_item),
                    "Test message sent.",
                    Snackbar.LENGTH_SHORT
                ).show()
                return true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            0 -> {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "onRequestPermissionsResult: No camera permissions.")
                    Snackbar.make(
                        findViewById(R.id.bot_token_editview),
                        R.string.no_camera_permission,
                        Snackbar.LENGTH_LONG
                    ).show()
                    return
                }
                val intent = Intent(applicationContext, ScannerActivity::class.java)
                @Suppress("DEPRECATION")
                startActivityForResult(intent, 1)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: $resultCode")
        if (requestCode == 1) {
            if (resultCode == RESULT_CONFIG_JSON) {
                val gson = Gson()
                val jsonConfig = gson.fromJson(
                    data!!.getStringExtra("config_json"),
                    CcSendService::class.java
                )
                Log.d(TAG, "onActivityResult: $jsonConfig")
                serviceList.add(jsonConfig)
                saveAndFlush(serviceList, listAdapter)
            }
        }
    }
}
