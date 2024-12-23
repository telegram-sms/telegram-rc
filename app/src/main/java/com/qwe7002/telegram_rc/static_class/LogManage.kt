package com.qwe7002.telegram_rc.static_class

import android.content.Context
import android.util.Log
import com.qwe7002.telegram_rc.R
import io.paperdb.Paper
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManage {
    @JvmStatic
    fun writeLog(context: Context, log: String) {
        Log.i("write_log", log)
        val newFileMode = Context.MODE_APPEND
        val simpleDateFormat = SimpleDateFormat(context.getString(R.string.time_format), Locale.UK)
        val writeString = """
${simpleDateFormat.format(Date(System.currentTimeMillis()))} $log"""
        var logCount = Paper.book("system_config").read("log_count", 0)!!
        if (logCount >= 50000) {
            resetLogFile(context)
        }
        Paper.book("system_config").write("log_count", ++logCount)

        writeLogFile(context, writeString, newFileMode)
    }

    @JvmStatic
    fun readLog(context: Context, line: Int): String {
        val result = context.getString(R.string.no_logs)
        val TAG = "read_log"
        val builder = StringBuilder()
        var fileInputStream: FileInputStream? = null
        var channel: FileChannel? = null
        try {
            fileInputStream = context.openFileInput("error.log")
            channel = fileInputStream.channel
            val buffer: ByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            buffer.position(channel.size().toInt())
            var count = 0
            for (i in channel.size() - 1 downTo 0) {
                val c = Char(buffer[i.toInt()].toUShort())
                builder.insert(0, c)
                if (c == '\n') {
                    if (count == (line - 1)) {
                        break
                    }
                    ++count
                }
            }
            return builder.toString().ifEmpty {
                result
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.d(TAG, "Unable to read the file.")
            return result
        } finally {
            try {
                fileInputStream?.close()
                channel?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun resetLogFile(context: Context) {
        Paper.book("system_config").delete("log_count")
        writeLogFile(context, "", Context.MODE_PRIVATE)
    }

    private fun writeLogFile(context: Context, writeString: String, mode: Int) {
        var fileStream: FileOutputStream? = null
        try {
            fileStream = context.openFileOutput("error.log", mode)
            val bytes = writeString.toByteArray()
            fileStream.write(bytes)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (fileStream != null) {
                try {
                    fileStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }
}
