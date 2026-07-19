package com.wxn.bookparser.parser.txt

import com.wxn.base.util.Logger
import org.mozilla.universalchardet.UniversalDetector
import javax.inject.Inject
import javax.inject.Singleton

private const val CHARSET_TAG = "CharsetDetector"

@Singleton
class JuniversalCharsetDetector @Inject constructor() : TxtCharsetDetector {

    override fun detect(bytes: ByteArray): CharsetDetectionResult {
        val bomResult = detectBom(bytes)
        if (bomResult != null) return bomResult

        val detector = UniversalDetector(null)
        detector.handleData(bytes, 0, bytes.size)
        detector.dataEnd()

        val detected = detector.detectedCharset
        detector.reset()

        if (detected != null) {
            val isUtf16Or32 = detected.startsWith("UTF-16") ||
                    detected == "UTF-32BE" || detected == "UTF-32LE"
            return CharsetDetectionResult(detected, isUtf16Or32)
        }

        Logger.w("$CHARSET_TAG: juniversalchardet returned null, falling back to UTF-8")
        return CharsetDetectionResult("UTF-8", isUtf16Or32 = false)
    }

    private fun detectBom(bytes: ByteArray): CharsetDetectionResult? {
        if (bytes.size < 2) return null

        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF

        if (b0 == 0xEF && b1 == 0xBB && bytes.size >= 3 && (bytes[2].toInt() and 0xFF) == 0xBF) {
            return CharsetDetectionResult("UTF-8", isUtf16Or32 = false)
        }
        if (b0 == 0xFE && b1 == 0xFF) {
            return CharsetDetectionResult("UTF-16BE", isUtf16Or32 = true)
        }
        if (b0 == 0xFF && b1 == 0xFE) {
            if (bytes.size >= 4 && (bytes[2].toInt() and 0xFF) == 0x00 && (bytes[3].toInt() and 0xFF) == 0x00) {
                return CharsetDetectionResult("UTF-32LE", isUtf16Or32 = true)
            }
            return CharsetDetectionResult("UTF-16LE", isUtf16Or32 = true)
        }
        if (b0 == 0x00 && b1 == 0x00 && bytes.size >= 4 &&
            (bytes[2].toInt() and 0xFF) == 0xFE && (bytes[3].toInt() and 0xFF) == 0xFF) {
            return CharsetDetectionResult("UTF-32BE", isUtf16Or32 = true)
        }

        return null
    }
}
