package com.wxn.bookparser

import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssInfo
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.base.bean.ReaderText


/****
 * 接口，提供对底层不同格式书籍的文件进行解析的统一接口
 */
interface TextParser {

    /****
     * 解析文件，得到对应文件的内容的列表
     * ReaderText 是一个展示数据的一个封装
     */
//    suspend fun parse(bookId: Long, cachedFile: CachedFile): List<ReaderText>

    /***
     * 解析得到章节列表
     */
    suspend fun parseChapterInfo(bookId: Long, cachedFile: CachedFile): List<BookChapter>

    /***
     * 解析得到给定章节数据
     */
    suspend fun parsedChapterData(bookId: Long, cachedFile: CachedFile, chapter: BookChapter) : List<ReaderText>

    suspend fun parseCss(bookId: Long, cachedFile: CachedFile, cssNames: List<String>, tagNames: List<String>, ids: List<String>): List<CssInfo>

    suspend fun getWordCount(bookId:Long, cachedFile: CachedFile): List<Triple<Int, Int, Int>>

    suspend fun close(bookId:Long, cachedFile: CachedFile)
}