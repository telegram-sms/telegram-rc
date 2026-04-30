package com.qwe7002.telegram_rc.beacon

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

class RssiFilter(private val validityMillis: Long = 20_000L) {
    private data class Sample(val rssi: Byte, val timestamp: Long)

    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun add(rssi: Byte) {
        val now = SystemClock.elapsedRealtime()
        while (samples.isNotEmpty() && samples.first().timestamp + validityMillis < now) {
            samples.removeFirst()
        }
        samples.addLast(Sample(rssi, now))
    }

    @Synchronized
    fun average(): Byte? {
        if (samples.isEmpty()) return null
        var sum = 0
        for (s in samples) sum += s.rssi
        return (sum / samples.size).toByte()
    }
}

class Ranger internal constructor() {
    private val filters = ConcurrentHashMap<Beacon, RssiFilter>()

    fun calculateDistance(beacon: Beacon): Double {
        val tx = beacon.txPower ?: return Double.MAX_VALUE
        val effectiveRssi = filters[beacon]?.average() ?: beacon.rssi
        return 10.0.pow((tx - effectiveRssi).toDouble() / 20.0)
    }

    internal fun record(beacon: Beacon, rssi: Byte) {
        filters.getOrPut(beacon) { RssiFilter() }.add(rssi)
    }

    internal fun forget(beacon: Beacon) {
        filters.remove(beacon)
    }
}
