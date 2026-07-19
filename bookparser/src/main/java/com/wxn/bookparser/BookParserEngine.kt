package com.wxn.bookparser

import com.wxn.mobi.inative.NativeLib

object BookParserEngine {

    fun retryLoad() = NativeLib.tryLoad()
}