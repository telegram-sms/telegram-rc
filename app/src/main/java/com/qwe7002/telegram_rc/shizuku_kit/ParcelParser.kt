package com.qwe7002.telegram_rc.shizuku_kit

class ParcelParser {
    companion object {
        /**
         * Parses hex dump data from Parcel format into a string
         * Precise version: treats the hex as big-endian word dump for little-endian UTF-16 string.
         * For each 32-bit word (8 hex chars), parses the low 16-bit first (as first char), then high 16-bit (as second char).
         * Skips header (first two 32-bit words), uses length from second word's low 16-bit if applicable.
         * Only includes printable non-null characters.
         * Example input format: as provided, outputs "460012246299164"
         */
        fun parseParcelData(hexDump: String): String {
            val result = StringBuilder()

            // Extract all hex parts and concatenate without spaces or non-hex
            val lines = hexDump.lines()
            val allHex = StringBuilder()
            for (line in lines) {
                if (!line.contains(":")) continue
                val colonIndex = line.indexOf(':')
                val quoteIndex = line.indexOf('\'')
                if (colonIndex != -1 && quoteIndex != -1) {
                    var hexPart = line.substring(colonIndex + 1, quoteIndex).trim()
                    hexPart = hexPart.replace(Regex("[^0-9a-fA-F]"), "")
                    allHex.append(hexPart.lowercase())
                }
            }
            val hexString = allHex.toString()

            // Header is first 16 hex chars (two 32-bit words: 8 each)
            val headerEnd = 16
            if (hexString.length < headerEnd) return ""

            // Parse from after header
            var offset = headerEnd
            var charCount = 0
            val length = try {
                // Second word low 16-bit: hexString[8:12] is first word low? Wait, first 8:00000000 high and low 0000 0000
                // Second 8:0000000f : high 0000 low 000f
                // So low 16 of second word: positions 12 to 16: but since 0000000f, chars 12-16:000f but 4 chars 000f? HexString starts 000000000000000f...
                // Positions 0-7:00000000, 8-15:0000000f
                // Low 16 of second: chars 12-15: 0 0 0 f (since 0000 000f, chars8-11:0000,12-15:000f
                Integer.parseInt(hexString.substring(12, 16), 16)
            } catch (e: Exception) {
                0
            }

            while (offset + 8 <= hexString.length) {
                val wordHex = hexString.substring(offset, offset + 8)
                val high16Hex = wordHex.substring(0, 4)
                val low16Hex = wordHex.substring(4, 8)

                try {
                    // First char from low 16-bit
                    var charCode = Integer.parseInt(low16Hex, 16)
                    if (isPrintableChar(charCode)) {
                        result.append(charCode.toChar())
                        charCount++
                        if (length > 0 && charCount >= length) break
                    }

                    // Second char from high 16-bit
                    charCode = Integer.parseInt(high16Hex, 16)
                    if (isPrintableChar(charCode)) {
                        result.append(charCode.toChar())
                        charCount++
                        if (length > 0 && charCount >= length) break
                    }
                } catch (e: NumberFormatException) {
                    // Skip invalid
                }

                offset += 8
            }

            return result.toString()
        }

        private fun isPrintableChar(code: Int): Boolean {
            return code != 0 && (code in 32..126 || code in 0x4E00..0x9FFF || code in 0x3000..0x303F) // ASCII + CJK + punctuation
        }

    }
}
