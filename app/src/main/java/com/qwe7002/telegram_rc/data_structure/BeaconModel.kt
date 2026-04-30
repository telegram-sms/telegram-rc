package com.qwe7002.telegram_rc.data_structure

object BeaconModel {
    data class BeaconModel(
        val uuid: String,
        val major: Int,
        val minor: Int,
        val rssi: Int,
        val hardwareAddress: String,
        val distance: Double,
        val name: String? = null
    )

    fun beaconItemName(uuid: String, major: Int, minor: Int): String {
        return "UUID:" + uuid + "Major:" + major + "Minor:" + minor
    }

    private val itemNameRegex = Regex("^UUID:(.+)Major:(\\d+)Minor:(\\d+)$")

    fun parseBeaconItemName(name: String): Triple<String, Int, Int>? {
        val match = itemNameRegex.matchEntire(name) ?: return null
        val (uuid, majorStr, minorStr) = match.destructured
        val major = majorStr.toIntOrNull() ?: return null
        val minor = minorStr.toIntOrNull() ?: return null
        return Triple(uuid, major, minor)
    }
}
