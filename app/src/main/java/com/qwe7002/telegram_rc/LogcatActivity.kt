package com.qwe7002.telegram_rc

import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.qwe7002.telegram_rc.static_class.LogManage
import kotlin.concurrent.thread

@Suppress("DEPRECATION")
class LogcatActivity : AppCompatActivity() {
    private lateinit var logcatTextview: TextView
    private val line = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logcat)
        logcatTextview = findViewById(R.id.logcat_textview)
        this.setTitle(R.string.logcat)
        logcatTextview.text = LogManage.readLog(applicationContext, line)
        logcatTextview.gravity = Gravity.BOTTOM
        thread {
            while (true) {
                runOnUiThread {
                    logcatTextview.text = LogManage.readLog(applicationContext, line)
                }
                Thread.sleep(500)
            }
        }
    }

    public override fun onPause() {
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        logcatTextview.text = LogManage.readLog(applicationContext, line)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.logcat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        LogManage.resetLogFile()
        return true
    }

}


