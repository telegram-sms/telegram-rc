package com.qwe7002.telegram_rc.data_structure

data class BeaconModel(
    val uuid: String,
    val major: Int,
    val minor: Int,
    val rssi: Int,
    val hardwareAddress: String,
    val distance: Double
)
