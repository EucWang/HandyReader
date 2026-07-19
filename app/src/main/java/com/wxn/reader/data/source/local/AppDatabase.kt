package com.wxn.reader.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wxn.reader.data.dto.BookAnnotationEntity
import com.wxn.reader.data.dto.BookChapterEntity
import com.wxn.reader.data.dto.BookEntity
import com.wxn.reader.data.dto.BookReadingTimeEntity
import com.wxn.reader.data.dto.BookShelfEntity
import com.wxn.reader.data.dto.BookmarkEntity
import com.wxn.reader.data.dto.BookVocabularyEntity
import com.wxn.reader.data.dto.DictionaryCacheEntity
import com.wxn.reader.data.dto.DeletedBookEntity
import com.wxn.reader.data.dto.DownloadHistoryEntity
import com.wxn.reader.data.dto.FontEntity
import com.wxn.reader.data.dto.FontFileEntity
import com.wxn.reader.data.dto.NoteEntity
import com.wxn.reader.data.dto.OpdsBookMappingEntity
import com.wxn.reader.data.dto.OpdsCatalogEntity
import com.wxn.reader.data.dto.PerBookMetaEntity
import com.wxn.reader.data.dto.PerBookThemeOverrideEntity
import com.wxn.reader.data.dto.ReadBgEntity
import com.wxn.reader.data.dto.ReadingActiveEntity
import com.wxn.reader.data.dto.ShelfEntity
import com.wxn.reader.data.dto.SherpaModelEntity
import com.wxn.reader.data.dto.SherpaSpeakerEntity
import com.wxn.reader.data.dto.SyncEtagCacheEntity
import com.wxn.reader.data.dto.SyncQueueEntity
import com.wxn.reader.data.source.local.dao.AnnotationDao
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookShelfDao
import com.wxn.reader.data.source.local.dao.BookmarkDao
import com.wxn.reader.data.source.local.dao.ChapterDao
import com.wxn.reader.data.source.local.dao.BookVocabularyDao
import com.wxn.reader.data.source.local.dao.DictionaryCacheDao
import com.wxn.reader.data.source.local.dao.DownloadHistoryDao
import com.wxn.reader.data.source.local.dao.FontDao
import com.wxn.reader.data.source.local.dao.NoteDao
import com.wxn.reader.data.source.local.dao.ReadBgDao
import com.wxn.reader.data.source.local.dao.ReadingActivityDao
import com.wxn.reader.data.source.local.dao.ShelfDao
import com.wxn.reader.data.source.local.dao.SherpaModelDao
import com.wxn.reader.data.source.local.dao.OpdsBookMappingDao
import com.wxn.reader.data.source.local.dao.OpdsCatalogDao
import com.wxn.reader.data.source.local.dao.DeletedBookDao
import com.wxn.reader.data.source.local.dao.PerBookMetaDao
import com.wxn.reader.data.source.local.dao.PerBookThemeOverrideDao
import com.wxn.reader.data.source.local.dao.ReaderThemeConfigDao
import com.wxn.reader.data.source.local.dao.SherpaSpeakerDao
import com.wxn.reader.data.dto.ReaderThemeConfigEntity

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
        DownloadHistoryEntity::class,
        SherpaModelEntity::class,
        SherpaSpeakerEntity::class,
        FontEntity::class,
        FontFileEntity::class,
        DictionaryCacheEntity::class,
        BookVocabularyEntity::class,
        OpdsBookMappingEntity::class,
        OpdsCatalogEntity::class,
        DeletedBookEntity::class,
        ReaderThemeConfigEntity::class,
        // ★ v9 同步方案新增(一期 §2.2):一期建表,sync_queue/sync_etag_cache 不写入不读取
        SyncQueueEntity::class,
        SyncEtagCacheEntity::class,
        BookReadingTimeEntity::class,
        // ★ v11 per-book 阅读配置
        PerBookMetaEntity::class,
        PerBookThemeOverrideEntity::class,
    ],
    version = 11,
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

    abstract fun downloadHistoryDao() : DownloadHistoryDao

    abstract fun sherpaModelDao(): SherpaModelDao

    abstract fun sherpaSpeakerDao(): SherpaSpeakerDao

    abstract fun fontDao(): FontDao

    abstract fun dictionaryCacheDao(): DictionaryCacheDao

    abstract fun bookVocabularyDao(): BookVocabularyDao

    abstract fun opdsCatalogDao(): OpdsCatalogDao

    abstract fun opdsBookMappingDao(): OpdsBookMappingDao

    abstract fun deletedBookDao(): DeletedBookDao

    abstract fun readerThemeConfigDao(): ReaderThemeConfigDao

    // ★ v9 同步方案新增 DAO(一期建表,sync_queue/sync_etag_cache 不写入不读取)
    abstract fun syncQueueDao(): com.wxn.reader.data.source.local.dao.SyncQueueDao
    abstract fun syncEtagCacheDao(): com.wxn.reader.data.source.local.dao.SyncEtagCacheDao
    abstract fun bookReadingTimeDao(): com.wxn.reader.data.source.local.dao.BookReadingTimeDao

    // ★ v11 per-book 阅读配置(见设计方案 §Step 1.6)
    abstract fun perBookMetaDao(): PerBookMetaDao
    abstract fun perBookThemeOverrideDao(): PerBookThemeOverrideDao

    companion object {
        /** 音频类文件类型集合,用于区分阅读进度合并策略(阅读时长 vs 播放位置)。 */
        val AUDIO_FILE_TYPES = setOf("mp3", "m4a", "m4b", "aac")

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
                            "`version` INTEGER NOT NULL, " +
                            "`downloadedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )

                // 新增下载历史表
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `download_history` (
                        `fileId` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `fileType` TEXT NOT NULL,
                        `fileName` TEXT,
                        `localPath` TEXT NOT NULL,
                        `fileSize` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `errorMessage` TEXT,
                        `startedAt` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        `downloadedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`fileId`)
                    )"""
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_download_history_downloadedAt` ON `download_history` (`downloadedAt`)")

                //
                // 创建 sherpa_models 表
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sherpa_models` (
                        `name` TEXT NOT NULL PRIMARY KEY,
                        `url` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `locale` TEXT NOT NULL,
                        `size` TEXT NOT NULL,
                        `processSpeed` REAL NOT NULL,
                        `quality` REAL NOT NULL,
                        `speakersNum` INTEGER NOT NULL,
                        
                        `baseDatas` TEXT NOT NULL,
                        
                        `localPath` TEXT,
                        `downloadedAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        
                        `license` TEXT,
                        `licenseUrl` TEXT,
                        `remark` TEXT
                    )"""
                )
                // 创建 sherpa_models 索引
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_sherpa_model_locale` ON `sherpa_models` (`locale`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_sherpa_model_type` ON `sherpa_models` (`type`)"
                )
                // 创建 sherpa_speakers 表
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sherpa_speakers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `index` INTEGER NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `speakerName` TEXT NOT NULL,
                        `gender` TEXT NOT NULL,
                        `locale` TEXT NOT NULL,
                        `sampleVoice` TEXT NOT NULL,
                        `active` INTEGER NOT NULL,
                        FOREIGN KEY(`modelName`) REFERENCES `sherpa_models`(`name`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                // 创建 sherpa_speakers 索引
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_speaker_model_name` ON `sherpa_speakers` (`modelName`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_speaker_model_speaker` ON `sherpa_speakers` (`modelName`, `speakerName`)"
                )
            }
        }

        val Migration_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `fonts` (
                        `id` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `source` TEXT NOT NULL DEFAULT 'download',
                        `category` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `dirName` TEXT NOT NULL,
                        `localDir` TEXT,
                        `downloadedAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )"""
                )

                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `font_files` (
                        `id` TEXT NOT NULL,
                        `fontId` TEXT NOT NULL,
                        `variant` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `localFileName` TEXT NOT NULL,
                        `localPath` TEXT,
                        `downloadedAt` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`fontId`) REFERENCES `fonts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )

                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_font_files_fontId` ON `font_files` (`fontId`)"
                )
            }
        }

        val Migration_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                //为笔记增加时间数据
                database.execSQL(
                    "ALTER TABLE notes ADD COLUMN createdAt INTEGER DEFAULT NULL"
                )

                //创建查词缓存表
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `dictionary_cache` (
                        `word` TEXT NOT NULL,
                        `lang` TEXT NOT NULL,
                        `dataJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`word`, `lang`)
                    )"""
                )

                //创建查词缓存索引表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `book_vocabulary` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        `lang` TEXT NOT NULL,
                        `word` TEXT NOT NULL,
                        `status` INTEGER NOT NULL DEFAULT 0,
                        `sentenceText` TEXT NOT NULL DEFAULT '',
                        `chapterIndex` INTEGER NOT NULL DEFAULT 0,
                        `startParagraphIndex` INTEGER NOT NULL DEFAULT 0,
                        `startTextOffset` INTEGER NOT NULL DEFAULT 0,
                        `locator` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`)
                            ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_book_vocabulary_bookId` ON `book_vocabulary` (`bookId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_book_vocabulary_word_lang` ON `book_vocabulary` (`word`, `lang`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_book_vocabulary_createdAt` ON `book_vocabulary` (`createdAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_book_vocabulary_status` ON `book_vocabulary` (`status`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_vocabulary_unique` ON `book_vocabulary` (`bookId`, `word`, `lang`, `chapterIndex`, `startParagraphIndex`, `startTextOffset`)")
            }
        }

        val Migration_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `opds_catalogs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `description` TEXT,
                        `iconUrl` TEXT,
                        `searchUrl` TEXT,
                        `supportsSearch` INTEGER NOT NULL DEFAULT 0,
                        `authType` TEXT NOT NULL DEFAULT 'NONE',
                        `isPredefined` INTEGER NOT NULL DEFAULT 0,
                        `predefinedId` TEXT,
                        `language` TEXT,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        `lastAccessedAt` INTEGER,
                        `lastSyncAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_opds_catalogs_predefinedId` ON `opds_catalogs` (`predefinedId`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_opds_catalogs_sortOrder` ON `opds_catalogs` (`sortOrder`)"
                )

                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `opds_book_mapping` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `remoteUrl` TEXT NOT NULL,
                        `catalogId` INTEGER NOT NULL,
                        `bookId` INTEGER NOT NULL,
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`)
                            ON DELETE CASCADE ON UPDATE NO ACTION
                    )"""
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_opds_book_mapping_remoteUrl_catalogId` ON `opds_book_mapping` (`remoteUrl`, `catalogId`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_opds_book_mapping_bookId` ON `opds_book_mapping` (`bookId`)"
                )

                database.execSQL("ALTER TABLE books ADD COLUMN source TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE books ADD COLUMN documentId TEXT")

                database.execSQL("CREATE INDEX IF NOT EXISTS `index_books_uri` ON `books` (`uri`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_books_deleted_importStatus` ON `books` (`deleted`, `importStatus`)")

                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_documentId` ON `books` (`documentId`)")

                database.execSQL("CREATE TABLE IF NOT EXISTS `deleted_books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `documentId` TEXT NOT NULL, `scanDirectoryUri` TEXT NOT NULL, `fileName` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_deleted_books_scanDirectoryUri` ON `deleted_books` (`scanDirectoryUri`)")

            }
        }

        val Migration_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE books ADD COLUMN favoriteDate INTEGER")
                database.execSQL(
                    "UPDATE books SET favoriteDate = ${System.currentTimeMillis()} " +
                        "WHERE isFavorite = 1 AND favoriteDate IS NULL"
                )

                /**
                 * 新增 reader_theme_configs 表（阅读主题存档，每主题一行，至多 9 行）。
                 * 对应 [ReaderThemeConfigEntity]。字段类型必须与 Room 生成的 schema 完全一致，否则启动校验抛异常。
                 * 不支持降级。
                 */
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reader_theme_configs` (" +
                        "`themeId` TEXT NOT NULL, " +
                        "`backgroundColor` INTEGER NOT NULL, " +
                        "`textColor` INTEGER NOT NULL, " +
                        "`backgroundImage` TEXT NOT NULL, " +
                        "`font` TEXT NOT NULL, " +
                        "`fontVariant` TEXT NOT NULL, " +
                        "`fontSize` REAL NOT NULL, " +
                        "`lineHeight` REAL NOT NULL, " +
                        "`letterSpacing` REAL NOT NULL, " +
                        "`paragraphIndent` REAL NOT NULL, " +
                        "`paragraphSpacing` REAL NOT NULL, " +
                        "`pageHorizontalMargins` REAL NOT NULL, " +
                        "`pageVerticalMargins` REAL NOT NULL, " +
                        "`titleSize` REAL NOT NULL, " +
                        "`titleTopSpacing` REAL NOT NULL, " +
                        "`titleBottomSpacing` REAL NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`themeId`))"
                )
            }
        }

        /*** v7→v8：chapters 表新增 type（章节类型）和 splitSeq（切分序号）列 */
        val Migration_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chapters ADD COLUMN type INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE chapters ADD COLUMN splitSeq INTEGER NOT NULL DEFAULT -1")
            }
        }

        /**
         * ★ 同步方案 §2.2 一期 v8→v9 Migration。
         *
         * 改动范围(对应 Entity 字段变更):
         *  1. books:加 contentHash/partialMd5 + 9 HLC(meta/user/reading 各 3 列)。
         *  2. notes/annotations/bookmarks/shelves/book_shelf:加 uuid + deleted + 6 HLC。
         *  3. book_vocabulary:加 uuid + 6 HLC(★ v1.3 严重-4:不加 deleted 列,软删走既有 status=-1)。
         *  4. reading_activities:重建为复合 PK (date, deviceId) + 回填 deviceId。
         *  5. 新增 sync_queue / sync_etag_cache / book_reading_time 三张表。
         *  6. uuid 回填(§2.2.2,Kotlin UUID.randomUUID,与运行时同源)。
         *  7. contentHash 去重前置 + UNIQUE 部分索引(§2.2.3)。
         *
         * @param localDeviceId 本机 UUID(由 [com.wxn.reader.data.source.local.DeviceLocalStore.getOrCreateLocalDeviceId] 提供,
         *     Migration 内不可走 Hilt,故由 provideAppDatabase 传入字符串)。
         */
        fun createMigration8To9(localDeviceId: String): Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                require(localDeviceId.isNotBlank()) { "Migration_8_9 needs non-blank localDeviceId" }

                // ── (1) books:加 contentHash/partialMd5 + 9 HLC ──
                db.execSQL("ALTER TABLE books ADD COLUMN contentHash TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN partialMd5 TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN metaHlcL INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN metaHlcC INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN metaHlcDevice TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE books ADD COLUMN userHlcL INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN userHlcC INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN userHlcDevice TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE books ADD COLUMN syncHlcL INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN syncHlcC INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN syncHlcDevice TEXT NOT NULL DEFAULT ''")

                // ── (2) notes/annotations/bookmarks/shelves/book_shelf:加 uuid + deleted + 6 HLC ──
                addUuidDeletedHlcColumns(db, "notes")
                addUuidDeletedHlcColumns(db, "annotations")
                addUuidDeletedHlcColumns(db, "bookmarks")
                addUuidDeletedHlcColumns(db, "shelves")
                addUuidDeletedHlcColumns(db, "book_shelf")

                // ── (3) book_vocabulary:加 uuid + 6 HLC(不加 deleted 列,严重-4)──
                db.execSQL("ALTER TABLE book_vocabulary ADD COLUMN uuid TEXT")
                db.execSQL("ALTER TABLE book_vocabulary ADD COLUMN syncHlcL INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_vocabulary ADD COLUMN syncHlcC INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_vocabulary ADD COLUMN syncHlcDevice TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE book_vocabulary ADD COLUMN deletedHlcL INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_vocabulary ADD COLUMN deletedHlcC INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE book_vocabulary ADD COLUMN deletedHlcDevice TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_book_vocabulary_uuid` ON `book_vocabulary` (`uuid`)")

                // ── (4) reading_activities:重建为复合 PK (date, deviceId) ──
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `reading_activities_new` (
                        `date` INTEGER NOT NULL,
                        `deviceId` TEXT NOT NULL,
                        `readingTime` INTEGER NOT NULL,
                        PRIMARY KEY(`date`, `deviceId`)
                    )"""
                )
                // 拷贝老行,deviceId 回填 = 本机 UUID(§2.2.4)
                db.execSQL(
                    "INSERT INTO reading_activities_new(date, deviceId, readingTime) " +
                        "SELECT date, '$localDeviceId', readingTime FROM reading_activities"
                )
                db.execSQL("DROP TABLE reading_activities")
                db.execSQL("ALTER TABLE reading_activities_new RENAME TO reading_activities")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_activities_deviceId` ON `reading_activities` (`deviceId`)")

                // ── (5) 新增 sync_queue / sync_etag_cache / book_reading_time 三张表 ──
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sync_queue` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `stableId` TEXT NOT NULL,
                        `scope` TEXT NOT NULL,
                        `op` TEXT NOT NULL,
                        `queuedAt` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_queue_stableId_scope_op` " +
                        "ON `sync_queue` (`stableId`, `scope`, `op`)"
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sync_etag_cache` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `resourceKey` TEXT NOT NULL,
                        `etag` TEXT,
                        `lastChecked` INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_etag_cache_resourceKey` " +
                        "ON `sync_etag_cache` (`resourceKey`)"
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `book_reading_time` (
                        `bookId` INTEGER NOT NULL,
                        `deviceId` TEXT NOT NULL,
                        `readingTimeMs` INTEGER NOT NULL,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`bookId`, `deviceId`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_reading_time_bookId` ON `book_reading_time` (`bookId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_reading_time_deviceId` ON `book_reading_time` (`deviceId`)")

                // ── (6) uuid 回填(§2.2.2,Kotlin UUID,与运行时同源 8-4-4-4-12 带连字符)──
                backfillUuid(db, "notes")
                backfillUuid(db, "annotations")
                backfillUuid(db, "bookmarks")
                backfillUuid(db, "shelves")
                backfillUuid(db, "book_shelf")
                backfillUuid(db, "book_vocabulary")

                // ── (7) contentHash 去重前置 + 普通索引(非唯一)──
                // ★ 不用 UNIQUE 索引:OnConflictStrategy.REPLACE 在 UNIQUE 约束下会触发 DELETE + CASCADE
                //   级联删除 notes/annotations/bookmarks 等子表数据。去重完全交给应用层 dedupeByHash。
                // ★ 先 DROP 旧 UNIQUE 索引(开发期遗留名 index_books_content_hash),确保不会被
                //   Room 自动生成的 index_books_contentHash 名差异蒙蔽导致旧索引残留。
                dedupContentHash(db)
                db.execSQL("DROP INDEX IF EXISTS `index_books_content_hash`")
                db.execSQL("DROP INDEX IF EXISTS `index_books_contentHash`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_books_contentHash` " +
                        "ON `books`(`contentHash`)"
                )
            }

            /**
             * 给指定表添加 uuid + deleted + 6 HLC 列(books/shelves/book_shelf/notes/annotations/bookmarks 共用)。
             *
             * ★ 必须同时建 `index_<table>_uuid` UNIQUE 索引:Entity 在 `indices` 里声明了
             *   `Index(value = ["uuid"], unique = true)`,Room 迁移后 schema 校验会比对索引名,
             *   名字不一致或缺失都会抛 `Migration didn't properly handle`。
             *   建索引在 uuid 回填([backfillUuid])之前执行是安全的:列此时全为 NULL,
             *   SQLite 允许多个 NULL 共存于 UNIQUE 索引;回填写入的 UUID 值互不相同。
             */
            private fun addUuidDeletedHlcColumns(db: SupportSQLiteDatabase, table: String) {
                db.execSQL("ALTER TABLE $table ADD COLUMN uuid TEXT")
                db.execSQL("ALTER TABLE $table ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $table ADD COLUMN syncHlcL INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $table ADD COLUMN syncHlcC INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $table ADD COLUMN syncHlcDevice TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE $table ADD COLUMN deletedHlcL INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $table ADD COLUMN deletedHlcC INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $table ADD COLUMN deletedHlcDevice TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uuid` ON `$table` (`uuid`)")
            }

            /**
             * uuid 回填:用 Kotlin `UUID.randomUUID().toString()`(8-4-4-4-12 带连字符),
             * 与运行时生成同源;不可用 SQL `randomblob`(无连字符,跨设备合并永不匹配)。
             */
            private fun backfillUuid(db: SupportSQLiteDatabase, table: String) {
                val (pkExpr, isComposite) = when (table) {
                    "book_shelf" -> "bookId || '_' || shelfId" to true
                    else -> "id" to false
                }
                val rows = db.query("SELECT $pkExpr AS pk FROM $table WHERE uuid IS NULL").use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0)) }
                }
                rows.forEach { pk ->
                    val uuid = java.util.UUID.randomUUID().toString()
                    if (isComposite) {
                        val (bookId, shelfId) = pk.split("_")
                        db.execSQL(
                            "UPDATE book_shelf SET uuid = ? WHERE bookId = ? AND shelfId = ?",
                            arrayOf<Any>(uuid, bookId.toLong(), shelfId.toLong())
                        )
                    } else {
                        db.execSQL(
                            "UPDATE $table SET uuid = ? WHERE id = ?",
                            arrayOf<Any>(uuid, pk.toLong())
                        )
                    }
                }
            }

            /**
             * contentHash 去重:同 contentHash 的多行保留 lastOpened 最新,其余软删(deleted=1)。
             * 仅对 contentHash IS NOT NULL 的行生效;NULL 行(老书未补算)由后续 EnsureContentHashWorker 补算时去重。
             */
            private fun dedupContentHash(db: SupportSQLiteDatabase) {
                val dupes = db.query(
                    "SELECT contentHash FROM books WHERE contentHash IS NOT NULL AND deleted = 0 " +
                        "GROUP BY contentHash HAVING COUNT(*) > 1"
                ).use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0)) }
                }
                dupes.forEach { hash ->
                    db.execSQL(
                        "UPDATE books SET deleted = 1 WHERE contentHash = ? AND id NOT IN (" +
                            "SELECT id FROM books WHERE contentHash = ? AND deleted = 0 " +
                            "ORDER BY lastOpened DESC LIMIT 1)",
                        arrayOf<Any>(hash, hash)
                    )
                }
            }
        }

        /**
         * v9→v10:backfill `book_reading_time` 表,
         * 将 `books.readingTime` 存量值按 deviceId 写入 per-device 行(非音频书)。
         * 音频书(阅读时长 = 播放进度)不参与拆分,保持 `books.readingTime` 原值。
         */
        fun createMigration9To10(localDeviceId: String): Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO book_reading_time(bookId, deviceId, readingTimeMs, lastUpdated)
                    SELECT id, ?, readingTime, ?
                    FROM books
                    WHERE readingTime > 0
                      AND fileType NOT IN ('mp3', 'm4a', 'm4b', 'aac')
                    """.trimIndent(),
                    arrayOf<Any>(localDeviceId, System.currentTimeMillis())
                )
            }
        }

        /**
         * ★ A+++ 严重-5:合并 v7→v8→v9→v10 为单一 Migration(本期未发布到线上,v7 是线上版本)。
         *
         * 实现策略:复用三个已定义的 Migration 的 [Migration.migrate] 方法,零代码重复。
         * 旧的三个 Migration 定义保留(不删),仅从 migration 链中移除注册。
         */
        fun createMigration7To10(localDeviceId: String): Migration = object : Migration(7, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ── (1) v7→v8:chapters 表加 type / splitSeq ──
                Migration_7_8.migrate(db)
                // ── (2) v8→v9:books contentHash + HLC + uuid 回填 + UNIQUE 索引 + 新表 ──
                createMigration8To9(localDeviceId).migrate(db)
                // ── (3) v9→v10:backfill book_reading_time ──
                createMigration9To10(localDeviceId).migrate(db)
            }
        }

        /**
         * ★ v11 per-book 阅读配置 Migration(见设计方案 §二.0 / §二.1 / §二.2)。
         *
         * 改动范围:
         *  1. reader_theme_configs 加 userTextAlign / forceAlignOverride 两列(对齐字段纳入归档管控,§二.0)。
         *  2. 新增 per_book_meta 表(§二.1)。
         *  3. 新增 per_book_theme_overrides 表(§二.2,17 delta 列 + 2 时间戳)。
         *
         * **DEFAULT 取值依据**(设计方案 §二.0 R2 ❶):userTextAlign DEFAULT **4**(Justify),
         * 与 ReaderPreferencesUtil.defaultPreferences 的运行时实际默认值对齐(ReaderPreferences.kt:38
         * 字段默认 1 只是构造兜底,运行时几乎不走到)。若误用 DEFAULT 1,存量 archive 行迁移后切主题
         * loadTarget 读到 1,会把用户对齐静默篡改为 Left。forceAlignOverride DEFAULT 0(false)无误。
         *
         * **不提供降级迁移**(Migration_11_10):项目发布策略为"只前进修复",降级迁移只在"应用版本回退"
         * 时触发,无法在"10→11 迁移执行失败"时救命。项目无 fallbackToDestructiveMigration,
         * 防护靠写对 SQL + MigrationTestHelper 校验测试。
         */
        val Migration_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ── (1) reader_theme_configs 加对齐两列(§二.0) ──
                // userTextAlign DEFAULT 4 = Justify(R2 ❶:误用 1 会静默篡改存量用户对齐)
                db.execSQL("ALTER TABLE reader_theme_configs ADD COLUMN userTextAlign INTEGER NOT NULL DEFAULT 4")
                db.execSQL("ALTER TABLE reader_theme_configs ADD COLUMN forceAlignOverride INTEGER NOT NULL DEFAULT 0")

                // ── (2) per_book_meta 表(§二.1) ──
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `per_book_meta` (
                        `bookId` INTEGER NOT NULL,
                        `overrideEnabled` INTEGER NOT NULL DEFAULT 0,
                        `selectedThemeId` TEXT,
                        `readerThemeMode` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`bookId`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // ── (3) per_book_theme_overrides 表(全量快照结构，17 字段 NOT NULL + DEFAULT)──
                // per-book 功能未上线，直接建 v12 全量快照结构（合并原 v11 nullable + v12 NOT NULL 两步）
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `per_book_theme_overrides` (
                        `bookId` INTEGER NOT NULL,
                        `themeId` TEXT NOT NULL,
                        `fontSize` REAL NOT NULL DEFAULT 1.0,
                        `lineHeight` REAL NOT NULL DEFAULT 1.5,
                        `letterSpacing` REAL NOT NULL DEFAULT 0.0,
                        `pageHorizontalMargins` REAL NOT NULL DEFAULT 1.5,
                        `pageVerticalMargins` REAL NOT NULL DEFAULT 1.2,
                        `paragraphIndent` REAL NOT NULL DEFAULT 2.0,
                        `paragraphSpacing` REAL NOT NULL DEFAULT 0.6,
                        `textColor` INTEGER NOT NULL DEFAULT -13882324,
                        `backgroundColor` INTEGER NOT NULL DEFAULT -328969,
                        `backgroundImage` TEXT NOT NULL DEFAULT '',
                        `font` TEXT NOT NULL DEFAULT 'sans_serif',
                        `fontVariant` TEXT NOT NULL DEFAULT 'regular',
                        `titleSize` REAL NOT NULL DEFAULT 1.0,
                        `titleTopSpacing` REAL NOT NULL DEFAULT 18.0,
                        `titleBottomSpacing` REAL NOT NULL DEFAULT 15.0,
                        `userTextAlign` INTEGER NOT NULL DEFAULT 4,
                        `forceAlignOverride` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`bookId`, `themeId`),
                        FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_per_book_theme_overrides_bookId` ON `per_book_theme_overrides`(`bookId`)")

                db.execSQL("ALTER TABLE books ADD COLUMN txtCharset TEXT")
            }
        }
    }
}
