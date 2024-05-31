package com.qwe7002.telegram_rc.data_structure

data class BeaconModel(
    val uuid: String,
    val major: Int,
    val minor: Int,
    val rssi: Int,
    val hardwareAddress: String,
    val distance: Double
)

fun beaconItemName(uuid: String, major: Int, minor: Int): String {
    return "UUID:" + uuid + "Major:" + major + "Minor:" + minor
}
