package com.wxn.bookparser

import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.domain.reader.ReaderText


/****
 * 接口，提供对底层不同格式书籍的文件进行解析的统一接口
 */
interface TextParser {

    /****
     * 解析文件，得到对应文件的内容的列表
     * ReaderText 是一个展示数据的一个封装
     */
    suspend fun parse(cachedFile: CachedFile): List<ReaderText>
}