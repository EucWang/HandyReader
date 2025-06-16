package com.wxn.reader.data.mapper.book

import com.wxn.base.bean.Book
import com.wxn.reader.data.dto.BookEntity
import com.wxn.reader.data.dto.ReadingStatus.Companion.intToReadStatus

import javax.inject.Inject

class BookMapperImpl @Inject constructor() : BookMapper {
    override suspend fun toBookEntity(book: Book): BookEntity {
        return BookEntity(
            id = book.id,
            uri = book.filePath,
//            fileType = stringToFileType(book.fileType),
            fileType = book.fileType,

            title = book.title,
            authors = book.author,
            description = book.description,

            publishDate = book.publishDate,
            publisher = book.publisher,
            language = book.language,
            numberOfPages = book.numberOfPages,
            wordCount = book.wordCount,

            subjects = book.category.toString(),
            coverPath = book.coverImage?.toString(),
            locator = book.locator,

            progression = book.progress,
            lastOpened = book.lastOpened,
            deleted = book.deleted,

            rating = book.rating,
            isFavorite = book.isFavorite,

            readingStatus = intToReadStatus(book.readingStatus ?: 0),
            readingTime = book.readingTime,
            startReadingDate = book.startReadingDate,
            endReadingDate = book.endReadingDate,

            review = book.review,
            duration = book.duration,
            narrator = book.narrator,

            scrollIndex = book.scrollIndex,
            scrollOffset = book.scrollOffset,

            crc = book.crc,
            cachedDir = book.cachedDir.orEmpty()

        )
    }

    override suspend fun toBook(bookEntity: BookEntity): Book {
        return Book(
            id = bookEntity.id,
            title = bookEntity.title,
            author = bookEntity.authors,

            description = bookEntity.description,
            scrollIndex = bookEntity.scrollIndex,
            scrollOffset = bookEntity.scrollOffset,

            progress = bookEntity.progression,
            filePath = bookEntity.uri,
            lastOpened = bookEntity.lastOpened,

            category = bookEntity.subjects.orEmpty(),
            coverImage = bookEntity.coverPath,

            fileType = bookEntity.fileType,

            publishDate = bookEntity.publishDate,
            publisher = bookEntity.publisher,
            language = bookEntity.language,

            numberOfPages = bookEntity.numberOfPages,
            wordCount = bookEntity.wordCount,
            locator = bookEntity.locator,
            deleted = bookEntity.deleted,

            rating = bookEntity.rating,
            isFavorite = bookEntity.isFavorite,
            readingStatus = bookEntity.readingStatus?.ordinal,

            readingTime = bookEntity.readingTime,
            startReadingDate = bookEntity.startReadingDate,
            endReadingDate = bookEntity.endReadingDate,

            review = bookEntity.review,
            duration = bookEntity.duration,
            narrator = bookEntity.narrator,

            crc = bookEntity.crc,
            cachedDir = bookEntity.cachedDir
        )
    }
}