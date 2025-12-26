package com.qwe7002.telegram_rc.data_structure

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.qwe7002.telegram_rc.R

class LogAdapter(private var logEntries: List<String>) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tagText: TextView = itemView.findViewById(R.id.log_tag)
        val timestampText: TextView = itemView.findViewById(R.id.log_timestamp)
        val messageText: TextView = itemView.findViewById(R.id.log_message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(view)
    }


    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val entry = logEntries[position]

        // Parse logcat format: "MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: message"
        val parsed = parseLogcatEntry(entry)
        holder.tagText.text = parsed.first
        holder.timestampText.text = parsed.second
        holder.messageText.text = parsed.third
    }

    override fun getItemCount(): Int = logEntries.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateLogs(newLogs: List<String>) {
        logEntries = newLogs
        notifyDataSetChanged()
    }

    private fun parseLogcatEntry(entry: String): Triple<String, String, String> {
        // Logcat format: "MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: message"
        // Example: "12-08 10:30:45.123  1234  5678 I MyTag: This is a message"
        val regex = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]+):\s*(.*)$""")
        val match = regex.find(entry)

        return if (match != null) {
            val timestamp = match.groupValues[1]
            val level = match.groupValues[2]
            val message = match.groupValues[4]
            val levelEmoji = levelToEmoji(level)
            Triple(levelEmoji, timestamp, message)
        } else {
            // Fallback if regex doesn't match
            Triple("", "", entry)
        }
    }

    private fun levelToEmoji(level: String): String {
        return when (level) {
            "V" -> "📝 Verbose"  // Verbose
            "D" -> "🐛 Debug"  // Debug
            "I" -> "ℹ️ Info"  // Info
            "W" -> "⚠️ Warning"  // Warning
            "E" -> "❌ Error"  // Error
            "F" -> "💀 Fatal"  // Fatal
            else -> "❓"
        }
    }
}
