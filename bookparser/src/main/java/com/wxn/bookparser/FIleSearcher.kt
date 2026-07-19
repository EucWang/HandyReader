package com.wxn.bookparser

import com.wxn.mobi.FileSearcher

object FileSearcher {

    fun search(path: String?): List<String> {
        if (path.isNullOrEmpty()) {
            return emptyList<String>()
        }
        return FileSearcher.search(path)
    }
}