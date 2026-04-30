package com.qwe7002.telegram_rc.beacon

import java.util.UUID

data class Beacon(
    val hardwareAddress: String,
    val name: String?,
    val identifiers: List<Any>,
    val txPower: Byte?,
    var rssi: Byte = 0,
    var lastSeenElapsedRealtime: Long = 0L
) {
    fun getIdentifierAsUuid(index: Int): UUID = identifiers[index] as UUID
    fun getIdentifierAsInt(index: Int): Int = identifiers[index] as Int
    fun getIdentifierAsByte(index: Int): Byte = identifiers[index] as Byte

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Beacon) return false
        return hardwareAddress == other.hardwareAddress && identifiers == other.identifiers
    }

    override fun hashCode(): Int = 31 * hardwareAddress.hashCode() + identifiers.hashCode()
}
