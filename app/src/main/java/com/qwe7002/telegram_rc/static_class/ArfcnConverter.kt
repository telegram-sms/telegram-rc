package com.qwe7002.telegram_rc.static_class

import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.TelephonyManager
import android.util.Log

object ArfcnConverter {
    private const val TAG = "ArfcnConverter"

    // LTE Bands mapping (EARFCN ranges) - 根据3GPP TS 36.101标准
    private val lteBandRanges: Map<Int, Pair<Int, Int>> = mapOf(
        1 to Pair(0, 599),
        2 to Pair(600, 1199),
        3 to Pair(1200, 1949),
        4 to Pair(1950, 2399),
        5 to Pair(2400, 2649),
        6 to Pair(2650, 2749),
        7 to Pair(2750, 3449),
        8 to Pair(3450, 3799),
        9 to Pair(3800, 4149),
        10 to Pair(4150, 4749),
        11 to Pair(4750, 4949),
        12 to Pair(5010, 5179),
        13 to Pair(5180, 5279),
        14 to Pair(5280, 5379),
        17 to Pair(5730, 5849),
        18 to Pair(5850, 5999),
        19 to Pair(6000, 6149),
        20 to Pair(6150, 6449),
        21 to Pair(6450, 6599),
        22 to Pair(6600, 7399),
        23 to Pair(7500, 7699),
        24 to Pair(7700, 8039),
        25 to Pair(8040, 8689),
        26 to Pair(8690, 9039),
        27 to Pair(9040, 9209),
        28 to Pair(9210, 9659),
        29 to Pair(9660, 9769),
        30 to Pair(9770, 9869),
        31 to Pair(9870, 9919),
        32 to Pair(9920, 10359),
        33 to Pair(36000, 36199),
        34 to Pair(36200, 36349),
        35 to Pair(36350, 36949),
        36 to Pair(36950, 37549),
        37 to Pair(37550, 37749),
        38 to Pair(37750, 38249),
        39 to Pair(38250, 38649),
        40 to Pair(38650, 39649),
        41 to Pair(39650, 41589),
        42 to Pair(41590, 43589),
        43 to Pair(43590, 45589),
        44 to Pair(45590, 46589),
        46 to Pair(46590, 54339),
        47 to Pair(54340, 55339),
        48 to Pair(55340, 56339),
        49 to Pair(56340, 58339),
        65 to Pair(65536, 66435),
        66 to Pair(66436, 67335),
        67 to Pair(67336, 67535),
        68 to Pair(67536, 67835),
        69 to Pair(67836, 68335),
        70 to Pair(68336, 68585),
        71 to Pair(68586, 68935),
        72 to Pair(68936, 68985),
        73 to Pair(68986, 69035),
        74 to Pair(69036, 69465),
        75 to Pair(69466, 70315),
        76 to Pair(70316, 70365),
        85 to Pair(70366, 70545),
        87 to Pair(70546, 70595),
        88 to Pair(70596, 70645)
    )

    // NR Bands mapping (NR-ARFCN ranges) - 根据3GPP TS 38.104标准
    private val nrBandRanges: Map<Int, Pair<Int, Int>> = mapOf(
        1 to Pair(422000, 434000),
        2 to Pair(386000, 398000),
        3 to Pair(361000, 376000),
        5 to Pair(173800, 178800),
        7 to Pair(524000, 538000),
        8 to Pair(185000, 192000),
        12 to Pair(145800, 149200),
        13 to Pair(149200, 151200),
        14 to Pair(151600, 152500),
        18 to Pair(172000, 175000),
        20 to Pair(158200, 164200),
        24 to Pair(305000, 311800),
        25 to Pair(386000, 399000),
        26 to Pair(171800, 178800),
        28 to Pair(151600, 160600),
        29 to Pair(143400, 145600),
        30 to Pair(470000, 472000),
        34 to Pair(402000, 405000),
        38 to Pair(514000, 524000),
        39 to Pair(376000, 384000),
        40 to Pair(460000, 480000),
        41 to Pair(499200, 538000),
        46 to Pair(743334, 795000),
        47 to Pair(790000, 795000),
        48 to Pair(636667, 646667),
        50 to Pair(286400, 303400),
        51 to Pair(285400, 286400),
        53 to Pair(496700, 499000),
        65 to Pair(422000, 440000),
        66 to Pair(422000, 440000),
        67 to Pair(147600, 151600),
        70 to Pair(399000, 404000),
        71 to Pair(123400, 130400),
        74 to Pair(295000, 303600),
        75 to Pair(286400, 303400),
        76 to Pair(285400, 286400),
        77 to Pair(620000, 680000),
        78 to Pair(620000, 653333),
        79 to Pair(693334, 733333),
        80 to Pair(342000, 357000),
        81 to Pair(176000, 183000),
        82 to Pair(166400, 172400),
        83 to Pair(140600, 149600),
        84 to Pair(384000, 396000),
        85 to Pair(145600, 149200),
        86 to Pair(342000, 356000),
        89 to Pair(164800, 169800),
        90 to Pair(499200, 538000),
        91 to Pair(166400, 172400),
        92 to Pair(166400, 172400),
        93 to Pair(176000, 183000),
        94 to Pair(176000, 183000),
        95 to Pair(402000, 405000),
        96 to Pair(795000, 875000),
        97 to Pair(460000, 480000),
        98 to Pair(376000, 384000),
        99 to Pair(305000, 312100),
        100 to Pair(183880, 185000),
        101 to Pair(380000, 382000),
        102 to Pair(795000, 845000),
        104 to Pair(845000, 875000)
    )

    // TDD频段列表 - 根据3GPP标准定义
    private val tddBands = setOf(
        34, 38, 39, 40, 41, 46, 47, 48, 50, 51, 53,
        77, 78, 79, 90, 96, 101, 102, 104
    )

    // FDD频段列表
    private val fddBands = setOf(
        1, 2, 3, 5, 7, 8, 12, 13, 14, 18, 20, 24, 25, 26, 28, 30,
        65, 66, 70, 71, 74, 100
    )

    // SUL (Supplementary Uplink) 和 SDL (Supplementary Downlink) 频段
    private val sulBands = setOf(
        80, 81, 82, 83, 84, 86, 89, 95, 97, 98, 99
    )

    private val sdlBands = setOf(
        29, 67, 75, 76
    )

    /**
     * 根据EARFCN获取LTE频段
     */
    fun getLteBand(earfcn: Int): Int? {
        for ((band, range) in lteBandRanges) {
            if (earfcn in range.first..range.second) {
                return band
            }
        }
        return null
    }

    /**
     * 根据NR-ARFCN获取NR频段 - 使用TDD优先逻辑
     */
    fun getNrBand(arfcn: Int): Int? {
        // 找出所有匹配的频段
        val matchingBands = nrBandRanges.filter { (_, range) ->
            arfcn in range.first..range.second
        }.keys.toList()

        if (matchingBands.isEmpty()) {
            return null
        }

        // 如果只有一个匹配，直接返回
        if (matchingBands.size == 1) {
            return matchingBands.first()
        }

        Log.d(TAG, "Multiple bands match ARFCN $arfcn: $matchingBands")

        // 多个匹配时的优先级规则：
        // 1. TDD频段优先
        // 2. 如果都是TDD或都不是TDD，选择范围更小的（更精确）
        val tddMatches = matchingBands.filter { it in tddBands }
        val fddMatches = matchingBands.filter { it in fddBands }

        return when {
            // 优先返回TDD频段
            tddMatches.isNotEmpty() -> {
                // 如果有多个TDD频段匹配，选择范围最小的
                tddMatches.minByOrNull { band ->
                    val range = nrBandRanges[band]!!
                    range.second - range.first
                } ?: tddMatches.first()
            }
            // 其次返回FDD频段
            fddMatches.isNotEmpty() -> {
                fddMatches.minByOrNull { band ->
                    val range = nrBandRanges[band]!!
                    range.second - range.first
                } ?: fddMatches.first()
            }
            // 最后返回SUL/SDL频段
            else -> {
                matchingBands.minByOrNull { band ->
                    val range = nrBandRanges[band]!!
                    range.second - range.first
                } ?: matchingBands.first()
            }
        }
    }

    /**
     * 获取频段的双工模式
     */
    fun getBandDuplexMode(band: Int): String {
        return when (band) {
            in tddBands -> "TDD"
            in fddBands -> "FDD"
            in sulBands -> "SUL"
            in sdlBands -> "SDL"
            else -> "Unknown"
        }
    }

    /**
     * 从CellInfo获取信号强度和频段信息
     */
    fun getCellInfoDetails(cellInfo: CellInfo): String {
        return try {
            when (cellInfo) {
                is CellInfoLte -> {
                    val cellIdentity = cellInfo.cellIdentity
                    val cellSignalStrength = cellInfo.cellSignalStrength
                    val earfcn = cellIdentity.earfcn
                    Log.d(TAG, "getCellInfoDetails LTE: EARFCN=$earfcn")

                    val band = if (earfcn != Int.MAX_VALUE) {
                        getLteBand(earfcn)
                    } else {
                        null
                    }
                    val dbm = cellSignalStrength.dbm

                    "$dbm dBm${if (band != null) ", B$band" else ""}${if (earfcn != Int.MAX_VALUE) ", EARFCN: $earfcn" else ""}"
                }
                is CellInfoNr -> {
                    val cellIdentity = cellInfo.cellIdentity
                    val cellSignalStrength = cellInfo.cellSignalStrength

                    // 获取NR-ARFCN
                    val arfcn = try {
                        cellIdentity.javaClass.getMethod("getNrarfcn").invoke(cellIdentity) as Int
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Int.MAX_VALUE
                    }
                    Log.d(TAG, "getCellInfoDetails NR: ARFCN=$arfcn")

                    val band = if (arfcn != Int.MAX_VALUE) getNrBand(arfcn) else null
                    val duplexMode = band?.let { getBandDuplexMode(it) }

                    // 获取RSRP值
                    val ssRsrp = try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            cellSignalStrength.javaClass.getMethod("getSsRsrp").invoke(cellSignalStrength) as Int
                        } else {
                            @Suppress("DEPRECATION")
                            cellSignalStrength.javaClass.getMethod("getRsrp").invoke(cellSignalStrength) as Int
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Int.MAX_VALUE
                    }

                    val rsrpStr = if (ssRsrp != Int.MAX_VALUE) "$ssRsrp dBm" else "N/A"

                    "$rsrpStr${if (band != null) ", N$band" else ""}${if (duplexMode != null) " ($duplexMode)" else ""}${if (arfcn != Int.MAX_VALUE) ", ARFCN: $arfcn" else ""}"
                }
                else -> {
                    "Unknown cell type"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cell info details", e)
            "Error getting cell info"
        }
    }
}
