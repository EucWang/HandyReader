package com.wxn.reader.data.remote.sync.merge

import androidx.room.Room
import com.wxn.base.bean.sync.HlcTimestamp
import com.wxn.reader.data.backup.StableIdResolver
import com.wxn.reader.data.dto.BookEntity
import com.wxn.reader.data.dto.BookReadingTimeEntity
import com.wxn.reader.data.mapper.sync.SyncRecordMapper
import com.wxn.reader.data.remote.sync.canonical.BookReadingRecord
import com.wxn.reader.data.source.local.AppDatabase
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookReadingTimeDao
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [SyncMergeEngine.mergeAndWriteReading] 集成测试。
 *
 * 验证三件事:
 * 1. 阅读进度字段(progression/locator/lastOpened/HLC)不会被老的 local 快照覆盖。
 * 2. readingTime 派生值在文本类按 per-device SUM、在有声类按 LWW。
 * 3. 同备份重复还原不重复累计。
 *
 * 使用 in-memory Room + Robolectric,无需 Mock 框架。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [android.os.Build.VERSION_CODES.P])
class MergeAndWriteReadingTest {

    private lateinit var appDb: AppDatabase
    private lateinit var bookDao: BookDao
    private lateinit var readingTimeDao: BookReadingTimeDao
    private lateinit var engine: SyncMergeEngine

    private companion object {
        const val LOCAL_DEVICE = "local-device"
        const val REMOTE_DEVICE = "remote-device"
    }

    @Before
    fun setUp() {
        appDb = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).build()
        bookDao = appDb.bookDao()
        readingTimeDao = appDb.bookReadingTimeDao()
        val stableIdResolver = StableIdResolver()
        engine = SyncMergeEngine(
            appDb = appDb,
            bookDao = bookDao,
            annotationDao = appDb.annotationDao(),
            noteDao = appDb.noteDao(),
            bookmarkDao = appDb.bookmarkDao(),
            shelfDao = appDb.shelfDao(),
            bookShelfDao = appDb.bookShelfDao(),
            vocabularyDao = appDb.bookVocabularyDao(),
            readingActivityDao = appDb.readingActivityDao(),
            mapper = SyncRecordMapper(stableIdResolver),
            stableIdResolver = stableIdResolver,
        )
    }

    @After
    fun tearDown() {
        appDb.close()
    }

    // ============ 辅助方法 ============

    /** 插入一本带初始阅读进度和 readingTime 的书,返回 id。 */
    private suspend fun insertBook(
        fileType: String = "epub",
        progression: Float = 0.0f,
        locator: String = "",
        lastOpened: Long = 0L,
        readingTime: Long = 0L,
        syncHlcL: Long = 0L,
        syncHlcC: Int = 0,
        syncHlcDevice: String = "",
    ): Long {
        val entity = BookEntity(
            uri = "file:///test.$fileType",
            fileType = fileType,
            title = "Test",
            authors = "Author",
            description = null,
            publishDate = null,
            publisher = null,
            language = null,
            numberOfPages = null,
            wordCount = 0L,
            subjects = null,
            coverPath = null,
            locator = locator,
            progression = progression,
            lastOpened = lastOpened,
            readingTime = readingTime,
            syncHlcL = syncHlcL,
            syncHlcC = syncHlcC,
            syncHlcDevice = syncHlcDevice,
        )
        return bookDao.insertBook(entity)
    }

    private fun readingRecord(
        locator: String = "remote-locator",
        progression: Float = 0.5f,
        scrollIndex: Int = 1,
        scrollOffset: Int = 100,
        readingTime: Long = 5000L,
        lastOpened: Long = 2000L,
        hlcL: Long = 2000L,
        hlcC: Int = 0,
        hlcDevice: String = REMOTE_DEVICE,
    ) = BookReadingRecord(
        locator = locator,
        progression = progression,
        scrollIndex = scrollIndex,
        scrollOffset = scrollOffset,
        readingTime = readingTime,
        lastOpened = lastOpened,
        startReadingDate = null,
        endReadingDate = null,
        hlc = HlcTimestamp(hlcL, hlcC, hlcDevice),
    )

    // ============ 测试用例 ============

    @Test
    fun `REMOTE wins non-audio - reading progress and readingTime sum`() = runTest {
        val bookId = insertBook(
            progression = 0.1f,
            locator = "old",
            lastOpened = 1000L,
            readingTime = 1000L,
            syncHlcL = 1000L,
            syncHlcDevice = LOCAL_DEVICE,
        )
        val remote = readingRecord() // HLC = 2000 > 1000 → REMOTE wins

        engine.mergeAndWriteReading(bookId, remote, "epub")

        val book = bookDao.getBookByIdIncludeDeleted(bookId)!!
        assertEquals("remote-locator", book.locator)
        assertEquals(0.5f, book.progression, 0.001f)
        assertEquals(2000L, book.lastOpened)
        // hlcReceive 内部使用 wallClock,不 assert 精确值。
        // readingTime = per-device SUM = remote's 5000 (only remote row, no local per-device row yet)
        assertEquals(5000L, book.readingTime)
    }

    @Test
    fun `LOCAL wins non-audio - reading progress preserved readingTime sum`() = runTest {
        val bookId = insertBook(
            progression = 0.8f,
            locator = "local-locator",
            lastOpened = 5000L,
            readingTime = 3000L,
            syncHlcL = 5000L,
            syncHlcDevice = LOCAL_DEVICE,
        )
        // remote HLC older → LOCAL wins
        val remote = readingRecord(hlcL = 1000L)

        engine.mergeAndWriteReading(bookId, remote, "epub")

        val book = bookDao.getBookByIdIncludeDeleted(bookId)!!
        assertEquals("local-locator", book.locator)
        assertEquals(0.8f, book.progression, 0.001f)
        assertEquals(5000L, book.lastOpened)
        // hlcReceive 内部使用 wallClock,不 assert 精确值。
        // readingTime = per-device SUM (only remote row)
        assertEquals(5000L, book.readingTime)
    }

    @Test
    fun `multi-restore idempotent - same remote twice no double-count`() = runTest {
        val bookId = insertBook(
            progression = 0.0f,
            locator = "",
            readingTime = 0L,
            syncHlcL = 0L,
            syncHlcDevice = LOCAL_DEVICE,
        )
        val remote = readingRecord(readingTime = 5000L, hlcL = 2000L)

        // First restore
        engine.mergeAndWriteReading(bookId, remote, "epub")
        assertEquals(5000L, bookDao.getBookByIdIncludeDeleted(bookId)!!.readingTime)

        // Second restore (same remote record)
        engine.mergeAndWriteReading(bookId, remote, "epub")
        assertEquals(5000L, bookDao.getBookByIdIncludeDeleted(bookId)!!.readingTime)
    }

    @Test
    fun `audiobook REMOTE wins - readingTime follows LWW remote value`() = runTest {
        val bookId = insertBook(
            fileType = "mp3",
            progression = 0.1f,
            locator = "old",
            lastOpened = 1000L,
            readingTime = 30000L, // 30s playback position
            syncHlcL = 1000L,
            syncHlcDevice = LOCAL_DEVICE,
        )
        // remote readingTime = 60000 (60s), HLC newer → REMOTE wins
        val remote = readingRecord(
            readingTime = 60000L,
            hlcL = 2000L,
        )

        engine.mergeAndWriteReading(bookId, remote, "mp3")

        val book = bookDao.getBookByIdIncludeDeleted(bookId)!!
        // readingTime = remote's value (LWW, not SUM)
        assertEquals(60000L, book.readingTime)
    }

    @Test
    fun `audiobook LOCAL wins - readingTime NOT overwritten`() = runTest {
        val bookId = insertBook(
            fileType = "m4b",
            progression = 0.5f,
            locator = "local",
            lastOpened = 5000L,
            readingTime = 90000L, // 90s playback position
            syncHlcL = 5000L,
            syncHlcDevice = LOCAL_DEVICE,
        )
        // remote readingTime = 60000, HLC older → LOCAL wins
        val remote = readingRecord(readingTime = 60000L, hlcL = 1000L)

        engine.mergeAndWriteReading(bookId, remote, "m4b")

        val book = bookDao.getBookByIdIncludeDeleted(bookId)!!
        // readingTime stays at local value
        assertEquals(90000L, book.readingTime)
    }

    @Test
    fun `local increment after merge does not revert merged progress`() = runTest {
        val bookId = insertBook(
            progression = 0.0f,
            locator = "",
            lastOpened = 0L,
            readingTime = 0L,
            syncHlcL = 0L,
        )
        // Merge remote (wins)
        val remote = readingRecord(hlcL = 2000L)
        engine.mergeAndWriteReading(bookId, remote, "epub")
        assertEquals("remote-locator", bookDao.getBookByIdIncludeDeleted(bookId)!!.locator)

        // Local increment (simulates user reading via bookDao to avoid legacy SQLite constraint on upsert)
        bookDao.incrementReadingTime(bookId, 1000L)

        // Verify progress fields preserved
        val book = bookDao.getBookByIdIncludeDeleted(bookId)!!
        assertEquals("remote-locator", book.locator)
        assertEquals(0.5f, book.progression, 0.001f)
        assertEquals(2000L, book.lastOpened)
        // readingTime = remote(5000) + local(1000)
        assertEquals(6000L, book.readingTime)
    }

    private fun runTest(test: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { test() }
    }
}
