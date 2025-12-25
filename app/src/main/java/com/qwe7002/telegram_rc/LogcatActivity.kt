package com.qwe7002.telegram_rc

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.qwe7002.telegram_rc.data_structure.LogAdapter
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

class LogcatActivity : AppCompatActivity() {
    private lateinit var logRecyclerView: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private val line = 500
    private lateinit var refreshThread: Thread
    private var isRefreshing = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_logcat)
        setTitle(R.string.logcat)
        
        // Handle window insets for edge-to-edge
        
        logRecyclerView = findViewById(R.id.log_recycler_view)
        logRecyclerView.layoutManager = LinearLayoutManager(this)
        ViewCompat.setOnApplyWindowInsetsListener(logRecyclerView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(logRecyclerView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<ImageView>(R.id.log_character_set)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        FakeStatusBar().fakeStatusBar(this, window)

        logAdapter = LogAdapter(emptyList())
        logRecyclerView.adapter = logAdapter
        
        loadLogs()
        
        startRefreshThread()
    }

    private fun startRefreshThread() {
        isRefreshing = true
        refreshThread = thread {
            while (isRefreshing) {
                runOnUiThread {
                    loadLogs()
                }
                Thread.sleep(1000)
            }
        }
    }

    private fun loadLogs() {
        thread {
            try {
                val pid = android.os.Process.myPid()
                val process = Runtime.getRuntime().exec(arrayOf("logcat","--pid=$pid", "-d", "-t", line.toString()))
                val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                val logList = mutableListOf<String>()
                var logLine: String?
                while (bufferedReader.readLine().also { logLine = it } != null) {
                    logLine?.let {
                        if (it.isNotBlank() && !it.startsWith("---------")) {
                            logList.add(it)
                        }
                    }
                }
                bufferedReader.close()
                process.waitFor()

                runOnUiThread {
                    logAdapter.updateLogs(logList.reversed())
                    // Only scroll to top if user is at the top or this is the initial load
                    val layoutManager = logRecyclerView.layoutManager as LinearLayoutManager
                    if (layoutManager.findFirstVisibleItemPosition() == 0) {
                        logRecyclerView.scrollToPosition(0)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    public override fun onPause() {
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        loadLogs()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRefreshing = false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.logcat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_logs -> {
                thread {
                    try {
                        Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()
                        runOnUiThread {
                            loadLogs()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
