package com.qwe7002.telegram_rc
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.database.YellowPage
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class YellowPageSyncActivity : AppCompatActivity() {
    private lateinit var countText: TextView
    private lateinit var lastSyncText: TextView
    private lateinit var urlInput: TextInputEditText
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var syncButton: Button
    private lateinit var clearButton: Button
    private val mmkv by lazy { MMKV.defaultMMKV() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_yellowpage_sync)
        FakeStatusBar().fakeStatusBar(this, window)
        // Initialize views
        countText = findViewById(R.id.yellowpage_count)
        lastSyncText = findViewById(R.id.yellowpage_last_sync_time)
        urlInput = findViewById(R.id.yellowpage_url_input)
        progressBar = findViewById(R.id.yellowpage_progress)
        statusText = findViewById(R.id.yellowpage_status)
        syncButton = findViewById(R.id.yellowpage_sync_button)
        clearButton = findViewById(R.id.yellowpage_clear_button)
        // Handle window insets for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        // Load saved URL
        val savedUrl = mmkv.getString("yellowpage_url", getString(R.string.yellowpage_default_url))
        urlInput.setText(savedUrl)
        // Update UI with current data
        updateDatabaseInfo()
        syncButton.setOnClickListener {
            startSync()
        }
        clearButton.setOnClickListener {
            showClearConfirmDialog()
        }
    }
    private fun updateDatabaseInfo() {
        CoroutineScope(Dispatchers.IO).launch {
            val count = YellowPage.getPhoneNumberCount(this@YellowPageSyncActivity)
            val lastSync = mmkv.getLong("yellowpage_last_sync", 0)
            withContext(Dispatchers.Main) {
                countText.text = count.toString()
                if (lastSync > 0) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    lastSyncText.text = dateFormat.format(Date(lastSync))
                } else {
                    lastSyncText.text = getString(R.string.yellowpage_never_synced)
                }
            }
        }
    }
    private fun startSync() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) {
            urlInput.error = "URL cannot be empty"
            return
        }
        // Save URL
        mmkv.encode("yellowpage_url", url)
        // Show progress
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        statusText.visibility = View.VISIBLE
        statusText.text = getString(R.string.yellowpage_downloading)
        syncButton.isEnabled = false
        clearButton.isEnabled = false
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Download
                withContext(Dispatchers.Main) {
                    statusText.text = getString(R.string.yellowpage_downloading)
                }
                val result = YellowPage.syncFromUrl(this@YellowPageSyncActivity, url) { status ->
                    CoroutineScope(Dispatchers.Main).launch {
                        statusText.text = status
                    }
                }
                // Save sync time
                mmkv.encode("yellowpage_last_sync", System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.text = getString(R.string.yellowpage_sync_success, result)
                    updateDatabaseInfo()
                    syncButton.isEnabled = true
                    clearButton.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(Const.TAG, "YellowPage sync failed", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.text = getString(R.string.yellowpage_sync_failed, e.message)
                    syncButton.isEnabled = true
                    clearButton.isEnabled = true
                }
            }
        }
    }
    private fun showClearConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.yellowpage_clear_button)
            .setMessage("Are you sure you want to clear all YellowPage data?")
            .setPositiveButton(R.string.ok_button) { _, _ ->
                clearDatabase()
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }
    private fun clearDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            YellowPage.clearDatabase(this@YellowPageSyncActivity)
            mmkv.remove("yellowpage_last_sync")
            withContext(Dispatchers.Main) {
                statusText.visibility = View.VISIBLE
                statusText.text = getString(R.string.yellowpage_cleared)
                updateDatabaseInfo()
            }
        }
    }
}
