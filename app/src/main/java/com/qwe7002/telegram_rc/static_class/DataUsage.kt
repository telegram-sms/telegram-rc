package com.qwe7002.telegram_rc.static_class

import android.Manifest
import android.annotation.SuppressLint
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat

object DataUsage {
    private const val TAG = "DataUsage"

    /**
     * 获取指定SIM卡的数据使用量
     *
     * @param context 应用上下文
     * @param subscriberId SIM卡的订阅者ID
     * @return 格式化的数据使用量字符串 (例如: "1.5 GB")
     */
    @SuppressLint("MissingPermission")
    fun getDataUsageForSim(context: Context, subscriberId: String?): String {
        return try {
            // 检查是否有READ_PHONE_STATE权限
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return "Permission denied"
            }

            // 获取NetworkStatsManager
            val networkStatsManager =
                context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

            // 查询从月初到现在的数据使用情况
            val startTime = getStartOfMonthTimestamp()
            val endTime = System.currentTimeMillis()

            // 使用null作为subscriberId来避免SecurityException，这会返回所有移动网络的数据
            val bucket = networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                subscriberId, // 使用null避免SecurityException
                startTime,
                endTime
            )

            val totalBytes = bucket.rxBytes + bucket.txBytes
            formatBytes(totalBytes)
        } catch (e: SecurityException) {
            // 如果出现安全异常，提示用户授予权限
            Log.e(TAG, "SecurityException when getting data", e)
            "Permission required - open settings"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting data usage", e)
            "Error: ${e.message}"
        }
    }

    /**
     * 打开应用使用统计设置页面，让用户授予权限
     */
    fun openUsageStatsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * 获取当前月份的开始时间戳
     */
    private fun getStartOfMonthTimestamp(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * 将字节数格式化为可读的字符串
     */
    @SuppressLint("DefaultLocale")
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
