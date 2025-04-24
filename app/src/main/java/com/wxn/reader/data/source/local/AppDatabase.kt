package com.wxn.reader.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wxn.reader.data.model.Book
import com.wxn.reader.data.model.BookAnnotation
import com.wxn.reader.data.model.BookShelf
import com.wxn.reader.data.model.Bookmark
import com.wxn.reader.data.model.Note
import com.wxn.reader.data.model.ReadingActivity
import com.wxn.reader.data.model.Shelf
import com.wxn.reader.data.source.local.dao.AnnotationDao
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookShelfDao
import com.wxn.reader.data.source.local.dao.BookmarkDao
import com.wxn.reader.data.source.local.dao.NoteDao
import com.wxn.reader.data.source.local.dao.ReadingActivityDao
import com.wxn.reader.data.source.local.dao.ShelfDao

@Database(
    entities = [
        Book::class,
        BookAnnotation::class,
        Note::class,
        Bookmark::class,
        Shelf::class,
        BookShelf::class,
        ReadingActivity::class
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun noteDao(): NoteDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun shelfDao(): ShelfDao
    abstract fun bookShelfDao(): BookShelfDao
    abstract fun readingActivityDao(): ReadingActivityDao
}
