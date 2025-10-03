package com.qwe7002.telegram_rc.shizuku_kit

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object BatteryHealth {
    private const val TAG = "Battery"

    /**
     * 使用Shizuku读取系统文件获取电池设计容量和当前容量
     * @return BatteryHealthInfo 包含电池健康信息的对象
     */
    fun getBatteryHealthFromSysfs(): BatteryHealthInfo {
        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "Shizuku is not running")
            return BatteryHealthInfo(0, 0, 0.0, 0, "",0.0, false, "Shizuku is not running")
        }

        // 检查Shizuku权限
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Shizuku permission not granted")
            return BatteryHealthInfo(0, 0, 0.0, 0, "",0.0, false, "Shizuku permission not granted")
        }

        return try {
            val chargeFullDesign =
                readSysfsFile("/sys/class/power_supply/battery/charge_full_design")
            val chargeFull = readSysfsFile("/sys/class/power_supply/battery/charge_full")
            val cycleCount = readSysfsFile("/sys/class/power_supply/battery/cycle_count")
            val healthStatus = readSysfsFileString("/sys/class/power_supply/battery/health")
            val temperature = readSysfsFile("/sys/class/power_supply/battery/temp")
            if (chargeFullDesign > 0) {
                val healthRatio = (chargeFull.toDouble() / chargeFullDesign.toDouble()) * 100
                BatteryHealthInfo(
                    chargeFullDesign,
                    chargeFull,
                    healthRatio,
                    cycleCount,
                    healthStatus.trim().replace("\n",""),
                    temperature.toDouble() / 10.0,
                    true,
                    ""
                )
            } else {
                BatteryHealthInfo(0, 0, 0.0, 0, "", 0.0,false, "Invalid charge_full_design value")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception occurred: ${e.message}", e)
            BatteryHealthInfo(0, 0, 0.0, 0, "", 0.0,false, e.message ?: "Unknown error")
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
            val service: IShizukuService? = IShizukuService.Stub.asInterface(Shizuku.getBinder())
            if (service == null) {
                return CommandResult(false, "", "Shizuku service not available")
            }

            val process = service.newProcess(command, null, null)
            // 正确处理 ParcelFileDescriptor
            val inputStream = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
            val reader = BufferedReader(InputStreamReader(inputStream))

            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }

            process.waitFor()
            return CommandResult(process.exitValue() == 0, output.toString(), "")
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed: " + e.message)
            return CommandResult(false, "", e.message)
        }
    }

    /**
     * 使用Shizuku读取系统文件内容
     * @param filePath 文件路径
     * @return 文件内容转换为整数
     */
    private fun readSysfsFile(filePath: String): Long {
        val commandResult = executeCommand(arrayOf("cat", filePath))
        return if (commandResult.isSuccess) {
            commandResult.output.trim().toLongOrNull() ?: 0L
        } else {
            0L
        }
    }

    /**
     * 使用Shizuku读取系统文件内容
     * @param filePath 文件路径
     * @return 文件内容
     */
    private fun readSysfsFileString(filePath: String): String {
        val commandResult = executeCommand(arrayOf("cat", filePath))
        return if (commandResult.isSuccess) {
            commandResult.output
        } else {
            ""
        }
    }

    /**
     * 命令执行结果数据类
     * @param isSuccess 命令是否执行成功
     * @param output 命令的标准输出
     * @param errorOutput 命令的错误输出
     */
    data class CommandResult(
        val isSuccess: Boolean,
        val output: String,
        val errorOutput: String?
    )

    /**
     * 电池健康信息数据类
     * @param chargeFullDesign 电池设计容量
     * @param chargeFull 电池当前容量
     * @param healthRatio 电池健康度百分比
     * @param isSuccess 是否成功获取信息
     * @param errorMessage 错误信息
     */
    data class BatteryHealthInfo(
        val chargeFullDesign: Long,
        val chargeFull: Long,
        val healthRatio: Double,
        val cycleCount: Long,
        val healthStatus: String,
        val temperature: Double,
        val isSuccess: Boolean,
        val errorMessage: String
    )
}
