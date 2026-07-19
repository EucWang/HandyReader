package com.wxn.bookparser.util

import com.wxn.mobi.data.model.MetaInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class BookExtCrcTest {

    @Test
    fun testFromMetaInfoToBook_mapsCrc() {
        val metaInfo = MetaInfo(
            title = "Test Book",
            author = "Author",
            crc = 99999
        )
        val book = fromMetaInfoToBook(metaInfo, "default", "/path/to/book.epub", "epub")
        assertEquals(99999, book.crc)
    }

    @Test
    fun testFromMetaInfoToBook_defaultCrc() {
        val metaInfo = MetaInfo(title = "Test", author = "Auth")
        val book = fromMetaInfoToBook(metaInfo, null, "/path/book.mobi", "mobi")
        assertEquals(0, book.crc)
    }

    @Test
    fun testFromMetaInfoToBook_titleFallback() {
        val metaInfo = MetaInfo(title = "", author = "Auth")
        val book = fromMetaInfoToBook(metaInfo, "Fallback Title", "/path/book.fb2", "fb2")
        assertEquals("Fallback Title", book.title)
    }

    @Test
    fun testFromMetaInfoToBook_allFieldMappings() {
        val metaInfo = MetaInfo(
            title = "Title",
            author = "Author",
            publisher = "Publisher",
            description = "Description",
            language = "en",
            review = "Review",
            subject = "Category",
            coverPath = "/cover.jpg",
            crc = 42
        )
        val book = fromMetaInfoToBook(metaInfo, null, "/path/book.epub", "epub")
        assertEquals("Title", book.title)
        assertEquals("Author", book.author)
        assertEquals("Publisher", book.publisher)
        assertEquals("Description", book.description)
        assertEquals("en", book.language)
        assertEquals("Review", book.review)
        assertEquals("Category", book.category)
        assertEquals("/cover.jpg", book.coverImage)
        assertEquals("epub", book.fileType)
        assertEquals("/path/book.epub", book.filePath)
        assertEquals(42, book.crc)
    }
}
