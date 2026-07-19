package com.wxn.bookparser.util

import com.wxn.base.bean.Book
import com.wxn.mobi.data.model.MetaInfo

fun fromMetaInfoToBook(metaInfo: MetaInfo, defaultTitle: String?, bookPath: String, format: String) =
    Book(
        title = metaInfo.title.ifEmpty { defaultTitle?:"" },
        author = metaInfo.author.orEmpty(),

        publisher = metaInfo.publisher.orEmpty(),
        description = metaInfo.description.orEmpty(),
        language = metaInfo.language.orEmpty(),
        review = metaInfo.review.orEmpty(),

        scrollIndex = 0,
        scrollOffset = 0,

        progress = 0f,
        filePath = bookPath,
        lastOpened = null,
        category = metaInfo.subject.orEmpty(),
        coverImage = metaInfo.coverPath.orEmpty(),
        fileType = format,
        crc = metaInfo.crc,
        // ★ 方案 A+:MOBI/EPUB/FB2 由 native 层在解析时与 CRC 合并算填,
        //   FileParserImpl 看到 contentHash 非空会跳过 Java 层的 SHA-256 二次计算
        contentHash = metaInfo.contentHash.ifBlank { null },
    )
