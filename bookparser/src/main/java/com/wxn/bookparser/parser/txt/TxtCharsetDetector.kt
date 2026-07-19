package com.wxn.bookparser.parser.txt

data class CharsetDetectionResult(
    val charsetName: String,
    val isUtf16Or32: Boolean
)

interface TxtCharsetDetector {
    fun detect(bytes: ByteArray): CharsetDetectionResult
}
