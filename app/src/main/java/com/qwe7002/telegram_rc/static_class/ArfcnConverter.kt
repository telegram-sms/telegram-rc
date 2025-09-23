package com.qwe7002.telegram_rc.static_class

import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.TelephonyManager
import android.util.Log

object ArfcnConverter {
    private const val TAG = "ArfcnConverter"

    // LTE Bands mapping (EARFCN ranges) - 根据3GPP TS 36.101标准修正
    private val lteBandRanges = mapOf(
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
        29 to Pair(9660, 9769),   // SDL only
        30 to Pair(9770, 9869),
        31 to Pair(9870, 9919),
        32 to Pair(9920, 10359),  // SDL only
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
        46 to Pair(46590, 54339), // LAA band, 修正范围
        47 to Pair(54340, 55339), // V2X band, 修正范围
        48 to Pair(55340, 56339), // CBRS band, 修正范围
        49 to Pair(56340, 58339), // 修正范围
        65 to Pair(65536, 66435),
        66 to Pair(66436, 67335), // 修正上限
        67 to Pair(67336, 67535), // SDL only
        68 to Pair(67536, 67835),
        69 to Pair(67836, 68335), // SDL only
        70 to Pair(68336, 68585),
        71 to Pair(68586, 68935), // 600MHz band, 完全修正
        72 to Pair(68936, 68985),
        73 to Pair(68986, 69035),
        74 to Pair(69036, 69465),
        75 to Pair(69466, 70315), // SDL only
        76 to Pair(70316, 70365), // SDL only
        85 to Pair(70366, 70545),
        87 to Pair(70546, 70595),
        88 to Pair(70596, 70645)
    )


    // NR Bands mapping (NR-ARFCN ranges) - FR1部分，根据3GPP TS 38.104标准修正
    private val nrBandRanges = mapOf(
        1 to Pair(422000, 434000),   // 2110-2170 MHz
        2 to Pair(386000, 398000),   // 1930-1990 MHz
        3 to Pair(361000, 376000),   // 1805-1880 MHz
        5 to Pair(173800, 178800),   // 869-894 MHz
        7 to Pair(524000, 538000),   // 2620-2690 MHz
        8 to Pair(185000, 192000),   // 925-960 MHz, 修正上限
        12 to Pair(145800, 149200),  // 729-746 MHz
        13 to Pair(149200, 151200),  // 746-756 MHz, 完全修正
        14 to Pair(151600, 153600),  // 758-768 MHz
        18 to Pair(172000, 175000),  // 860-875 MHz, 完全修正
        20 to Pair(158200, 164200),  // 791-821 MHz
        24 to Pair(305000, 311800),  // 1525-1559 MHz, 完全修正
        25 to Pair(386000, 399000),  // 1930-1995 MHz
        26 to Pair(171800, 178800),  // 859-894 MHz, 完全修正
        28 to Pair(151600, 160600),  // 758-803 MHz, 修正上限
        29 to Pair(143400, 145600),  // 717-728 MHz SDL, 完全修正
        30 to Pair(470000, 472000),  // 2350-2360 MHz, 完全修正
        34 to Pair(402000, 405000),  // 2010-2025 MHz, 修正下限
        38 to Pair(514000, 524000),  // 2570-2620 MHz
        39 to Pair(376000, 384000),  // 1880-1920 MHz, 完全修正
        40 to Pair(460000, 480000),  // 2300-2400 MHz, 完全修正
        41 to Pair(499200, 538000),  // 2496-2690 MHz
        46 to Pair(743334, 795000),  // 5150-5925 MHz, 完全修正
        47 to Pair(790000, 795000),  // 5855-5925 MHz, 完全修正
        48 to Pair(636667, 646667),  // 3550-3700 MHz, 完全修正
        50 to Pair(286400, 303400),  // 1432-1517 MHz, 完全修正
        51 to Pair(285400, 286400),  // 1427-1432 MHz, 完全修正
        53 to Pair(496700, 499000),  // 2483.5-2495 MHz, 完全修正
        65 to Pair(422000, 440000),  // 2110-2200 MHz, 修正上限
        66 to Pair(422000, 440000),  // 2110-2200 MHz, 修正上限
        67 to Pair(147600, 151600),  // 738-758 MHz SDL, 完全修正
        70 to Pair(399000, 404000),  // 1995-2020 MHz, 完全修正
        71 to Pair(123400, 130400),  // 617-652 MHz, 修正上限
        74 to Pair(295000, 303600),  // 1475-1518 MHz, 完全修正
        75 to Pair(286400, 303400),  // 1432-1517 MHz SDL, 完全修正
        76 to Pair(285400, 286400),  // 1427-1432 MHz SDL, 完全修正
        77 to Pair(620000, 680000),  // 3300-4200 MHz, 完全修正
        78 to Pair(620000, 653333),  // 3300-3800 MHz, 完全修正
        79 to Pair(693334, 733333),  // 4400-5000 MHz, 完全修正
        80 to Pair(342000, 357000),  // 1710-1785 MHz SUL, 完全修正
        81 to Pair(176000, 183000),  // 880-915 MHz SUL, 完全修正
        82 to Pair(166400, 172400),  // 832-862 MHz SUL, 完全修正
        83 to Pair(140600, 149600),  // 703-748 MHz SUL, 完全修正
        84 to Pair(384000, 396000),  // 1920-1980 MHz SUL, 完全修正
        85 to Pair(145600, 149200),  // 728-746 MHz, 完全修正
        86 to Pair(342000, 356000),  // 1710-1780 MHz SUL, 完全修正
        89 to Pair(164800, 169800),  // 824-849 MHz SUL, 完全修正
        90 to Pair(499200, 538000),  // 2496-2690 MHz
        91 to Pair(166400, 172400),  // UL: 832-862 MHz, 完全修正
        92 to Pair(166400, 172400),  // UL: 832-862 MHz, 完全修正
        93 to Pair(176000, 183000),  // UL: 880-915 MHz, 完全修正
        94 to Pair(176000, 183000),  // UL: 880-915 MHz, 完全修正
        95 to Pair(402000, 405000),  // 2010-2025 MHz SUL, 完全修正
        96 to Pair(795000, 875000),  // 5925-7125 MHz, 完全修正
        97 to Pair(460000, 480000),  // 2300-2400 MHz SUL, 完全修正
        98 to Pair(376000, 384000),  // 1880-1920 MHz SUL, 完全修正
        99 to Pair(305000, 312100),  // 1626.5-1660.5 MHz SUL, 完全修正
        100 to Pair(183880, 185000), // 919.4-925 MHz, 完全修正
        101 to Pair(380000, 382000), // 1900-1910 MHz, 完全修正
        102 to Pair(795000, 845000), // 5925-6425 MHz, 完全修正
        104 to Pair(845000, 875000)  // 6425-7125 MHz, 完全修正
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
     * 根据NR-ARFCN获取NR频段
     */
    fun getNrBand(arfcn: Int): Int? {
        for ((band, range) in nrBandRanges) {
            if (arfcn in range.first..range.second) {
                return band
            }
        }
        return null
    }

    /**
     * 将频率(MHz)转换为NR-ARFCN
     */
    fun frequencyToNrArfcn(frequency: Double): Int {
        return when {
            frequency in 0.0..3000.0 -> {
                (frequency * 1000 / 5).toInt()
            }
            frequency in 3000.0..24250.0 -> {
                (600000 + (frequency - 3000) * 1000 / 15).toInt()
            }
            frequency in 24250.0..100000.0 -> {
                (2016667 + (frequency - 24250) * 1000 / 60).toInt()
            }
            else -> -1
        }
    }

    /**
     * 将NR-ARFCN转换为频率(MHz)
     */
    fun nrArfcnToFrequency(arfcn: Int): Double {
        return when {
            arfcn in 0..599999 -> {
                arfcn * 0.005
            }
            arfcn in 600000..2016666 -> {
                3000.0 + (arfcn - 600000) * 0.015
            }
            arfcn in 2016667..3279167 -> {
                24250.0 + (arfcn - 2016667) * 0.060
            }
            else -> -1.0
        }
    }

    /**
     * 将频率(MHz)转换为EARFCN
     */
    fun frequencyToEarfcn(frequency: Double, isUplink: Boolean = true): Int {
        // 这是一个简化的实现，实际的转换需要考虑具体的频段
        return (frequency * 10 - (if (isUplink) 19200 else 21100)).toInt()
    }

    /**
     * 将EARFCN转换为频率(MHz)
     */
    fun earfcnToFrequency(earfcn: Int): Double {
        // 简化的实现，实际应该根据具体频段进行计算
        return (earfcn / 10.0) + 1920.0
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
                    val earfcn =
                        cellIdentity.earfcn
                    Log.d(TAG, "getCellInfoDetails: $earfcn")
                    val band = if (earfcn != Int.MAX_VALUE) {
                        getLteBand(earfcn)
                    } else {
                        null
                    }
                    val dbm = cellSignalStrength.dbm
                    
                    "($dbm dBm${if (band != null) ", B$band" else ""}${if (earfcn != Int.MAX_VALUE) ", EARFCN: $earfcn" else ""})"
                }
                is CellInfoNr -> {
                    val cellIdentity = cellInfo.cellIdentity
                    val cellSignalStrength = cellInfo.cellSignalStrength

                    // 获取NR-ARFCN
                    val arfcn = try {
                        cellIdentity.javaClass.getMethod("getNrarfcn").invoke(cellIdentity) as Int
                    } catch (e: Exception) {
                        Int.MAX_VALUE
                    }
                    Log.d(TAG, "getCellInfoDetails: $arfcn")
                    val band = if (arfcn != Int.MAX_VALUE) getNrBand(arfcn) else null

                    // 获取RSRP值
                    val ssRsrp = try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            cellSignalStrength.javaClass.getMethod("getSsRsrp").invoke(cellSignalStrength) as Int
                        } else {
                            @Suppress("DEPRECATION")
                            cellSignalStrength.javaClass.getMethod("getRsrp").invoke(cellSignalStrength) as Int
                        }
                    } catch (e: Exception) {
                        Int.MAX_VALUE
                    }

                    val rsrpStr = if (ssRsrp != Int.MAX_VALUE) "$ssRsrp dBm" else "N/A"

                    "($rsrpStr${if (band != null) ", N$band" else ""}${if (arfcn != Int.MAX_VALUE) ", ARFCN: $arfcn" else ""})"
                }
                else -> {
                    "(Unknown cell type)"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cell info details", e)
            "(Error getting cell info)"
        }
    }
}
