package com.qwe7002.telegram_rc.beacon

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import java.nio.ByteBuffer
import java.util.UUID

private const val DEFAULT_MANUFACTURER_ID = 0x004C // Apple, used by iBeacon

interface BeaconParser {
    fun parse(scanResult: ScanResult): Beacon?
    fun buildScanFilters(): List<ScanFilter>
}

private sealed class FieldSpec(val length: Int) {
    class Matcher(length: Int, val expected: ByteArray) : FieldSpec(length)
    class IntField(length: Int) : FieldSpec(length)
    class UuidField : FieldSpec(16)
    class ByteField : FieldSpec(1)
    class TxPower : FieldSpec(1)
}

private fun layoutToSpecs(layout: String): List<FieldSpec> {
    val specs = mutableListOf<FieldSpec>()
    for (token in layout.split(',')) {
        val trimmed = token.trim()
        require(trimmed.length >= 3 && trimmed[1] == ':') {
            "Invalid layout token: $trimmed"
        }
        val prefix = trimmed[0]
        val rest = trimmed.substring(2)
        val rangeText = rest.substringBefore('=')
        val parts = rangeText.split('-')
        require(parts.size == 2) { "Invalid range in token: $trimmed" }
        val length = parts[1].toInt() - parts[0].toInt() + 1
        require(length > 0) { "Non-positive length in token: $trimmed" }

        specs += when (prefix) {
            'm' -> {
                val expectedHex = rest.substringAfter('=', "")
                require(expectedHex.isNotEmpty()) {
                    "Matcher token missing expected value: $trimmed"
                }
                FieldSpec.Matcher(length, hexToBytes(expectedHex))
            }
            'i' -> when (length) {
                16 -> FieldSpec.UuidField()
                2 -> FieldSpec.IntField(length)
                1 -> FieldSpec.ByteField()
                else -> error("Unsupported identifier width: $length")
            }
            'p' -> FieldSpec.TxPower()
            'd' -> FieldSpec.ByteField()
            else -> error("Unsupported field prefix: $prefix")
        }
    }
    return specs
}

private fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Odd-length hex string: $hex" }
    val out = ByteArray(hex.length / 2)
    var i = 0
    while (i < hex.length) {
        out[i / 2] = ((Character.digit(hex[i], 16) shl 4) or
                Character.digit(hex[i + 1], 16)).toByte()
        i += 2
    }
    return out
}

private class LayoutBeaconParser(
    private val specs: List<FieldSpec>,
    private val manufacturerId: Int
) : BeaconParser {

    override fun parse(scanResult: ScanResult): Beacon? {
        val record = scanResult.scanRecord ?: return null
        val payload = record.getManufacturerSpecificData(manufacturerId)
            ?: return tryAllManufacturerEntries(scanResult)

        return parsePayload(payload, scanResult)
    }

    private fun tryAllManufacturerEntries(scanResult: ScanResult): Beacon? {
        val data = scanResult.scanRecord?.manufacturerSpecificData ?: return null
        for (i in 0 until data.size()) {
            val raw = data.valueAt(i) ?: continue
            val parsed = parsePayload(raw, scanResult)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parsePayload(payload: ByteArray, scanResult: ScanResult): Beacon? {
        val totalRequired = specs.sumOf { it.length }
        if (payload.size < totalRequired) return null

        val identifiers = mutableListOf<Any>()
        var txPower: Byte? = null
        var offset = 0
        for (spec in specs) {
            when (spec) {
                is FieldSpec.Matcher -> {
                    for (k in 0 until spec.length) {
                        if (payload[offset + k] != spec.expected[k]) return null
                    }
                    identifiers.add(payload.copyOfRange(offset, offset + spec.length).toList())
                }
                is FieldSpec.UuidField -> {
                    val buf = ByteBuffer.wrap(payload, offset, 16)
                    identifiers.add(UUID(buf.long, buf.long))
                }
                is FieldSpec.IntField -> {
                    var v = 0
                    for (k in 0 until spec.length) {
                        v = (v shl 8) or (payload[offset + k].toInt() and 0xFF)
                    }
                    identifiers.add(v)
                }
                is FieldSpec.ByteField -> identifiers.add(payload[offset])
                is FieldSpec.TxPower -> {
                    val tx = payload[offset]
                    txPower = tx
                    identifiers.add(tx)
                }
            }
            offset += spec.length
        }

        val device = scanResult.device
        val deviceName = scanResult.scanRecord?.deviceName
            ?: runCatching { device?.name }.getOrNull()

        return Beacon(
            hardwareAddress = device?.address ?: "",
            name = deviceName,
            identifiers = identifiers,
            txPower = txPower
        )
    }

    override fun buildScanFilters(): List<ScanFilter> {
        var matcher: FieldSpec.Matcher? = null
        var matcherOffset = 0
        var offset = 0
        for (spec in specs) {
            if (spec is FieldSpec.Matcher) {
                matcher = spec
                matcherOffset = offset
                break
            }
            offset += spec.length
        }

        val builder = ScanFilter.Builder()
        if (matcher != null) {
            val expected = matcher.expected
            val totalLen = matcherOffset + expected.size
            val data = ByteArray(totalLen)
            val mask = ByteArray(totalLen)
            System.arraycopy(expected, 0, data, matcherOffset, expected.size)
            for (i in matcherOffset until totalLen) mask[i] = 0xFF.toByte()
            builder.setManufacturerData(manufacturerId, data, mask)
        } else {
            builder.setManufacturerData(manufacturerId, ByteArray(0))
        }
        return listOf(builder.build())
    }
}

object BeaconParserFactory {
    @JvmStatic
    @JvmOverloads
    fun createFromLayout(
        layout: String,
        manufacturerId: Int = DEFAULT_MANUFACTURER_ID
    ): BeaconParser = LayoutBeaconParser(layoutToSpecs(layout), manufacturerId)
}
