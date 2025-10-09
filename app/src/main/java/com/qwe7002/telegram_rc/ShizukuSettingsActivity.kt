package com.qwe7002.telegram_rc

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.EditTextPreference
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.static_class.Network
import com.tencent.mmkv.MMKV
import okhttp3.*
import java.io.IOException

class ShizukuSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.shizuku_preferences, rootKey)
            setupPreferences()
        }

        private fun setupPreferences() {
            val device = findPreference<EditTextPreference>("device")
            device?.summary = android.os.Build.MODEL
            val manufacturer = findPreference<EditTextPreference>("manufacturer")
            manufacturer?.summary = android.os.Build.MANUFACTURER
            val shizukuMMKV = MMKV.mmkvWithID(Const.SHIZUKU_MMKV_ID)
            val imsiMMKV = MMKV.mmkvWithID(Const.IMSI_MMKV_ID)

            // Setup IMSI preference
            val imsiPreference = findPreference<EditTextPreference>("imsi_info")
            imsiPreference?.summary = "Loading..."

            // Setup Shizuku preferences
            val getSubscriberIdPreference = findPreference<EditTextPreference>("getSubscriberId")
            val getSubscriberIdForSubscriberPreference =
                findPreference<EditTextPreference>("getSubscriberIdForSubscriber")
            val setDefaultDataSubIdPreference =
                findPreference<EditTextPreference>("setDefaultDataSubId")
            val setSimPowerStatePreference = findPreference<EditTextPreference>("setSimPowerState")

            // Setup GitHub fallback preference
            val fetchGithubFallbackPreference = findPreference<Preference>("fetch_github_fallback")
            fetchGithubFallbackPreference?.setOnPreferenceClickListener {
                fetchFallbackParametersFromGithub()
                true
            }

            // Set input type and filters for Shizuku preferences to only allow digits
            val digitsFilter = InputFilter { source, start, end, dest, dstart, dend ->
                for (i in start until end) {
                    if (!Character.isDigit(source[i])) {
                        return@InputFilter ""
                    }
                }
                null
            }

            getSubscriberIdPreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                editText.filters = arrayOf(digitsFilter)
            }

            getSubscriberIdForSubscriberPreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                editText.filters = arrayOf(digitsFilter)
            }

            setDefaultDataSubIdPreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                editText.filters = arrayOf(digitsFilter)
            }

            setSimPowerStatePreference?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
                editText.filters = arrayOf(digitsFilter)
            }

            // Set current values
            getSubscriberIdPreference?.text = shizukuMMKV.getString("getSubscriberId", "8")
            getSubscriberIdForSubscriberPreference?.text =
                shizukuMMKV.getString("getSubscriberIdForSubscriber", "10")
            setDefaultDataSubIdPreference?.text = shizukuMMKV.getString("setDefaultDataSubId", "31")
            setSimPowerStatePreference?.text = shizukuMMKV.getString("setSimPowerState", "185")

            // Set preference listeners
            getSubscriberIdPreference?.setOnPreferenceChangeListener { _, newValue ->
                shizukuMMKV.putString("getSubscriberId", newValue as String)
                true
            }

            getSubscriberIdForSubscriberPreference?.setOnPreferenceChangeListener { _, newValue ->
                shizukuMMKV.putString("getSubscriberIdForSubscriber", newValue as String)
                true
            }

            setDefaultDataSubIdPreference?.setOnPreferenceChangeListener { _, newValue ->
                shizukuMMKV.putString("setDefaultDataSubId", newValue as String)
                true
            }

            setSimPowerStatePreference?.setOnPreferenceChangeListener { _, newValue ->
                shizukuMMKV.putString("setSimPowerState", newValue as String)
                true
            }


            // Load IMSI information
            loadImsiInfo(imsiPreference, imsiMMKV)
        }

        private fun loadImsiInfo(imsiPreference: EditTextPreference?, imsiMMKV: MMKV) {
            Thread {
                try {
                    val allKeys = imsiMMKV.allKeys()
                    val imsiInfo = StringBuilder()

                    allKeys?.let {
                        if (it.isNotEmpty()) {
                            for (key in allKeys) {
                                val imsi = imsiMMKV.getString(key, "")
                                imsiInfo.append("$key: $imsi\n")
                            }
                        } else {
                            imsiInfo.append("No IMSI information available")
                        }
                    }

                    activity?.runOnUiThread {
                        imsiPreference?.summary = imsiInfo.toString().trim()
                        imsiPreference?.text = ""
                    }
                } catch (e: Exception) {
                    activity?.runOnUiThread {
                        imsiPreference?.summary = "Error loading IMSI: ${e.message}"
                        imsiPreference?.text = ""
                    }
                }
            }.start()
        }

        private fun fetchFallbackParametersFromGithub() {
            val githubUrl =
                "https://raw.githubusercontent.com/telegram-sms/shizuku-fallback-code/main/${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}.json"
            val client = Network.getOkhttpObj()
            val request = Request.Builder()
                .url(githubUrl)
                .build()

            activity?.runOnUiThread {
                Toast.makeText(activity, "Fetching fallback parameters...", Toast.LENGTH_SHORT)
                    .show()
            }

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    activity?.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Failed to fetch parameters: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val fallbackParameters = JsonParser.parseString(responseBody)
                        val shizukuMMKV = MMKV.mmkvWithID(Const.SHIZUKU_MMKV_ID)
                        shizukuMMKV.putString(
                            "getSubscriberId",
                            fallbackParameters.getAsJsonObject().get("getSubscriberId").asString
                        )
                        shizukuMMKV.putString(
                            "getSubscriberIdForSubscriber",
                            fallbackParameters.getAsJsonObject()
                                .get("getSubscriberIdForSubscriber").asString
                        )
                        shizukuMMKV.putString(
                            "setDefaultDataSubId",
                            fallbackParameters.getAsJsonObject().get("setDefaultDataSubId").asString
                        )
                        shizukuMMKV.putString(
                            "setSimPowerState",
                            fallbackParameters.getAsJsonObject().get("setSimPowerState").asString
                        )
                        activity?.runOnUiThread {
                            Toast.makeText(
                                activity,
                                "Parameters fetched successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            setupPreferences()
                        }

                    } else {
                        activity?.runOnUiThread {
                            Toast.makeText(
                                activity,
                                "Server error: ${response.code}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            })
        }
    }
}
