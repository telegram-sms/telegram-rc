package com.qwe7002.telegram_rc

import android.content.Context
import android.os.Bundle
import android.os.FileObserver
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.qwe7002.telegram_rc.static_class.log

@Suppress("DEPRECATION")
class LogcatActivity : AppCompatActivity() {
    private lateinit var observer: fileObserver
    private lateinit var logcatTextview: TextView
    private val line = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logcat)
        logcatTextview = findViewById(R.id.logcat_textview)
        this.setTitle(R.string.logcat)
        logcatTextview.setText(log.readLog(applicationContext, line))
        observer = fileObserver(applicationContext, logcatTextview)
        logcatTextview.setGravity(Gravity.BOTTOM)
    }

    public override fun onPause() {
        super.onPause()
        observer.stopWatching()
    }

    public override fun onResume() {
        super.onResume()
        logcatTextview.text = log.readLog(applicationContext, line)
        observer.startWatching()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.logcat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        log.resetLogFile(applicationContext)
        return true
    }

    internal inner class fileObserver(private val context: Context, private val logcat: TextView?) :
        FileObserver(
            context.filesDir.absolutePath
        ) {
        override fun onEvent(event: Int, path: String?) {
            if (event == MODIFY && path!!.contains("error.log")) {
                runOnUiThread { logcat!!.text = log.readLog(context, line) }
            }
        }
    }
}


