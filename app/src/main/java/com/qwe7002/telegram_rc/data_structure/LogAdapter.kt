package com.qwe7002.telegram_rc.data_structure

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.qwe7002.telegram_rc.R

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: Char,
    val tag: String,
    val message: String,
    val rawLine: String,
    val continuationLines: MutableList<String> = mutableListOf(),
    var isExpanded: Boolean = false
) {
    fun hasContinuation(): Boolean = continuationLines.isNotEmpty()
}

class LogAdapter : ListAdapter<LogEntry, LogAdapter.LogViewHolder>(LogDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).id
    }

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val levelView: TextView = itemView.findViewById(R.id.log_level)
        val tagView: TextView = itemView.findViewById(R.id.log_tag)
        val timestampView: TextView = itemView.findViewById(R.id.log_timestamp)
        val messageView: TextView = itemView.findViewById(R.id.log_message)
        val expandIndicator: TextView = itemView.findViewById(R.id.log_expand_indicator)
        val detailsView: TextView = itemView.findViewById(R.id.log_details)
    }

    class LogDiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val entry = getItem(position)

        // Set level emoji
        holder.levelView.text = getLevelString(entry.level)
        holder.levelView.setTextColor(getLevelColor(entry.level))

        // Set tag
        holder.tagView.text = entry.tag.ifEmpty { "Unknown" }

        // Set timestamp
        holder.timestampView.text = entry.timestamp

        // Set message
        holder.messageView.text = entry.message

        // Handle expand/collapse for continuation lines
        if (entry.hasContinuation()) {
            holder.expandIndicator.visibility = View.VISIBLE
            holder.expandIndicator.text = if (entry.isExpanded) "▼ ${entry.continuationLines.size}" else "▶ ${entry.continuationLines.size}"

            if (entry.isExpanded) {
                holder.detailsView.visibility = View.VISIBLE
                holder.detailsView.text = entry.continuationLines.joinToString("\n")
            } else {
                holder.detailsView.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    val currentEntry = getItem(currentPosition)
                    currentEntry.isExpanded = !currentEntry.isExpanded
                    notifyItemChanged(currentPosition)
                }
            }
        } else {
            holder.expandIndicator.visibility = View.GONE
            holder.detailsView.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
        }
    }

    private fun getLevelString(level: Char): String {
        return when (level) {
            'E' -> "❌ Error"  // Error
            'W' -> "⚠️ Warning"  // Warning
            'I' -> "ℹ️ Info"  // Info
            'D' -> "🐛 Debug"  // Debug
            'V' -> "📝 Verbose"  // Verbose
            'F' -> "💀 Fatal"  // Fatal
            else -> "❓"
        }
    }

    private fun getLevelColor(level: Char): Int {
        return when (level) {
            'E' -> Color.RED
            'W' -> Color.rgb(255, 165, 0)  // Orange
            'I' -> Color.rgb(100, 149, 237) // Cornflower Blue
            'D' -> Color.rgb(0, 200, 0)     // Green
            'V' -> Color.GRAY
            'F' -> Color.RED
            else -> Color.WHITE
        }
    }
}
