package com.qwe7002.telegram_rc.static_class

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import com.qwe7002.telegram_rc.MMKV.Const
import com.qwe7002.telegram_rc.MMKV.DataPlanManager
import java.util.Calendar


@Suppress("DEPRECATION")
object DataUsage {
    
    init {
        DataPlanManager.initialize()
    }

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

            val networkStatsManager =
                context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

            val startTime = getDataPlanStartTime()
            val endTime = System.currentTimeMillis()

            val bucket = networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                subscriberId,
                startTime,
                endTime
            )

            val totalBytes = bucket.rxBytes + bucket.txBytes
            formatBytes(totalBytes)
        } catch (e: SecurityException) {
            Log.e(Const.TAG, "SecurityException when getting data", e)
            "Permission required - open settings"
        } catch (e: Exception) {
            Log.e(Const.TAG, "Error getting data usage", e)
            "Error: ${e.message}"
        }
    }

    fun hasPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode: Int = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: ClassCastException) {
            // 处理Shizuku可能返回BinderProxy的情况
            Log.e(Const.TAG, "ClassCastException when checking permission", e)
            false
        } catch (e: Exception) {
            Log.e(Const.TAG, "Error when checking permission", e)
            false
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
     * 根据数据计划类型获取计费周期开始时间戳
     */
    private fun getDataPlanStartTime(): Long {
        val calendar = Calendar.getInstance()
        
        when (DataPlanManager.getDataPlanType()) {
            DataPlanManager.DATA_PLAN_TYPE_DAILY -> {
                // 日租卡：从当天0点开始
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            DataPlanManager.DATA_PLAN_TYPE_MONTHLY -> {
                // 月租卡：从计费周期开始日开始
                val billingCycleStart = DataPlanManager.getBillingCycleStart()
                val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
                
                if (currentDay >= billingCycleStart) {
                    // 如果当前日期大于等于计费周期开始日，则从本月该日开始
                    calendar.set(Calendar.DAY_OF_MONTH, billingCycleStart)
                } else {
                    // 如果当前日期小于计费周期开始日，则从上个月该日开始
                    calendar.add(Calendar.MONTH, -1)
                    calendar.set(Calendar.DAY_OF_MONTH, billingCycleStart)
                }
                
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            else -> {
                // 默认：从月初开始
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
        }
        
        return calendar.timeInMillis
    }

    /**
     * 将字节数格式化为可读的字符串
     */
    @SuppressLint("DefaultLocale")
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(
                "%.2f GB",
                bytes / (1024.0 * 1024.0 * 1024.0)
            )

            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
