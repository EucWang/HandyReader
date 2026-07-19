package com.wxn.bookparser.parser.txt

import java.io.InputStream

class CountingInputStream(private val inner: InputStream) : InputStream() {

    var count: Long = 0
        private set

    override fun read(): Int = inner.read().also { if (it >= 0) count++ }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        inner.read(b, off, len).also { n -> if (n > 0) count += n }

    override fun skip(n: Long): Long = inner.skip(n).also { count += it }

    override fun close() = inner.close()

    override fun available(): Int = inner.available()
}
