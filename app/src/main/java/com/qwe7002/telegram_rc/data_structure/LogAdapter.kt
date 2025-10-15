package com.qwe7002.telegram_rc.data_structure

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.qwe7002.telegram_rc.R
import java.text.SimpleDateFormat
import java.util.Locale

class LogAdapter(private var logEntries: List<String>) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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

        // Parse the log entry to separate timestamp and message
        // Try to find the timestamp at the beginning of the entry
        val timestampEndIndex = findTimestampEnd(entry)
        if (timestampEndIndex > 0 && isValidTimestamp(entry.substring(0, timestampEndIndex))) {
            holder.timestampText.text = entry.substring(0, timestampEndIndex)
            holder.messageText.text = entry.substring(timestampEndIndex).trimStart()
        } else {
            holder.timestampText.text = ""
            holder.messageText.text = entry
        }
    }

    override fun getItemCount(): Int = logEntries.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateLogs(newLogs: List<String>) {
        logEntries = newLogs
        notifyDataSetChanged()
    }

    private fun findTimestampEnd(entry: String): Int {
        // Look for a valid timestamp at the beginning of the entry
        // The format is "dd/MMM/yyyy HH:mm:ss z" - try to match this pattern
        val parts = entry.split(" ")
        if (parts.size < 4) return -1 // Not enough parts for a timestamp + message
        
        // Try different combinations to find a valid timestamp
        // Try combining first 3 parts, then 4 parts, etc.
        for (i in 3..parts.size) {
            val candidate = parts.take(i).joinToString(" ")
            if (isValidTimestamp(candidate)) {
                return candidate.length
            }
        }
        return -1
    }

    private fun isValidTimestamp(timestamp: String): Boolean {
        return try {
            val format = SimpleDateFormat("dd/MMM/yyyy HH:mm:ss z", Locale.UK)
            format.isLenient = false
            format.parse(timestamp.replace(Regex("[zZ]$"), ""))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
