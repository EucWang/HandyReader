package com.wxn.bookparser

import com.wxn.bookparser.domain.book.BookWithCover
import com.wxn.bookparser.domain.file.CachedFile

/****
 * 对文件进行解析，得到带封面的书籍数据
 */
interface FileParser {

    suspend fun parse(cachedFile: CachedFile): BookWithCover?
}