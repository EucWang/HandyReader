package com.wxn.reader.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wxn.reader.data.dto.BookAnnotationEntity
import com.wxn.reader.data.dto.BookChapterEntity
import com.wxn.reader.data.dto.BookEntity
import com.wxn.reader.data.dto.BookShelfEntity
import com.wxn.reader.data.dto.BookmarkEntity
import com.wxn.reader.data.dto.NoteEntity
import com.wxn.reader.data.dto.ReadingActiveEntity
import com.wxn.reader.data.dto.ShelfEntity
import com.wxn.reader.data.source.local.dao.AnnotationDao
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookShelfDao
import com.wxn.reader.data.source.local.dao.BookmarkDao
import com.wxn.reader.data.source.local.dao.ChapterDao
import com.wxn.reader.data.source.local.dao.NoteDao
import com.wxn.reader.data.source.local.dao.ReadingActivityDao
import com.wxn.reader.data.source.local.dao.ShelfDao

@Database(
    entities = [
        BookEntity::class,
        BookAnnotationEntity::class,
        NoteEntity::class,
        BookmarkEntity::class,
        ShelfEntity::class,
        BookShelfEntity::class,
        ReadingActiveEntity::class,
        BookChapterEntity::class
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
    abstract fun bookChapterDao(): ChapterDao
}
