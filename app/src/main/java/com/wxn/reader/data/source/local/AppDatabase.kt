package com.wxn.reader.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wxn.reader.data.dto.BookAnnotationEntity
import com.wxn.reader.data.dto.BookChapterEntity
import com.wxn.reader.data.dto.BookEntity
import com.wxn.reader.data.dto.BookShelfEntity
import com.wxn.reader.data.dto.BookmarkEntity
import com.wxn.reader.data.dto.NoteEntity
import com.wxn.reader.data.dto.ReadBgEntity
import com.wxn.reader.data.dto.ReadingActiveEntity
import com.wxn.reader.data.dto.ShelfEntity
import com.wxn.reader.data.source.local.dao.AnnotationDao
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookShelfDao
import com.wxn.reader.data.source.local.dao.BookmarkDao
import com.wxn.reader.data.source.local.dao.ChapterDao
import com.wxn.reader.data.source.local.dao.NoteDao
import com.wxn.reader.data.source.local.dao.ReadBgDao
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
        BookChapterEntity::class,
        ReadBgEntity::class,
    ],
    version = 3,
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

    abstract fun readBgDao(): ReadBgDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE books ADD COLUMN importStatus INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val Migration_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `read_bgs` " +
                            "(`id` TEXT NOT NULL, " +
                            "`localPath` TEXT NOT NULL, " +
                            "`thumbnailPath` TEXT NOT NULL, " +
                            "`remoteImageUrl` TEXT NOT NULL, " +
                            "`isDownloaded` INTEGER NOT NULL, " +
                            "`downloadedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
            }
        }
    }
}
