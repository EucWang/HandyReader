package com.wxn.bookparser.parser.fb2

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.baseName
import com.anggrayudi.storage.file.extension
import com.wxn.base.bean.Book
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.exts.rawFile
import com.wxn.mobi.Fb2Parser
import com.wxn.mobi.data.model.MetaInfo
import java.io.File
import javax.inject.Inject

class Fb2FileParser @Inject constructor(val context: Context) : FileParser {

    override suspend fun parse(file: DocumentFile): Book? {
        val rawFile = file.rawFile(context)
        val title = file.baseName
        val path = file.uri.toString()
        val format = file.extension

        return innerParse(rawFile, title, path, format)
    }

    override suspend fun parse(cachedFile: CachedFile): Book? {
        val rawFile = cachedFile.rawFile
        val title = cachedFile.name.substringBeforeLast(".").trim()
        val path = cachedFile.uri.toString()
        val format = cachedFile.extension
        return innerParse(rawFile, title, path, format)
    }

    private suspend fun innerParse(rawFile: File?, title: String, uriPath: String, format: String): Book? {
        if (rawFile == null || !rawFile.isFile || !rawFile.exists() || !rawFile.canRead()) {
            return null
        }
        val path = rawFile.absolutePath

        val metaInfo: MetaInfo = Fb2Parser.getFb2Info(context, path) ?: return null

        return Book(
                title = metaInfo.title ?: title ?: "",
                author = metaInfo.author.orEmpty(),

                publisher = metaInfo.publisher.orEmpty(),
                description = metaInfo.description.orEmpty(),
                language = metaInfo.language.orEmpty(),
                review = metaInfo.review.orEmpty(),

                scrollIndex = 0,
                scrollOffset = 0,

                progress = 0f,
                filePath = uriPath,
                lastOpened = null,
                category = metaInfo.subject.orEmpty(),
                coverImage = metaInfo.coverPath.orEmpty(),
                fileType = format)
    }
}