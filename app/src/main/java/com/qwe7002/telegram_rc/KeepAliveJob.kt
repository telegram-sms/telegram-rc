package com.qwe7002.telegram_rc

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.qwe7002.telegram_rc.static_class.ServiceManage
import io.paperdb.Paper
import java.util.concurrent.TimeUnit


class KeepAliveJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Paper.init(applicationContext)
        val preferences = Paper.book("preferences")
        if (preferences.contains("initialized")) {
            ServiceManage.startService(
                applicationContext,
                preferences.read("battery_monitoring_switch", false)!!,
                preferences.read("chat_command", false)!!
            )
            ServiceManage.startBeaconService(applicationContext)
        }
        Log.d("telegram-rc", "startJob: Try to pull up the service")
        this.jobFinished(params, false)
        startJob(applicationContext)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }

    companion object {
        fun startJob(context: Context) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            val jobInfoBuilder = JobInfo.Builder(
                10,
                ComponentName(context.packageName, KeepAliveJob::class.java.getName())
            )
                .setPersisted(true)
            jobInfoBuilder.setMinimumLatency(TimeUnit.SECONDS.toMillis(5))
            jobInfoBuilder.setOverrideDeadline(JobInfo.DEFAULT_INITIAL_BACKOFF_MILLIS)
            jobScheduler.schedule(jobInfoBuilder.build())

        }

        fun stopJob(context: Context) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            jobScheduler.cancel(10)
        }
    }
}
