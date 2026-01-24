package com.qwe7002.telegram_rc

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.qwe7002.telegram_rc.data_structure.LogAdapter
import com.qwe7002.telegram_rc.data_structure.LogEntry
import com.qwe7002.telegram_rc.shizuku_kit.ShizukuKit
import com.qwe7002.telegram_rc.value.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList

class LogcatActivity : AppCompatActivity() {
    private lateinit var logRecyclerView: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private var logcatProcess: Process? = null
    private var logcatJob: Job? = null
    private val logBuffer = CopyOnWriteArrayList<LogEntry>()
    private val logChannel = Channel<LogEntry>(Channel.UNLIMITED)
    private var entryId = 0L

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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<ImageView>(R.id.log_character_set)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        FakeStatusBar().fakeStatusBar(this, window)
        logAdapter = LogAdapter()
        logAdapter.setHasStableIds(true)
        logRecyclerView.adapter = logAdapter

        startLogConsumer()
    }

    private fun startLogConsumer() {
        lifecycleScope.launch(Dispatchers.Main) {
            for (entry in logChannel) {
                logBuffer.add(entry)
                updateAdapter()
            }
        }
    }

    private fun updateAdapter() {
        // Create a new immutable list to avoid concurrent modification
        val newList = ArrayList(logBuffer)
        logAdapter.submitList(newList) {
            // Scroll after the list has been updated
            if (newList.isNotEmpty()) {
                logRecyclerView.post {
                    logRecyclerView.scrollToPosition(newList.size - 1)
                }
            }
        }
    }

    private fun startLogcat() {
        logcatJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                var level = "I"
                if (BuildConfig.DEBUG) {
                    level = "V" // Verbose in debug builds
                    if (BuildConfig.VERSION_NAME.contains("nightly", ignoreCase = true)) {
                        level = "D"
                        Log.d(TAG, "onCreate: Setting log level to D for debug/nightly build")
                    }
                }
                logcatProcess = Runtime.getRuntime().exec(
                    arrayOf(
                        "logcat", "${TAG}:${level}", "Telegram-RC.TetherManager:${level}",
                        "${ShizukuKit.TAG}:${level}",
                        "*:S", "-d", "-t", "500", "-v", "time"
                    )
                )

                val reader = BufferedReader(InputStreamReader(logcatProcess?.inputStream))
                var lastEntry: LogEntry? = null
                var lastTimestamp: String? = null
                var lastTag: String? = null

                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isNotEmpty() && !line.startsWith("------") && !line.startsWith("---------")) {
                        val parsed = parseLogLine(entryId, line)

                        // Check if this is a continuation line (same timestamp, tag, and message is part of a stack trace)
                        val msg = parsed.message
                        val isContinuation = lastEntry != null &&
                            lastTimestamp == parsed.timestamp &&
                            lastTag == parsed.tag &&
                            (msg.startsWith("\t") ||
                             msg.startsWith("    ") ||
                             msg.matches(Regex("^\\s*at\\s+.*")) ||
                             msg.matches(Regex("^\\s*Caused by:.*")) ||
                             msg.matches(Regex("^\\s*Suppressed:.*")) ||
                             msg.matches(Regex("^\\s*\\.{3}\\s+\\d+\\s+more\\s*$")) ||
                             msg.matches(Regex("^[a-zA-Z]+(\\.[a-zA-Z0-9_\$]+)*Exception.*")) ||
                             msg.matches(Regex("^[a-zA-Z]+(\\.[a-zA-Z0-9_\$]+)*Error.*")) ||
                             msg.matches(Regex("^[a-zA-Z]+(\\.[a-zA-Z0-9_\$]+)*:\\s+.*")) ||
                             (msg.trim().isEmpty() && msg.isNotEmpty()))

                        if (isContinuation) {
                            // Add to the last entry's continuation lines
                            lastEntry.continuationLines.add(parsed.message)
                        } else {
                            // Send the previous entry if exists
                            if (lastEntry != null) {
                                logChannel.trySend(lastEntry)
                            }
                            // Start a new entry
                            entryId++
                            lastEntry = parsed
                            lastTimestamp = parsed.timestamp
                            lastTag = parsed.tag
                        }
                    }
                }

                // Send the last entry if exists
                if (lastEntry != null) {
                    logChannel.trySend(lastEntry)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading logcat", e)
            }
        }
    }

    private fun parseLogLine(id: Long, line: String): LogEntry {
        // Logcat format with -v time: "MM-DD HH:MM:SS.mmm D/Tag(PID): Message"
        val regex =
            Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^(]+)\(\s*\d+\):\s*(.*)$""")
        val match = regex.find(line)

        return if (match != null) {
            val (timestamp, level, tag, message) = match.destructured
            LogEntry(
                id = id,
                timestamp = timestamp,
                level = level.first(),
                tag = tag.trim(),
                message = message,
                rawLine = line,
                continuationLines = mutableListOf()
            )
        } else {
            // Fallback for unparseable lines
            LogEntry(
                id = id,
                timestamp = "",
                level = 'V',
                tag = "",
                message = line,
                rawLine = line,
                continuationLines = mutableListOf()
            )
        }
    }

    private fun stopLogcat() {
        logcatJob?.cancel()
        logcatProcess?.destroy()
        logcatProcess = null
    }



    public override fun onPause() {
        super.onPause()
        stopLogcat()
    }

    public override fun onResume() {
        super.onResume()
        startLogcat()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLogcat()
        logChannel.close()
    }

}
