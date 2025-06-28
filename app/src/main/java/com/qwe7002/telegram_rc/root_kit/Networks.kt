package com.qwe7002.telegram_rc.root_kit

import android.util.Log
import com.topjohnwu.superuser.Shell

object Networks {
    @JvmStatic
    fun setWifi(enable: Boolean): Boolean {
        var state = "disable"
        if (enable) {
            state = "enable"
        }
        val shell = Shell.getShell()
        if (shell.isRoot()) {
            val result = shell.newJob()
                .add("svc wifi $state") // 执行的 root 命令
                .exec()
            if (result.isSuccess()) {
                // 命令执行成功，可以获取输出
                val output = result.getOut()
                Log.i("setWifi", output.toString())
            } else {
                // 命令执行失败，可以获取错误信息
                val error = result.getErr()
                Log.e("setWifi", "Error: $error")
            }
            return result.isSuccess
        }
        return false
    }

    @JvmStatic
    fun setData(enable: Boolean): Boolean {
        var state = "disable"
        if (enable) {
            state = "enable"
        }
        val shell = Shell.getShell()
        if (shell.isRoot()) {
            val result = shell.newJob()
                .add("svc data $state") // 执行的 root 命令
                .exec()
            if (result.isSuccess()) {
                // 命令执行成功，可以获取输出
                val output = result.getOut()
                Log.i("setData", output.toString())
            } else {
                // 命令执行失败，可以获取错误信息
                val error = result.getErr()
                Log.e("setData", "Error: $error")
            }
            return result.isSuccess
        }
        return false
    }


    @JvmStatic
    fun addDummyDevice(ipAddr: String): Boolean {
        val shell = Shell.getShell()
        if (shell.isRoot()) {
            val result = shell.newJob()
                .add("ip link add dummy0 type dummy")
                .add("ip addr add $ipAddr/32 dev dummy0").exec()// 执行的 root 命令
            if (result.isSuccess()) {
                // 命令执行成功，可以获取输出
                val output = result.getOut()
                Log.i("startService", output.toString())
            } else {
                // 命令执行失败，可以获取错误信息
                val error = result.getErr()
                Log.e("startService", "Error: $error")
            }
            return result.isSuccess
        }
        return false
    }

    @JvmStatic
    fun delDummyDevice(): Boolean {
        val shell = Shell.getShell()
        if (shell.isRoot()) {
            val result = shell.newJob()
                .add("ip link del dummy0").exec()
            if (result.isSuccess()) {
                // 命令执行成功，可以获取输出
                val output = result.getOut()
                Log.i("startService", output.toString())
            } else {
                // 命令执行失败，可以获取错误信息
                val error = result.getErr()
                Log.e("startService", "Error: $error")
            }
            return result.isSuccess
        }
        return false
    }
}
