package com.qwe7002.telegram_rc.root_kit

import android.util.Log
import com.topjohnwu.superuser.Shell

object ActivityManage {
    @Suppress("unused")
    fun startService(packageName: String, serviceName: String): Boolean {
        val shell = Shell.getShell()
        if (shell.isRoot) {
            val result = shell.newJob()
                .add("am startservice -n $packageName/$serviceName") // 执行的 root 命令
                .exec()
            if (result.isSuccess) {
                // 命令执行成功，可以获取输出
                val output = result.out
                Log.i("startService", output.toString())
            } else {
                // 命令执行失败，可以获取错误信息
                val error = result.err
                Log.e("startService", "Error: $error")
            }
            return result.isSuccess
        }
        return false
    }

    @JvmStatic
    @Suppress("unused")
    fun startForegroundService(packageName: String, serviceName: String): Boolean {
        val shell = Shell.getShell()
        if (shell.isRoot) {
            val result = shell.newJob()
                .add("am start-foreground-service -n $packageName/$serviceName") // 执行的 root 命令
                .exec()
            if (result.isSuccess) {
                // 命令执行成功，可以获取输出
                val output = result.out
                Log.i("startForegroundService", output.toString())
            } else {
                // 命令执行失败，可以获取错误信息
                val error = result.err
                Log.e("startForegroundService", "Error: $error")
            }
            return result.isSuccess
        }
        return false
    }

    @Suppress("unused")
    fun startActivity(packageName: String, activityName: String): Boolean {
        val shell = Shell.getShell()
        if (shell.isRoot) {
            val result = shell.newJob()
                .add("am start -n $packageName/$activityName") // 执行的 root 命令
                .exec()
            if (result.isSuccess) {
                // 命令执行成功，可以获取输出
                val output = result.out
                Log.i("startActivity", output.toString())
            } else {
                // 命令执行失败，可以获取错误信息
                val error = result.err
                Log.e("startActivity", "Error: $error")
            }
            return result.isSuccess
        }
        return false
    }

    @JvmStatic
    @Suppress("unused")
    fun forceStopService(packageName: String): Boolean {
        val shell = Shell.getShell()
        if (shell.isRoot) {
            val result = shell.newJob()
                .add("am force-stop -n $packageName") // 执行的 root 命令
                .exec()
            if (result.isSuccess) {
                // 命令执行成功，可以获取输出
                val output = result.out
                Log.i("forceStopService", output.toString())
            } else {
                // 命令执行失败，可以获取错误信息
                val error = result.err
                Log.e("forceStopService", "Error: $error")
            }
            return result.isSuccess
        }
        return false
    }

}
