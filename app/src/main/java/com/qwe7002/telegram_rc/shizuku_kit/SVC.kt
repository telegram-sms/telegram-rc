@file:Suppress("CAST_NEVER_SUCCEEDS")

package com.qwe7002.telegram_rc.shizuku_kit

import android.content.pm.PackageManager
import android.util.Log
import com.qwe7002.telegram_rc.MMKV.Const
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader


object SVC {

    @JvmStatic
    fun setWifi(enable: Boolean): Boolean {
        val state = if (enable) "enable" else "disable"
        return try {
            val result = executeCommand(arrayOf("svc", "wifi", state))
            Log.i(shizuku.TAG, "Command output: $result")
            result.isSuccess
        } catch (e: Exception) {
            Log.e(shizuku.TAG, "Exception occurred: ${e.message}", e)
            false
        }
    }

    @JvmStatic
    fun setData(enable: Boolean): Boolean {
        if (!Shizuku.pingBinder()) {
            Log.e(shizuku.TAG, "Shizuku is not running")
            return false
        }

        // 检查Shizuku权限
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e(shizuku.TAG, "Shizuku permission not granted")
            return false
        }

        val state = if (enable) "enable" else "disable"
        return try {
            val result = executeCommand(arrayOf("svc", "data", state))
            Log.i(shizuku.TAG, "Command output: $result")
            result.isSuccess
        } catch (e: Exception) {
            Log.e(shizuku.TAG, "Exception occurred: ${e.message}", e)
            false
        }
    }

    @JvmStatic
    fun setBlueTooth(enable: Boolean): Boolean {
        if (!Shizuku.pingBinder()) {
            Log.e(shizuku.TAG, "Shizuku is not running")
            return false
        }

        // 检查Shizuku权限
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e(shizuku.TAG, "Shizuku permission not granted")
            return false
        }

        val state = if (enable) "enable" else "disable"
        return try {
            val result = executeCommand(arrayOf("svc", "bluetooth", state))
            Log.i(shizuku.TAG, "Command output: $result")
            result.isSuccess
        } catch (e: Exception) {
            Log.e(shizuku.TAG, "Exception occurred: ${e.message}", e)
            false
        }
    }

    /**
     * 使用Shizuku执行单个shell命令
     * @param command 要执行的命令
     * @return CommandResult 包含执行结果的对象
     */
    private fun executeCommand(command: Array<String>): CommandResult {
        try {
            // 获取 Shizuku 服务
            val service: IShizukuService =
                IShizukuService.Stub.asInterface(Shizuku.getBinder()) ?: return CommandResult(
                    false,
                    "",
                    "Shizuku service not available"
                )

            val process = service.newProcess(command, null, null)
            // 读取输出（示例）
            @Suppress("USELESS_CAST") val reader = BufferedReader(InputStreamReader(process.inputStream as InputStream?))
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                Log.d(shizuku.TAG, line!!)
            }

            // 使用線程來實現超時
            val processThread = Thread {
                try {
                    process.waitFor()
                } catch (e: Exception) {
                    Log.e(shizuku.TAG, "Process wait error: ${e.message}",e)
                }
            }
            processThread.start()
            processThread.join(10000) // 10秒超時

            if (processThread.isAlive) {
                process.destroy()
                processThread.interrupt()
                Log.e(shizuku.TAG, "Command timed out")
                return CommandResult(false, "", "Command timed out")
            }

            return CommandResult(process.exitValue() == 0, reader.readText(), "")
        } catch (e: java.lang.Exception) {
            Log.e(shizuku.TAG, "Execution failed: " + e.message,e)
            return CommandResult(false, "", e.message)
        }
    }

    /**
     * 命令执行结果数据类
     * @param isSuccess 命令是否执行成功
     * @param output 命令的标准输出
     * @param errorOutput 命令的错误输出
     */
    private data class CommandResult(
        val isSuccess: Boolean,
        val output: String,
        val errorOutput: String?
    )
}
