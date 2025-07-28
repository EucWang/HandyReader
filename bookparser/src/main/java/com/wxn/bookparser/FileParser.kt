package com.wxn.bookparser

import androidx.documentfile.provider.DocumentFile
import com.wxn.base.bean.Book
import com.wxn.bookparser.domain.file.CachedFile

/****
 * 对文件进行解析，得到带封面的书籍数据
 */
interface FileParser {

    suspend fun parse(cachedFile: CachedFile): Book?

    suspend fun parse(file: DocumentFile) : Book?
}

