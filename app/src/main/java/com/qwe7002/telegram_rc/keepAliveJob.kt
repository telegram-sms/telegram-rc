package com.qwe7002.telegram_rc

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.qwe7002.telegram_rc.static_class.service


class keepAliveJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        if (sharedPreferences.getBoolean("initialized", false)) {
            service.startService(
                applicationContext,
                sharedPreferences.getBoolean("battery_monitoring_switch", false),
                sharedPreferences.getBoolean("chat_command", false)
            )
            service.startBeaconService(applicationContext)
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        startJob(this)
        return false
    }

    companion object {
        fun startJob(context: Context) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            val jobInfoBuilder = JobInfo.Builder(
                10,
                ComponentName(context.packageName, keepAliveJob::class.java.getName())
            )
                .setPersisted(true)
            jobInfoBuilder.setMinimumLatency(5000)

            jobScheduler.schedule(jobInfoBuilder.build())
        }
    }
}
