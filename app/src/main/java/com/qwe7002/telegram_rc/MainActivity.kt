@file:Suppress("DEPRECATION")

package com.qwe7002.telegram_rc

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.qwe7002.telegram_rc.data_structure.ScannerJson
import com.qwe7002.telegram_rc.data_structure.PollingJson
import com.qwe7002.telegram_rc.data_structure.RequestMessage
import com.qwe7002.telegram_rc.static_class.Const
import com.qwe7002.telegram_rc.static_class.LogManage.writeLog
import com.qwe7002.telegram_rc.static_class.Network.getOkhttpObj
import com.qwe7002.telegram_rc.static_class.Network.getUrl
import com.qwe7002.telegram_rc.static_class.Other.getActiveCard
import com.qwe7002.telegram_rc.static_class.Other.parseStringToLong
import com.qwe7002.telegram_rc.static_class.ServiceManage.isNotifyListener
import com.qwe7002.telegram_rc.static_class.ServiceManage.startBeaconService
import com.qwe7002.telegram_rc.static_class.ServiceManage.startService
import com.qwe7002.telegram_rc.static_class.ServiceManage.stopAllService
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Objects
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri
import com.tencent.mmkv.MMKV
import com.topjohnwu.superuser.Shell

class MainActivity : AppCompatActivity() {
    private val TAG = "main_activity"
    private lateinit var preferences: MMKV
    private lateinit var proxyMMKV: MMKV
    private lateinit var writeSettingsButton: Button
    private lateinit var scannerLauncher: ActivityResultLauncher<Intent>
    
    // View components
    private lateinit var botTokenEditView: EditText
    private lateinit var chatIdEditView: EditText
    private lateinit var trustedPhoneNumberEditView: EditText
    private lateinit var messageThreadIdEditView: EditText
    private lateinit var messageThreadIdView: TextInputLayout
    private lateinit var chatCommandSwitch: SwitchMaterial
    private lateinit var fallbackSmsSwitch: SwitchMaterial
    private lateinit var batteryMonitoringSwitch: SwitchMaterial
    private lateinit var dohSwitch: SwitchMaterial
    private lateinit var chargerStatusSwitch: SwitchMaterial
    private lateinit var verificationCodeSwitch: SwitchMaterial
    private lateinit var displayDualSimDisplayNameSwitch: SwitchMaterial
    private lateinit var saveButton: Button
    private lateinit var getIdButton: Button

    @SuppressLint("BatteryLife", "QueryPermissionsNeeded")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 初始化ActivityResultLauncher
        scannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Const.RESULT_CONFIG_JSON) {
                val data = result.data
                val configJson = data?.getStringExtra("config_json")
                if (configJson != null) {
                    val gson = Gson()
                    val scannerJson = gson.fromJson(configJson, ScannerJson::class.java)
                    botTokenEditView.setText(scannerJson.botToken)
                    chatIdEditView.setText(scannerJson.chatId)
                    batteryMonitoringSwitch.isChecked = scannerJson.batteryMonitoringSwitch
                    verificationCodeSwitch.isChecked = scannerJson.verificationCode

                    if (scannerJson.chargerStatus) {
                        chargerStatusSwitch.isChecked = true
                        chargerStatusSwitch.visibility = View.VISIBLE
                    } else {
                        chargerStatusSwitch.isChecked = false
                        chargerStatusSwitch.visibility = View.GONE
                    }

                    chatCommandSwitch.isChecked = scannerJson.chatCommand
                    setPrivacyModeCheckbox(scannerJson.chatId, messageThreadIdView)

                    trustedPhoneNumberEditView.setText(scannerJson.trustedPhoneNumber)
                    fallbackSmsSwitch.isChecked = scannerJson.fallbackSms
                    if (scannerJson.trustedPhoneNumber.isNotEmpty()) {
                        fallbackSmsSwitch.visibility = View.VISIBLE
                    } else {
                        fallbackSmsSwitch.visibility = View.GONE
                        fallbackSmsSwitch.isChecked = false
                    }
                    messageThreadIdEditView.setText(scannerJson.topicID)
                }
            }
        }
        
        // 初始化视图组件
        botTokenEditView = findViewById(R.id.bot_token_editview)
        chatIdEditView = findViewById(R.id.chat_id_editview)
        trustedPhoneNumberEditView = findViewById(R.id.trusted_phone_number_editview)
        messageThreadIdEditView = findViewById(R.id.message_thread_id_editview)
        messageThreadIdView = findViewById(R.id.message_thread_id_view)
        chatCommandSwitch = findViewById(R.id.chat_command_switch)
        fallbackSmsSwitch = findViewById(R.id.fallback_sms_switch)
        batteryMonitoringSwitch = findViewById(R.id.battery_monitoring_switch)
        dohSwitch = findViewById(R.id.doh_switch)
        chargerStatusSwitch = findViewById(R.id.charger_status_switch)
        verificationCodeSwitch = findViewById(R.id.verification_code_switch)
        displayDualSimDisplayNameSwitch = findViewById(R.id.display_dual_sim_switch)
        saveButton = findViewById(R.id.save_button)
        getIdButton = findViewById(R.id.get_id_button)
        writeSettingsButton = findViewById(R.id.write_settings_button)
        
        //load config
        MMKV.initialize(applicationContext)
        preferences = MMKV.defaultMMKV()
        proxyMMKV = MMKV.mmkvWithID(Const.PROXY_MMKV_ID)
        writeSettingsButton.setOnClickListener {
            val writeSystemIntent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:$packageName".toUri()
            )
            startActivity(writeSystemIntent)
        }
        if (!preferences.getBoolean("privacy_dialog_agree", false)) {
            showPrivacyDialog()
        }
        val botTokenSave = preferences.getString("bot_token", "")
        val chatIdSave = preferences.getString("chat_id", "")
        val messageThreadIdSave = preferences.getString("message_thread_id", "")

        if (preferences.contains("initialized")) {
            startService(
                applicationContext,
                preferences.getBoolean("battery_monitoring_switch", false),
                preferences.getBoolean("chat_command", false)
            )
            Log.i("BootReceiver", Shell.getShell().isRoot.toString())
            startBeaconService(applicationContext)
            KeepAliveJob.startJob(applicationContext)
            ReSendJob.startJob(applicationContext)
        }


        var displayDualSimDisplayName =
            preferences.getBoolean("display_dual_sim_display_name", false)
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (getActiveCard(applicationContext) < 2) {
                displayDualSimDisplayNameSwitch.isEnabled = false
                displayDualSimDisplayName = false
            }
            displayDualSimDisplayNameSwitch.isChecked = displayDualSimDisplayName
        }

        botTokenEditView.setText(botTokenSave)
        chatIdEditView.setText(chatIdSave)
        messageThreadIdEditView.setText(messageThreadIdSave)
        trustedPhoneNumberEditView.setText(preferences.getString("trusted_phone_number", ""))

        batteryMonitoringSwitch.isChecked =
            preferences.getBoolean("battery_monitoring_switch", false)
        chargerStatusSwitch.isChecked = preferences.getBoolean("charger_status", false)

        if (!batteryMonitoringSwitch.isChecked) {
            chargerStatusSwitch.isChecked = false
            chargerStatusSwitch.visibility = View.GONE
        }

        batteryMonitoringSwitch.setOnClickListener {
            if (batteryMonitoringSwitch.isChecked) {
                chargerStatusSwitch.visibility = View.VISIBLE
            } else {
                chargerStatusSwitch.visibility = View.GONE
                chargerStatusSwitch.isChecked = false
            }
        }

        fallbackSmsSwitch.isChecked = preferences.getBoolean("fallback_sms", false)
        if (trustedPhoneNumberEditView.length() == 0) {
            fallbackSmsSwitch.visibility = View.GONE
            fallbackSmsSwitch.isChecked = false
        }
        trustedPhoneNumberEditView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                if (trustedPhoneNumberEditView.length() != 0) {
                    fallbackSmsSwitch.visibility = View.VISIBLE
                } else {
                    fallbackSmsSwitch.visibility = View.GONE
                    fallbackSmsSwitch.isChecked = false
                }
            }
        })

        chatCommandSwitch.isChecked = preferences.getBoolean("chat_command", false)
        chatCommandSwitch.setOnClickListener {
            setPrivacyModeCheckbox(
                chatIdEditView.text.toString(),
                messageThreadIdView
            )
        }
        verificationCodeSwitch.isChecked =
            preferences.getBoolean("verification_code", false)

        dohSwitch.isChecked = preferences.getBoolean("doh_switch", true)
        dohSwitch.isEnabled = !proxyMMKV.getBoolean("enabled", false)
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val tm = checkNotNull(getSystemService(TELEPHONY_SERVICE) as TelephonyManager)
            val phoneCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tm.activeModemCount
            } else {
                @Suppress("DEPRECATION")
                tm.phoneCount
            }
            if (phoneCount <= 1) {
                displayDualSimDisplayNameSwitch.visibility = View.GONE
            }
        }
        displayDualSimDisplayNameSwitch.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                displayDualSimDisplayNameSwitch.isChecked = false
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.READ_PHONE_STATE),
                    1
                )
            } else {
                if (getActiveCard(applicationContext) < 2) {
                    displayDualSimDisplayNameSwitch.isEnabled = false
                    displayDualSimDisplayNameSwitch.isChecked = false
                }
            }
        }

        chatIdEditView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                setPrivacyModeCheckbox(
                    chatIdEditView.text.toString(),
                    messageThreadIdView
                )
            }

            override fun afterTextChanged(s: Editable) {
            }
        })
        getIdButton.setOnClickListener { v: View ->
            if (botTokenEditView.text.toString().isEmpty()) {
                showErrorDialog(applicationContext.getString(R.string.token_not_configure))
                return@setOnClickListener
            }
            Thread { stopAllService(applicationContext) }.start()
            val progressDialog = ProgressDialog(this)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.setTitle(getString(R.string.get_recent_chat_title))
            progressDialog.setMessage(getString(R.string.get_recent_chat_message))
            progressDialog.isIndeterminate = false
            progressDialog.setCancelable(false)
            progressDialog.show()
            val requestUri =
                getUrl(botTokenEditView.text.toString().trim { it <= ' ' }, "getUpdates")
            var okhttpClient = getOkhttpObj()
            okhttpClient = okhttpClient.newBuilder()
                .readTimeout(65, TimeUnit.SECONDS)
                .build()
            val requestBody = PollingJson()
            requestBody.timeout = 60
            val body: RequestBody = RequestBody.create(Const.JSON, Gson().toJson(requestBody))
            val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
            val call = okhttpClient.newCall(request)
            progressDialog.setOnKeyListener { _: DialogInterface?, _: Int, keyEvent: KeyEvent ->
                if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    call.cancel()
                }
                false
            }
            val errorHead = "Get chat ID failed:"
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "onFailure: ", e)
                    progressDialog.cancel()
                    val errorMessage = errorHead + e.message
                    writeLog(applicationContext, errorMessage)
                    runOnUiThread { showErrorDialog(errorMessage) }
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    progressDialog.cancel()
                    if (response.code != 200) {
                        val result = Objects.requireNonNull(response.body).string()
                        val resultObj = JsonParser.parseString(result).asJsonObject
                        val errorMessage = errorHead + resultObj["description"].asString
                        writeLog(applicationContext, errorMessage)
                        runOnUiThread { showErrorDialog(errorMessage) }
                        return
                    }
                    val result = Objects.requireNonNull(response.body).string()
                    val resultObj = JsonParser.parseString(result).asJsonObject
                    val chatList = resultObj.getAsJsonArray("result")
                    if (chatList.isEmpty) {
                        runOnUiThread { showErrorDialog(applicationContext.getString(R.string.unable_get_recent)) }
                        return
                    }
                    val chatNameList = ArrayList<String>()
                    val chatIdList = ArrayList<String>()
                    val chatTopicIdList = ArrayList<String>()
                    for (item in chatList) {
                        val itemObj = item.asJsonObject
                        if (itemObj.has("message")) {
                            val messageObj = itemObj["message"].asJsonObject
                            val chatObj = messageObj["chat"].asJsonObject
                            if (!chatIdList.contains(chatObj["id"].asString)) {
                                var username = StringBuilder()
                                if (chatObj.has("username")) {
                                    username = StringBuilder(chatObj["username"].asString)
                                }
                                if (chatObj.has("title")) {
                                    username = StringBuilder(chatObj["title"].asString)
                                }
                                if (username.toString().isEmpty() && !chatObj.has("username")) {
                                    if (chatObj.has("first_name")) {
                                        username = StringBuilder(chatObj["first_name"].asString)
                                    }
                                    if (chatObj.has("last_name")) {
                                        username.append(" ").append(chatObj["last_name"].asString)
                                    }
                                }
                                val type = chatObj["type"].asString
                                chatNameList.add("$username($type)")
                                chatIdList.add(chatObj["id"].asString)
                                var threadId = ""
                                if (type == "supergroup" && messageObj.has("is_topic_message")) {
                                    threadId = messageObj["message_thread_id"].asString
                                }
                                chatTopicIdList.add(threadId)
                            }
                        }
                        if (itemObj.has("channel_post")) {
                            val messageObj = itemObj["channel_post"].asJsonObject
                            val chatObj = messageObj["chat"].asJsonObject
                            if (!chatIdList.contains(chatObj["id"].asString)) {
                                chatNameList.add(chatObj["title"].asString + "(Channel)")
                                chatIdList.add(chatObj["id"].asString)
                            }
                        }
                    }
                    this@MainActivity.runOnUiThread {
                        AlertDialog.Builder(v.context).setTitle(R.string.select_chat)
                            .setItems(chatNameList.toTypedArray<String>()) { _: DialogInterface?, i: Int ->
                                chatIdEditView.setText(
                                    chatIdList[i]
                                )
                                messageThreadIdEditView.setText(chatTopicIdList[i])
                            }.setPositiveButton("Cancel", null).show()
                    }
                }
            })
        }

        saveButton.setOnClickListener { v: View? ->
            Log.i("BootReceiver", Shell.getShell().isRoot.toString())
            if (botTokenEditView.text.toString().isEmpty() || chatIdEditView.text.toString()
                    .isEmpty()
            ) {
                showErrorDialog(applicationContext.getString(R.string.chat_id_or_token_not_config))
                return@setOnClickListener
            }
            if (fallbackSmsSwitch.isChecked && trustedPhoneNumberEditView.text.toString()
                    .isEmpty()
            ) {
                showErrorDialog(applicationContext.getString(R.string.trusted_phone_number_empty))
                return@setOnClickListener
            }
            if (!preferences.getBoolean("privacy_dialog_agree", false)) {
                showPrivacyDialog()
                return@setOnClickListener
            }
            var permissionList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val permissionArrayList =
                    java.util.ArrayList(listOf(*permissionList))
                permissionArrayList.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
                permissionList = permissionArrayList.toTypedArray<String>()
            }
            ActivityCompat.requestPermissions(
                this,
                permissionList,
                1
            )
            val powerManager = checkNotNull(getSystemService(POWER_SERVICE) as PowerManager)
            val hasIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
            if (!hasIgnored) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.setData("package:$packageName".toUri())
                if (intent.resolveActivityInfo(
                        packageManager,
                        PackageManager.MATCH_DEFAULT_ONLY
                    ) != null
                ) {
                    startActivity(intent)
                }
            }

            val progressDialog = ProgressDialog(this)
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.setTitle(getString(R.string.connect_wait_title))
            progressDialog.setMessage(getString(R.string.connect_wait_message))
            progressDialog.isIndeterminate = false
            progressDialog.setCancelable(false)
            progressDialog.show()

            val requestUri =
                getUrl(botTokenEditView.text.toString().trim { it <= ' ' }, "sendMessage")
            val requestBody =
                RequestMessage()
            requestBody.chatId = chatIdEditView.text.toString().trim { it <= ' ' }
            requestBody.messageThreadId =
                messageThreadIdEditView.text.toString().trim { it <= ' ' }
            requestBody.text =
                "${getString(R.string.system_message_head)}\n${getString(R.string.success_connect)}"
            val gson = Gson()
            val requestBodyRaw = gson.toJson(requestBody)
            val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
            val okhttpClient = getOkhttpObj()
            val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
            val call = okhttpClient.newCall(request)
            val errorHead = "Send message failed: "
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "onFailure: ", e)
                    progressDialog.cancel()
                    val errorMessage = errorHead + e.message
                    writeLog(applicationContext, errorMessage)
                    runOnUiThread { showErrorDialog(errorMessage) }
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    progressDialog.cancel()
                    val newBotToken = botTokenEditView.text.toString().trim { it <= ' ' }
                    if (response.code != 200) {
                        val result = Objects.requireNonNull(response.body).string()
                        val resultObj = JsonParser.parseString(result).asJsonObject
                        val errorMessage = errorHead + resultObj["description"]
                        writeLog(applicationContext, errorMessage)
                        runOnUiThread { showErrorDialog(errorMessage) }
                        return
                    }
                    if (newBotToken != botTokenSave) {
                        Log.i(
                            TAG,
                            "onResponse: The current bot token does not match the saved bot token, clearing the message database."
                        )
                        MMKV.mmkvWithID(Const.CHAT_INFO_MMKV_ID).clear()
                    }
                    MMKV.mmkvWithID(Const.UPGRADE_MMKV_ID).putInt(
                        "version",
                        Const.SYSTEM_CONFIG_VERSION
                    )
                    preferences.clear()
                    preferences.putString("bot_token", newBotToken)
                    preferences.putString(
                        "chat_id",
                        chatIdEditView.text.toString().trim { it <= ' ' })
                    preferences.putString(
                        "message_thread_id",
                        messageThreadIdEditView.text.toString().trim { it <= ' ' })
                    if (trustedPhoneNumberEditView.text.toString().trim { it <= ' ' }
                            .isNotEmpty()) {
                        preferences.putString(
                            "trusted_phone_number",
                            trustedPhoneNumberEditView.text.toString().trim { it <= ' ' })
                        preferences.putBoolean("fallback_sms", fallbackSmsSwitch.isChecked)
                    }
                    preferences.putBoolean("chat_command", chatCommandSwitch.isChecked)
                    preferences.putBoolean(
                        "battery_monitoring_switch",
                        batteryMonitoringSwitch.isChecked
                    )
                    preferences.putBoolean("charger_status", chargerStatusSwitch.isChecked)
                    preferences.putBoolean(
                        "display_dual_sim_display_name",
                        displayDualSimDisplayNameSwitch.isChecked
                    )
                    preferences.putBoolean("verification_code", verificationCodeSwitch.isChecked)
                    preferences.putBoolean("doh_switch", dohSwitch.isChecked)
                    preferences.putBoolean("initialized", true)
                    preferences.putBoolean("privacy_dialog_agree", true)
                    Thread {
                        KeepAliveJob.stopJob(applicationContext)
                        ReSendJob.stopJob(applicationContext)
                        stopAllService(applicationContext)
                        startService(
                            applicationContext,
                            batteryMonitoringSwitch.isChecked,
                            chatCommandSwitch.isChecked
                        )
                        startBeaconService(applicationContext)
                        KeepAliveJob.startJob(applicationContext)
                        ReSendJob.startJob(applicationContext)
                    }.start()
                    runOnUiThread {
                        Snackbar.make(v!!, R.string.success, Snackbar.LENGTH_LONG)
                            .show()
                    }
                }
            })
        }
    }


    private fun setPrivacyModeCheckbox(
        chatId: String,
        messageThreadIdView: TextInputLayout
    ) {
        if (parseStringToLong(chatId) < 0) {
            messageThreadIdView.visibility = View.VISIBLE
        } else {
            messageThreadIdView.visibility = View.GONE
        }
    }


    override fun onResume() {
        super.onResume()
        val backStatus = setPermissionBack
        setPermissionBack = false
        if (backStatus) {
            if (isNotifyListener(applicationContext)) {
                startActivity(Intent(this@MainActivity, NotifyActivity::class.java))
            }
        }
        if (Settings.System.canWrite(applicationContext)) {
            writeSettingsButton.visibility = View.GONE
        }
    }

    private fun showPrivacyDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.privacy_reminder_title)
        builder.setMessage(R.string.privacy_reminder_information)
        builder.setCancelable(false)
        builder.setPositiveButton(R.string.agree) { _: DialogInterface?, _: Int ->
            preferences.putBoolean("privacy_dialog_agree", true)
        }
        builder.setNegativeButton(R.string.decline, null)
        builder.setNeutralButton(R.string.visit_page) { _: DialogInterface?, _: Int ->
            val privacyPolice =
                "/guide/${applicationContext.getString(R.string.Lang)}/privacy-policy"
            val uri = "https://get.telegram-sms.com$privacyPolice".toUri()
            val privacyBuilder = CustomTabsIntent.Builder()
            val customTabsIntent = privacyBuilder.build()
            customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                customTabsIntent.launchUrl(applicationContext, uri)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "show_privacy_dialog: ", e)
                showErrorDialog("Browser not found.")
            }
        }
        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isAllCaps = false
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isAllCaps = false
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isAllCaps = false
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
                    Log.d(TAG, "No camera permissions.")
                    showErrorDialog(applicationContext.getString(R.string.no_camera_permission))
                    return
                }
                val intent = Intent(applicationContext, ScannerActivity::class.java)
                scannerLauncher.launch(intent)
            }

            1 -> {
                val displayDualSimDisplayName =
                    findViewById<SwitchMaterial>(R.id.display_dual_sim_switch)
                if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    val telephonyManager =
                        checkNotNull(getSystemService(TELEPHONY_SERVICE) as TelephonyManager)
                    val phoneCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        telephonyManager.activeModemCount
                    } else {
                        @Suppress("DEPRECATION")
                        telephonyManager.phoneCount
                    }
                    if (phoneCount <= 1 || getActiveCard(
                            applicationContext
                        ) < 2
                    ) {
                        displayDualSimDisplayName.isEnabled = false
                        displayDualSimDisplayName.isChecked = false
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode == Const.RESULT_CONFIG_JSON) {
                val gson = Gson()
                val scannerJson =
                    gson.fromJson(data?.getStringExtra("config_json"), ScannerJson::class.java)
                (findViewById<View>(R.id.bot_token_editview) as EditText).setText(scannerJson.botToken)
                (findViewById<View>(R.id.chat_id_editview) as EditText).setText(scannerJson.chatId)
                (findViewById<View>(R.id.battery_monitoring_switch) as SwitchMaterial).isChecked =
                    scannerJson.batteryMonitoringSwitch
                (findViewById<View>(R.id.verification_code_switch) as SwitchMaterial).isChecked =
                    scannerJson.verificationCode

                val chargerStatus = findViewById<SwitchMaterial>(R.id.charger_status_switch)
                if (scannerJson.chargerStatus) {
                    chargerStatus.isChecked = true
                    chargerStatus.visibility = View.VISIBLE
                } else {
                    chargerStatus.isChecked = false
                    chargerStatus.visibility = View.GONE
                }

                val chatCommand = findViewById<SwitchMaterial>(R.id.chat_command_switch)
                chatCommand.isChecked = scannerJson.chatCommand
                val messageThreadIdView = findViewById<TextInputLayout>(R.id.message_thread_id_view)
                setPrivacyModeCheckbox(
                    scannerJson.chatId,
                    messageThreadIdView
                )

                val trustedPhoneNumber =
                    findViewById<EditText>(R.id.trusted_phone_number_editview)
                trustedPhoneNumber.setText(scannerJson.trustedPhoneNumber)
                val fallbackSMS = findViewById<SwitchMaterial>(R.id.fallback_sms_switch)
                fallbackSMS.isChecked = scannerJson.fallbackSms
                if (trustedPhoneNumber.length() != 0) {
                    fallbackSMS.visibility = View.VISIBLE
                } else {
                    fallbackSMS.visibility = View.GONE
                    fallbackSMS.isChecked = false
                }
                val topicIDView = findViewById<EditText>(R.id.message_thread_id_editview)
                topicIDView.setText(scannerJson.topicID)
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    @SuppressLint("InflateParams", "NonConstantResourceId", "SetTextI18n")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val inflater = this.layoutInflater
        var fileName: String? = null
        when (item.itemId) {
            R.id.about_menu_item -> {
                val packageManager = applicationContext.packageManager
                val packageInfo: PackageInfo
                var versionName = ""
                try {
                    packageInfo = packageManager.getPackageInfo(applicationContext.packageName, 0)
                    versionName = packageInfo.versionName.toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.e(TAG, "onOptionsItemSelected: ", e)
                }
                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.about_title)
                builder.setMessage(getString(R.string.about_content) + versionName)
                builder.setCancelable(false)
                builder.setPositiveButton(R.string.ok_button, null)
                builder.show()
                return true
            }

            R.id.scan_menu_item -> {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.CAMERA),
                    0
                )
                return true
            }

            R.id.logcat_menu_item -> {
                startActivity(Intent(this, LogcatActivity::class.java))
                return true
            }

            R.id.config_qrcode_menu_item -> {
                if (preferences.contains("initialized")) {
                    startActivity(Intent(this, QRCodeActivity::class.java))
                } else {
                    showErrorDialog("Uninitialized.")
                }
                return true
            }

            R.id.set_beacon_menu_item -> {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "No permissions.")
                    showErrorDialog("No permission.")
                    return false
                }
                if (preferences.contains("initialized")) {
                    startActivity(Intent(this, BeaconActivity::class.java))
                } else {
                    showErrorDialog("Uninitialized.")
                }
                return true
            }

            R.id.set_copy_menu_item -> {
                if (preferences.contains("initialized")) {
                    startActivity(Intent(this, CcActivity::class.java))
                } else {
                    showErrorDialog("Uninitialized.")
                }
            }

            R.id.set_notify_menu_item -> {
                if (!isNotifyListener(applicationContext)) {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    setPermissionBack = true
                    return false
                }
                startActivity(Intent(this, NotifyActivity::class.java))
                return true
            }

            R.id.spam_sms_keyword_edittext -> {
                startActivity(Intent(this, SpamActivity::class.java))
                return true
            }

            R.id.set_proxy_menu_item -> {
                val proxyDialogView = inflater.inflate(R.layout.set_proxy_layout, null)
                val dohSwitch = findViewById<SwitchMaterial>(R.id.doh_switch)
                val proxyEnable =
                    proxyDialogView.findViewById<SwitchMaterial>(R.id.proxy_enable_switch)
                val proxyHost = proxyDialogView.findViewById<EditText>(R.id.proxy_host_editview)
                val proxyPort = proxyDialogView.findViewById<EditText>(R.id.proxy_port_editview)
                val proxyUsername =
                    proxyDialogView.findViewById<EditText>(R.id.proxy_username_editview)
                val proxyPassword =
                    proxyDialogView.findViewById<EditText>(R.id.proxy_password_editview)
                proxyEnable.isChecked = proxyMMKV.getBoolean("enabled", false)
                proxyHost.setText(proxyMMKV.getString("host", ""))
                proxyPort.setText(proxyMMKV.getInt("port", 1080).toString())
                proxyUsername.setText(proxyMMKV.getString("username", ""))
                proxyPassword.setText(proxyMMKV.getString("password", ""))
                AlertDialog.Builder(this).setTitle(R.string.proxy_dialog_title)
                    .setView(proxyDialogView)
                    .setPositiveButton(R.string.ok_button) { _: DialogInterface?, _: Int ->
                        if (!dohSwitch.isChecked) {
                            dohSwitch.isChecked = true
                        }
                        dohSwitch.isEnabled = !proxyEnable.isChecked
                        proxyMMKV.putBoolean("enabled", proxyEnable.isChecked)
                        proxyMMKV.putString("host", proxyHost.text.toString())
                        proxyMMKV.putInt("port", proxyPort.text.toString().toInt())
                        proxyMMKV.putString("username", proxyUsername.text.toString())
                        proxyMMKV.putString("password", proxyPassword.text.toString())
                        preferences.putBoolean("doh_switch", dohSwitch.isChecked)
                        Thread {
                            KeepAliveJob.stopJob(applicationContext)
                            stopAllService(applicationContext)
                            if (preferences.contains("initialized")) {
                                startService(
                                    applicationContext,
                                    preferences.getBoolean("battery_monitoring_switch", false),
                                    preferences.getBoolean("chat_command", false)
                                )
                                startBeaconService(applicationContext)
                                KeepAliveJob.startJob(applicationContext)
                            }
                        }.start()
                    }
                    .show()
                return true
            }

            R.id.user_manual_menu_item -> fileName =
                "/guide/" + applicationContext.getString(R.string.Lang) + "/user-manual"

            R.id.privacy_policy_menu_item -> fileName =
                "/guide/${applicationContext.getString(R.string.Lang)}/privacy-policy"

            R.id.question_and_answer_menu_item -> fileName =
                "/guide/" + applicationContext.getString(R.string.Lang) + "/Q&A"

            R.id.donate_menu_item -> fileName = "/donate"
        }
        // Use the already initialized privacyPolice variable
        checkNotNull(fileName)
        val uri = "https://get.telegram-sms.com$fileName".toUri()
        val builder = CustomTabsIntent.Builder()
        val params = CustomTabColorSchemeParams.Builder().setToolbarColor(
            ContextCompat.getColor(
                applicationContext, R.color.colorPrimary
            )
        ).build()
        builder.setDefaultColorSchemeParams(params)
        val customTabsIntent = builder.build()
        try {
            customTabsIntent.launchUrl(this, uri)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "onOptionsItemSelected: ", e)
            showErrorDialog("Browser not found.")
        }
        return true
    }

    fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    companion object {
        private var setPermissionBack = false
    }
}

