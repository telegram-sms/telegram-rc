package com.qwe7002.telegram_rc.beacon

import java.util.concurrent.TimeUnit

class ScanDuration private constructor(
    val scanMillis: Long,
    val restMillis: Long
) {
    companion object {
        @JvmField
        val UNIFORM: ScanDuration = ScanDuration(
            TimeUnit.SECONDS.toMillis(6),
            TimeUnit.SECONDS.toMillis(6)
        )

        fun of(scanMillis: Long, restMillis: Long): ScanDuration {
            require(scanMillis >= 0) { "scan duration must be non-negative" }
            require(restMillis >= 0) { "rest duration must be non-negative" }
            return ScanDuration(scanMillis, restMillis)
        }
    }
}
