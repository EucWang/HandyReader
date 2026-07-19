package com.wxn.mobi

import com.wxn.base.util.supportedExtensions
import com.wxn.mobi.inative.NativeLib

object FileSearcher {

    /***
     * 阻塞的扫描给定目录下的所有文件，遍历是否有支持的书籍目录并返回
     */
    fun search(path: String) : List<String> {
        val allowedExtensions = supportedExtensions().toTypedArray()
        val retVal = NativeLib.searchFiles(path, allowedExtensions)
        return if (retVal.isNullOrEmpty()) {
            emptyList()
        } else {
            retVal.toList()
        }
    }
}