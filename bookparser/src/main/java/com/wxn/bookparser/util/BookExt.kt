package com.wxn.bookparser.util

import com.wxn.base.bean.Book
import com.wxn.mobi.data.model.MetaInfo

fun fromMetaInfoToBook(metaInfo: MetaInfo, defaultTitle: String?, bookPath: String, format: String) =
    Book(
        title = metaInfo.title ?: defaultTitle ?: "",
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
    )
