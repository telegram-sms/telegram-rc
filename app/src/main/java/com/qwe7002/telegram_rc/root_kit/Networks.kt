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
        var shell: Shell? = null
        return try {
            shell = Shell.getShell()
            if (shell.isRoot) {
                val result = shell.newJob()
                    .add("svc wifi $state") // 执行的 root 命令
                    .exec()
                if (result.isSuccess) {
                    // 命令执行成功，可以获取输出
                    val output = result.out
                    Log.i("setWifi", output.toString())
                } else {
                    // 命令执行失败，可以获取错误信息
                    val error = result.err
                    Log.e("setWifi", "Error: $error")
                }
                result.isSuccess
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("setWifi", "Exception occurred: ${e.message}", e)
            false
        } finally {
            try {
                shell?.close()
            } catch (e: Exception) {
                Log.w("setWifi", "Failed to close shell: ${e.message}")
            }
        }
    }

    @JvmStatic
    fun setData(enable: Boolean): Boolean {
        var state = "disable"
        if (enable) {
            state = "enable"
        }
        var shell: Shell? = null
        return try {
            shell = Shell.getShell()
            if (shell.isRoot) {
                val result = shell.newJob()
                    .add("svc data $state") // 执行的 root 命令
                    .exec()
                if (result.isSuccess) {
                    // 命令执行成功，可以获取输出
                    val output = result.out
                    Log.i("setData", output.toString())
                } else {
                    // 命令执行失败，可以获取错误信息
                    val error = result.err
                    Log.e("setData", "Error: $error")
                }
                result.isSuccess
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("setData", "Exception occurred: ${e.message}", e)
            false
        } finally {
            try {
                shell?.close()
            } catch (e: Exception) {
                Log.w("setData", "Failed to close shell: ${e.message}")
            }
        }
    }


    @JvmStatic
    fun addDummyDevice(ipAddr: String): Boolean {
        var shell: Shell? = null
        return try {
            shell = Shell.getShell()
            if (shell.isRoot) {
                val result = shell.newJob()
                    .add("ip link add dummy0 type dummy")
                    .add("ip addr add $ipAddr/32 dev dummy0")
                    .add("ip link set dummy0 up").exec() // 执行的 root 命令
                if (result.isSuccess) {
                    // 命令执行成功，可以获取输出
                    val output = result.out
                    Log.i("addDummyDevice", output.toString())
                } else {
                    // 命令执行失败，可以获取错误信息
                    val error = result.err
                    Log.e("addDummyDevice", "Error: $error")
                }
                result.isSuccess
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("addDummyDevice", "Exception occurred: ${e.message}", e)
            false
        } finally {
            try {
                shell?.close()
            } catch (e: Exception) {
                Log.w("addDummyDevice", "Failed to close shell: ${e.message}")
            }
        }
    }

    @JvmStatic
    fun delDummyDevice(): Boolean {
        var shell: Shell? = null
        return try {
            shell = Shell.getShell()
            if (shell.isRoot) {
                val result = shell.newJob()
                    .add("ip link del dummy0").exec()
                if (result.isSuccess) {
                    // 命令执行成功，可以获取输出
                    val output = result.out
                    Log.i("delDummyDevice", output.toString())
                } else {
                    // 命令执行失败，可以获取错误信息
                    val error = result.err
                    Log.e("delDummyDevice", "Error: $error")
                }
                result.isSuccess
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("delDummyDevice", "Exception occurred: ${e.message}", e)
            false
        } finally {
            try {
                shell?.close()
            } catch (e: Exception) {
                Log.w("delDummyDevice", "Failed to close shell: ${e.message}")
            }
        }
    }
}
