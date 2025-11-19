package com.qwe7002.telegram_rc

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.qwe7002.telegram_rc.data_structure.LogAdapter
import com.qwe7002.telegram_rc.static_class.LogManage
import kotlin.concurrent.thread

class LogcatActivity : AppCompatActivity() {
    private lateinit var logRecyclerView: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private val line = 100
    private lateinit var refreshThread: Thread
    private var isRefreshing = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_logcat)
        setTitle(R.string.logcat)
        
        // Handle window insets for edge-to-edge
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        FakeStatusBar().fakeStatusBar(this, window)
        
        logRecyclerView = findViewById(R.id.log_recycler_view)
        logRecyclerView.layoutManager = LinearLayoutManager(this)
        
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
        val logs = LogManage.readLog(this, line)
        val logList = logs.split("\n").filter { it.isNotBlank() }.reversed()
        logAdapter.updateLogs(logList)
        // Only scroll to top if user is at the top or this is the initial load
        val layoutManager = logRecyclerView.layoutManager as LinearLayoutManager
        if (layoutManager.findFirstVisibleItemPosition() == 0) {
            logRecyclerView.scrollToPosition(0)
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
                LogManage.resetLogFile()
                loadLogs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
