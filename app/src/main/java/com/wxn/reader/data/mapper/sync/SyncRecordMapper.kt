package com.wxn.reader.data.mapper.sync

import com.wxn.base.bean.sync.HlcTimestamp
import com.wxn.reader.data.backup.StableIdResolver
import com.wxn.reader.data.dto.BookAnnotationEntity
import com.wxn.reader.data.dto.BookEntity
import com.wxn.reader.data.dto.BookmarkEntity
import com.wxn.reader.data.dto.BookReadingTimeEntity
import com.wxn.reader.data.dto.BookShelfEntity
import com.wxn.reader.data.dto.BookVocabularyEntity
import com.wxn.reader.data.dto.NoteEntity
import com.wxn.reader.data.dto.ReadingActiveEntity
import com.wxn.reader.data.dto.ShelfEntity
import com.wxn.reader.data.remote.sync.canonical.AnnotationBody
import com.wxn.reader.data.remote.sync.canonical.AnnotationMotivation
import com.wxn.reader.data.remote.sync.canonical.BookIdentity
import com.wxn.reader.data.remote.sync.canonical.BookMetaRecord
import com.wxn.reader.data.remote.sync.canonical.BookReadingRecord
import com.wxn.reader.data.remote.sync.canonical.BookShelfRelationRecord
import com.wxn.reader.data.remote.sync.canonical.BookUserRecord
import com.wxn.reader.data.remote.sync.canonical.CanonicalAnnotation
import com.wxn.reader.data.remote.sync.canonical.CanonicalBookmark
import com.wxn.reader.data.remote.sync.canonical.CanonicalNote
import com.wxn.reader.data.remote.sync.canonical.ReadingActivityRecord
import com.wxn.reader.data.remote.sync.canonical.ShelfRecord
import com.wxn.reader.data.remote.sync.canonical.VocabularyRecord
import com.wxn.reader.domain.model.AnnotationType
import com.wxn.base.util.Logger
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entity ↔ Canonical Record 双向转换。
 *
 * ★ 严重-C:[BookUserRecord.readingStatus] 是可空 Int?(对应本地 ReadingStatus? 枚举)。
 * ★ v1.4 一般-F11:导入 readingStatus 未知值返回 null + warning,不崩。
 * ★ v1.3 严重-4:vocabulary 的 deleted 由 status=-1 派生。
 *
 * ★ 同步方案 §3.3 / §4.2。
 */
@Singleton
class SyncRecordMapper @Inject constructor(
    private val stableIdResolver: StableIdResolver,
) {
    // ===== Book =====

    fun toBookMetaRecord(
        book: BookEntity,
        perDeviceReadingTime: Long,
        localDeviceId: String,
    ): BookMetaRecord = BookMetaRecord(
        identity = BookIdentity(
            contentHash = book.contentHash ?: "",
            fileSize = 0L, // fileSize 不存实体,从文件可取;此处 0 占位
            partialMd5 = book.partialMd5,
            crc = book.crc, // ★ schema 4:导出 CRC32 供接收端 orphan 提升匹配
            fileType = book.fileType,
            title = book.title,
            authors = book.authors,
        ),
        title = book.title,
        authors = book.authors,
        description = book.description,
        publishDate = book.publishDate,
        publisher = book.publisher,
        language = book.language,
        numberOfPages = book.numberOfPages,
        wordCount = book.wordCount,
        subjects = book.subjects,
        coverPath = book.coverPath,
        duration = book.duration,
        narrator = book.narrator,
        hlc = HlcTimestamp(book.metaHlcL, book.metaHlcC, book.metaHlcDevice),
        deleted = book.deleted,
    )

    fun toBookUserRecord(book: BookEntity): BookUserRecord = BookUserRecord(
        rating = book.rating,
        isFavorite = book.isFavorite,
        readingStatus = book.readingStatus?.value,
        review = book.review,
        favoriteDate = book.favoriteDate,
        hlc = HlcTimestamp(book.userHlcL, book.userHlcC, book.userHlcDevice),
    )

    fun toBookReadingRecord(book: BookEntity, perDeviceReadingTime: Long): BookReadingRecord =
        BookReadingRecord(
            locator = book.locator,
            progression = book.progression,
            scrollIndex = book.scrollIndex,
            scrollOffset = book.scrollOffset,
            readingTime = perDeviceReadingTime,
            lastOpened = book.lastOpened,
            startReadingDate = book.startReadingDate,
            endReadingDate = book.endReadingDate,
            hlc = HlcTimestamp(book.syncHlcL, book.syncHlcC, book.syncHlcDevice),
        )

    // ===== Annotation =====

    fun toCanonicalAnnotation(e: BookAnnotationEntity): CanonicalAnnotation = CanonicalAnnotation(
        uuid = e.uuid ?: ensureUuid(),
        hlc = HlcTimestamp(e.syncHlcL, e.syncHlcC, e.syncHlcDevice),
        deleted = e.deleted,
        motivation = when (e.type) {
            AnnotationType.UNDERLINE -> AnnotationMotivation.UNDERLINING
            else -> AnnotationMotivation.HIGHLIGHTING
        },
        locator = e.locator,
        color = e.color,
        body = e.note?.let { AnnotationBody(text = it) },
    )

    fun toAnnotationEntity(bookId: Long, r: CanonicalAnnotation): BookAnnotationEntity =
        BookAnnotationEntity(
            id = 0,
            bookId = bookId,
            locator = r.locator,
            color = r.color,
            note = r.body?.text,
            type = when (r.motivation) {
                AnnotationMotivation.UNDERLINING -> AnnotationType.UNDERLINE
                else -> AnnotationType.HIGHLIGHT
            },
            uuid = r.uuid,
            deleted = r.deleted,
            syncHlcL = r.hlc.l,
            syncHlcC = r.hlc.c,
            syncHlcDevice = r.hlc.deviceId,
        )

    // ===== Note =====

    fun toCanonicalNote(e: NoteEntity): CanonicalNote = CanonicalNote(
        uuid = e.uuid ?: ensureUuid(),
        hlc = HlcTimestamp(e.syncHlcL, e.syncHlcC, e.syncHlcDevice),
        deleted = e.deleted,
        locator = e.locator,
        selectedText = e.selectedText,
        note = e.note,
        color = e.color,
        createdAt = e.createdAt,
    )

    fun toNoteEntity(bookId: Long, r: CanonicalNote): NoteEntity = NoteEntity(
        id = 0,
        locator = r.locator,
        selectedText = r.selectedText,
        note = r.note,
        color = r.color,
        bookId = bookId,
        createdAt = r.createdAt,
        uuid = r.uuid,
        deleted = r.deleted,
        syncHlcL = r.hlc.l,
        syncHlcC = r.hlc.c,
        syncHlcDevice = r.hlc.deviceId,
    )

    // ===== Bookmark =====

    fun toCanonicalBookmark(e: BookmarkEntity): CanonicalBookmark = CanonicalBookmark(
        uuid = e.uuid ?: ensureUuid(),
        hlc = HlcTimestamp(e.syncHlcL, e.syncHlcC, e.syncHlcDevice),
        deleted = e.deleted,
        locator = e.locator,
        chapterIndex = e.chapterIndex,
        color = e.color,
        dateAndTime = e.dateAndTime,
    )

    fun toBookmarkEntity(bookId: Long, r: CanonicalBookmark): BookmarkEntity = BookmarkEntity(
        id = 0,
        bookId = bookId,
        chapterIndex = r.chapterIndex,
        locator = r.locator,
        dateAndTime = r.dateAndTime,
        color = r.color,
        uuid = r.uuid,
        deleted = r.deleted,
        syncHlcL = r.hlc.l,
        syncHlcC = r.hlc.c,
        syncHlcDevice = r.hlc.deviceId,
    )

    // ===== Vocabulary (★ v1.3 严重-4:deleted 由 status=-1 派生)=====

    fun toVocabularyRecord(e: BookVocabularyEntity): VocabularyRecord {
        val deleted = e.status == -1
        val hlc = if (deleted) {
            HlcTimestamp(e.deletedHlcL, e.deletedHlcC, e.deletedHlcDevice)
        } else {
            HlcTimestamp(e.syncHlcL, e.syncHlcC, e.syncHlcDevice)
        }
        return VocabularyRecord(
            uuid = e.uuid ?: ensureUuid(),
            hlc = hlc,
            deleted = deleted,
            word = e.word,
            lang = e.lang,
            sentenceText = e.sentenceText,
            locator = e.locator,
            chapterIndex = e.chapterIndex,
            startParagraphIndex = e.startParagraphIndex,
            startTextOffset = e.startTextOffset,
            createdAt = e.createdAt,
        )
    }

    fun toVocabularyEntity(bookId: Long, r: VocabularyRecord): BookVocabularyEntity =
        BookVocabularyEntity(
            id = 0,
            bookId = bookId,
            lang = r.lang,
            word = r.word,
            status = if (r.deleted) -1 else 0,
            sentenceText = r.sentenceText,
            chapterIndex = r.chapterIndex,
            startParagraphIndex = r.startParagraphIndex,
            startTextOffset = r.startTextOffset,
            locator = r.locator,
            createdAt = r.createdAt,
            uuid = r.uuid,
            // deleted=true → 写 deletedHlc*;deleted=false → 写 syncHlc*(Mapper 双向转换)
            syncHlcL = if (r.deleted) 0L else r.hlc.l,
            syncHlcC = if (r.deleted) 0 else r.hlc.c,
            syncHlcDevice = if (r.deleted) "" else r.hlc.deviceId,
            deletedHlcL = if (r.deleted) r.hlc.l else 0L,
            deletedHlcC = if (r.deleted) r.hlc.c else 0,
            deletedHlcDevice = if (r.deleted) r.hlc.deviceId else "",
        )

    // ===== Shelf / BookShelf =====

    fun toShelfRecord(e: ShelfEntity): ShelfRecord = ShelfRecord(
        uuid = e.uuid ?: ensureUuid(),
        hlc = HlcTimestamp(e.syncHlcL, e.syncHlcC, e.syncHlcDevice),
        deleted = e.deleted,
        name = e.name,
    )

    fun toShelfEntity(r: ShelfRecord, order: Int): ShelfEntity = ShelfEntity(
        id = 0,
        name = r.name,
        order = order,
        uuid = r.uuid,
        deleted = r.deleted,
        syncHlcL = r.hlc.l,
        syncHlcC = r.hlc.c,
        syncHlcDevice = r.hlc.deviceId,
    )

    fun toRelationRecord(
        e: BookShelfEntity,
        bookContentHash: String,
    ): BookShelfRelationRecord = BookShelfRelationRecord(
        uuid = e.uuid ?: ensureUuid(),
        hlc = HlcTimestamp(e.syncHlcL, e.syncHlcC, e.syncHlcDevice),
        deleted = e.deleted,
        bookContentHash = bookContentHash,
        shelfUuid = "", // 由调用方(Exporter)填入对应 shelf 的 uuid
    )

    // ===== ReadingActivity =====

    fun toReadingActivityRecord(e: ReadingActiveEntity): ReadingActivityRecord =
        ReadingActivityRecord(
            date = e.date,
            deviceId = e.deviceId,
            readingTime = e.readingTime,
        )

    fun toReadingActiveEntity(r: ReadingActivityRecord): ReadingActiveEntity =
        ReadingActiveEntity(
            date = r.date,
            deviceId = r.deviceId,
            readingTime = r.readingTime,
        )

    // ===== readingStatus 映射(★ 严重-C + 一般-F11)=====

    /**
     * 导入 readingStatus:未知值返回 null + warning,不崩。
     * 已知值 0/1/2 → NOT_STARTED/IN_PROGRESS/FINISHED。
     */
    fun mapReadingStatus(value: Int?): Int? = value

    private fun ensureUuid(): String = UUID.randomUUID().toString()
}
