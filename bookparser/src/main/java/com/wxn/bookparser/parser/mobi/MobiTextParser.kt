package com.wxn.bookparser.parser.mobi

import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.domain.reader.ReaderText
import com.wxn.bookparser.parser.base.DocumentParser
import javax.inject.Inject

class MobiTextParser @Inject constructor(
    private val documentParser: DocumentParser
) : TextParser  {
    override suspend fun parse(cachedFile: CachedFile): List<ReaderText> {
        //TODO
        return emptyList()
    }
}