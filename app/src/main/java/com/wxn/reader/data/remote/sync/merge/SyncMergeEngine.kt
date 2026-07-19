package com.wxn.reader.data.remote.sync.merge

import androidx.room.withTransaction
import com.wxn.base.bean.sync.HlcTimestamp
import com.wxn.base.util.Logger
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
import com.wxn.reader.data.mapper.sync.SyncRecordMapper
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
import com.wxn.reader.data.source.local.AppDatabase
import com.wxn.reader.data.source.local.dao.AnnotationDao
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookShelfDao
import com.wxn.reader.data.source.local.dao.BookVocabularyDao
import com.wxn.reader.data.source.local.dao.BookmarkDao
import com.wxn.reader.data.source.local.dao.NoteDao
import com.wxn.reader.data.source.local.dao.ReadingActivityDao
import com.wxn.reader.data.source.local.dao.ShelfDao
import com.wxn.reader.util.sync.hlcReceive
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * ★ 同步合并引擎(一期 §3.3):十个 merge 方法(★ v1.4 严重-F3:七→十,补 notes/bookmarks/vocabulary)。
 *
 * 通用原则:
 * - meta/user:LWW + 非空优先(空值不覆盖非空)。
 * - reading:progression → hlc → deviceId 决胜;locator 同源;readingTime per-device SUM。
 * - annotations/notes/bookmarks/vocabulary/shelves:uuid 并集 + 同 uuid LWW + 删除墓碑。
 * - readingActivities:同 date+deviceId 取最新,跨 deviceId 求和。
 *
 * ★ 同步方案 v2.6 §6 全部合并算法 + 一期 §3.3.0/§3.3.1。
 */
@Singleton
class SyncMergeEngine @Inject constructor(
    private val appDb: AppDatabase,
    private val bookDao: BookDao,
    private val annotationDao: AnnotationDao,
    private val noteDao: NoteDao,
    private val bookmarkDao: BookmarkDao,
    private val shelfDao: ShelfDao,
    private val bookShelfDao: BookShelfDao,
    private val vocabularyDao: BookVocabularyDao,
    private val readingActivityDao: ReadingActivityDao,
    private val mapper: SyncRecordMapper,
    private val stableIdResolver: StableIdResolver,
) {

    // ===== mergeMeta:per-field LWW + 非空优先 =====

    suspend fun mergeAndWriteMeta(bookId: Long, remote: BookMetaRecord) {
        val local = bookDao.getBookByIdIncludeDeleted(bookId) ?: return
        val localHlc = HlcTimestamp(local.metaHlcL, local.metaHlcC, local.metaHlcDevice)
        if (remote.hlc <= localHlc && !remote.deleted) {
            // 远端不比本地新,跳过(但若远端是墓碑且 HLC 更新,见下方)
            return
        }
        val winner = if (remote.hlc >= localHlc) remote else null
        val mergedHlc = hlcReceive(localHlc, remote.hlc)
        val updated = local.copy(
            title = pickNonBlank(remote.title, local.title, winner == remote),
            authors = pickNonBlank(remote.authors, local.authors, winner == remote),
            description = pickNullable(remote.description, local.description, winner == remote),
            publishDate = pickNullable(remote.publishDate, local.publishDate, winner == remote),
            publisher = pickNullable(remote.publisher, local.publisher, winner == remote),
            language = pickNullable(remote.language, local.language, winner == remote),
            numberOfPages = pickNullable(remote.numberOfPages, local.numberOfPages, winner == remote),
            wordCount = if (winner == remote) remote.wordCount else local.wordCount,
            subjects = pickNullable(remote.subjects, local.subjects, winner == remote),

            // ⚠️ P1-1:跨设备还原时 remote.coverPath 是源设备绝对路径(本机无效)。
            //   调用方(BackupImporter)必须先 copy(coverPath=null) 再传入。
            //   封面跨设备同步由 resolveCoverPathWinner + writeCoverPath 独占管理。
            coverPath = pickNullable(remote.coverPath, local.coverPath, winner == remote),
            duration = pickNullable(remote.duration, local.duration, winner == remote),
            narrator = pickNullable(remote.narrator, local.narrator, winner == remote),
            contentHash = local.contentHash ?: remote.identity.contentHash.ifEmpty { null },
            metaHlcL = mergedHlc.l,
            metaHlcC = mergedHlc.c,
            metaHlcDevice = mergedHlc.deviceId,
            deleted = if (remote.deleted && remote.hlc >= localHlc) true else local.deleted,
        )
        bookDao.update(updated)
    }

    // ===== 封面同步:dry-run 决策 + 强写 coverPath =====

    /**
     * ★ 封面同步方案 Step 4.1:语义决策 coverPath 该取哪一端。**不写 DB**。
     *
     * 用于 BackupImporter 决定是否落地封面字节(只有 winner==REMOTE 才落地)。
     *
     * **关键差异**:本函数与 [mergeAndWriteMeta] 对 coverPath 的处理**不一致**——
     * 后者在 `remote.hlc <= localHlc` 时直接 return(line 74 早退),coverPath 不会被 pickNullable
     * 更新;而本函数是纯语义判断"理论上谁该赢"。因此 dry-run==REMOTE 后必须调
     * [writeCoverPath] 强写,不能调 mergeAndWriteMeta(否则 orphan/D8 场景永远写不进)。
     */
    fun resolveCoverPathWinner(remote: BookMetaRecord, local: BookEntity): FieldWinner {
        // D8 优先:本地无封面 + 远端有 → 取远端(无数据可比,远端即真相)
        if (local.coverPath.isNullOrEmpty() && !remote.coverPath.isNullOrEmpty()) {
            return FieldWinner.REMOTE
        }
        // 远端无封面 → 本地(清空语义由 mergeAndWriteMeta 处理,无需落地字节)
        if (remote.coverPath.isNullOrEmpty()) return FieldWinner.LOCAL
        // 双方都非空:HLC 比较(平局时取远端,符合 LWW tie-break)
        val localHlc = HlcTimestamp(local.metaHlcL, local.metaHlcC, local.metaHlcDevice)
        return if (remote.hlc >= localHlc) FieldWinner.REMOTE else FieldWinner.LOCAL
    }

    /**
     * ★ 封面同步方案 Step 4.2:强写 coverPath,绕过 [mergeAndWriteMeta] 的 HLC 早退。
     *
     * **仅在** [resolveCoverPathWinner] == REMOTE 且封面字节已落地后调用。
     *
     * 同时推进 metaHlc(`hlcReceive` 后 strictly > remoteHlc),保证下次重试幂等:
     * 重试时 localHlc > remoteHlc,dry-run 判 LOCAL 跳过落地,本地 coverPath 已是新路径且文件存在 → 一致。
     *
     * 单次 [bookDao.update] 同时写 coverPath 和 metaHlc(§4.3 原子性前提)。
     */
    suspend fun writeCoverPath(bookId: Long, newCoverPath: String, remoteHlc: HlcTimestamp) {
        val local = bookDao.getBookByIdIncludeDeleted(bookId) ?: return
        val localHlc = HlcTimestamp(local.metaHlcL, local.metaHlcC, local.metaHlcDevice)
        val mergedHlc = hlcReceive(localHlc, remoteHlc) // 推进,确保 > 旧值
        val updated = local.copy(
            coverPath = newCoverPath,
            metaHlcL = mergedHlc.l,
            metaHlcC = mergedHlc.c,
            metaHlcDevice = mergedHlc.deviceId,
        )
        bookDao.update(updated)
    }

    /** 封面字段决策结果。 */
    enum class FieldWinner { REMOTE, LOCAL }

    // ===== mergeUser:readingStatus 单调状态机 + rating/favorite/review LWW =====

    suspend fun mergeAndWriteUser(bookId: Long, remote: BookUserRecord) {
        val local = bookDao.getBookByIdIncludeDeleted(bookId) ?: return
        val localHlc = HlcTimestamp(local.userHlcL, local.userHlcC, local.userHlcDevice)
        val mergedHlc = hlcReceive(localHlc, remote.hlc)
        // ★ 严重-C:readingStatus 单调上升(maxOf,null 视为 0)
        val mergedStatus = maxOf(local.readingStatus?.value ?: 0, remote.readingStatus ?: 0)
        val winnerIsRemote = remote.hlc >= localHlc
        val updated = local.copy(
            rating = if (winnerIsRemote) remote.rating else local.rating,
            isFavorite = if (winnerIsRemote) remote.isFavorite else local.isFavorite,
            favoriteDate = if (winnerIsRemote) remote.favoriteDate else local.favoriteDate,
            readingStatus = com.wxn.reader.data.dto.ReadingStatus.intToReadStatus(mergedStatus),
            review = pickNullable(remote.review, local.review, winnerIsRemote),
            userHlcL = mergedHlc.l,
            userHlcC = mergedHlc.c,
            userHlcDevice = mergedHlc.deviceId,
        )
        bookDao.update(updated)
    }

    // ===== mergeReading:progression → hlc → deviceId 决胜 =====

    suspend fun mergeAndWriteReading(bookId: Long, remote: BookReadingRecord, fileType: String) {
        val local = bookDao.getBookByIdIncludeDeleted(bookId) ?: return
        val localHlc = HlcTimestamp(local.syncHlcL, local.syncHlcC, local.syncHlcDevice)
        val mergedHlc = hlcReceive(localHlc, remote.hlc)

        val winnerIsRemote = resolveReadingWinner(remote.hlc, localHlc) == FieldWinner.REMOTE

        // 第一步:用 `local` 快照构建 `updated`(不含 readingTime——第二步单独处理)
        val updated = if (winnerIsRemote) {
            local.copy(
                locator = remote.locator,
                progression = remote.progression,
                scrollIndex = remote.scrollIndex,
                scrollOffset = remote.scrollOffset,
                lastOpened = remote.lastOpened ?: local.lastOpened,
                startReadingDate = remote.startReadingDate ?: local.startReadingDate,
                endReadingDate = remote.endReadingDate ?: local.endReadingDate,
                syncHlcL = mergedHlc.l,
                syncHlcC = mergedHlc.c,
                syncHlcDevice = mergedHlc.deviceId,
            )
        } else {
            local.copy(
                syncHlcL = mergedHlc.l,
                syncHlcC = mergedHlc.c,
                syncHlcDevice = mergedHlc.deviceId,
            )
        }

        appDb.withTransaction {
            // 写 merged 阅读进度
            bookDao.update(updated)

            // per-device readingTime + books.readingTime 刷新(音频 vs 文本不同策略)
            if (remote.readingTime > 0) {
                val remoteDeviceId = remote.hlc.deviceId
                val existing = appDb.bookReadingTimeDao().getByBookIdAndDevice(bookId, remoteDeviceId)
                if (existing == null || existing.readingTimeMs != remote.readingTime) {
                    appDb.bookReadingTimeDao().upsert(
                        BookReadingTimeEntity(
                            bookId = bookId,
                            deviceId = remoteDeviceId,
                            readingTimeMs = remote.readingTime,
                            lastUpdated = System.currentTimeMillis(),
                        )
                    )
                }
                // 派生值重算:音频 LWW / 文本 SUM
                val isAudio = fileType.lowercase() in AppDatabase.AUDIO_FILE_TYPES
                if (isAudio) {
                    if (winnerIsRemote) {
                        bookDao.updateReadingTime(bookId, remote.readingTime)
                    }
                } else {
                    val sum = appDb.bookReadingTimeDao().sumByBookId(bookId)
                    bookDao.updateReadingTime(bookId, sum)
                }
            }
        }
    }

    // ===== mergeAnnotations:uuid 并集 + 同 uuid LWW + 墓碑 =====

    suspend fun mergeAndWriteAnnotations(bookId: Long, remoteList: List<CanonicalAnnotation>) {
        val localList = annotationDao.getByBookIdIncludeDeleted(bookId)
        val localByUuid = localList.filter { !it.uuid.isNullOrEmpty() }.associateBy { it.uuid!! }
        appDb.withTransaction {
            remoteList.forEach { remote ->
                val local = localByUuid[remote.uuid]
                if (local == null) {
                    // 新增
                    annotationDao.insert(mapper.toAnnotationEntity(bookId, remote))
                } else {
                    // LWW
                    val localHlc = HlcTimestamp(local.syncHlcL, local.syncHlcC, local.syncHlcDevice)
                    if (remote.hlc >= localHlc) {
                        // 远端较新:覆盖(含 deleted 墓碑)
                        val updated = mapper.toAnnotationEntity(bookId, remote).copy(id = local.id)
                        annotationDao.update(updated)
                    }
                }
            }
        }
    }

    // ===== mergeNotes(★ v1.4 严重-F3 新增)=====

    suspend fun mergeAndWriteNotes(bookId: Long, remoteList: List<CanonicalNote>) {
        val localList = noteDao.getByBookIdIncludeDeleted(bookId)
        val localByUuid = localList.filter { !it.uuid.isNullOrEmpty() }.associateBy { it.uuid!! }
        appDb.withTransaction {
            remoteList.forEach { remote ->
                val local = localByUuid[remote.uuid]
                if (local == null) {
                    noteDao.insert(mapper.toNoteEntity(bookId, remote))
                } else {
                    val localHlc = HlcTimestamp(local.syncHlcL, local.syncHlcC, local.syncHlcDevice)
                    if (remote.hlc >= localHlc) {
                        val updated = mapper.toNoteEntity(bookId, remote).copy(id = local.id)
                        noteDao.update(updated)
                    }
                }
            }
        }
    }

    // ===== mergeBookmarks(★ v1.4 严重-F3 新增)=====

    suspend fun mergeAndWriteBookmarks(bookId: Long, remoteList: List<CanonicalBookmark>) {
        val localList = bookmarkDao.getByBookIdIncludeDeleted(bookId)
        val localByUuid = localList.filter { !it.uuid.isNullOrEmpty() }.associateBy { it.uuid!! }
        appDb.withTransaction {
            remoteList.forEach { remote ->
                val local = localByUuid[remote.uuid]
                if (local == null) {
                    bookmarkDao.insert(mapper.toBookmarkEntity(bookId, remote))
                } else {
                    val localHlc = HlcTimestamp(local.syncHlcL, local.syncHlcC, local.syncHlcDevice)
                    if (remote.hlc >= localHlc) {
                        val updated = mapper.toBookmarkEntity(bookId, remote).copy(id = local.id)
                        bookmarkDao.update(updated)
                    }
                }
            }
        }
    }

    // ===== mergeVocabulary(★ v1.4 严重-F3 新增;严重-4:deleted 由 status=-1 派生)=====

    suspend fun mergeAndWriteVocabulary(bookId: Long, remoteList: List<VocabularyRecord>) {
        val localList = vocabularyDao.getByBookIdIncludeDeleted(bookId)
        val localByUuid = localList.filter { !it.uuid.isNullOrEmpty() }.associateBy { it.uuid!! }
        appDb.withTransaction {
            remoteList.forEach { remote ->
                val local = localByUuid[remote.uuid]
                if (local == null) {
                    vocabularyDao.insert(mapper.toVocabularyEntity(bookId, remote))
                } else {
                    val localEffectiveHlc = if (local.status == -1) {
                        HlcTimestamp(local.deletedHlcL, local.deletedHlcC, local.deletedHlcDevice)
                    } else {
                        HlcTimestamp(local.syncHlcL, local.syncHlcC, local.syncHlcDevice)
                    }
                    if (remote.hlc >= localEffectiveHlc) {
                        val updated = mapper.toVocabularyEntity(bookId, remote).copy(id = local.id)
                        vocabularyDao.update(updated)
                    }
                }
            }
        }
    }

    // ===== mergeShelves + mergeBookShelfRelations(§6.8)=====

    suspend fun mergeAndWriteShelves(
        shelves: List<ShelfRecord>,
        relations: List<BookShelfRelationRecord>,
    ) {
        appDb.withTransaction {
            // shelves:uuid 并集 + LWW
            val localShelves = shelfDao.getAllIncludeDeleted()
            val localByUuid = localShelves.filter { !it.uuid.isNullOrEmpty() }.associateBy { it.uuid!! }
            val shelfIdByUuid = mutableMapOf<String, Long>()
            shelves.forEach { remote ->
                val local = localByUuid[remote.uuid]
                if (local == null) {
                    val newId = shelfDao.insert(mapper.toShelfEntity(remote, order = 0))
                    shelfIdByUuid[remote.uuid] = newId
                } else {
                    shelfIdByUuid[remote.uuid] = local.id
                    val localHlc = HlcTimestamp(local.syncHlcL, local.syncHlcC, local.syncHlcDevice)
                    if (remote.hlc >= localHlc) {
                        val updated = mapper.toShelfEntity(remote, order = local.order).copy(id = local.id)
                        shelfDao.update(updated)
                    }
                }
            }

            // relations:book_shelf uuid 并集 + LWW
            val localRelations = bookShelfDao.getAllIncludeDeleted()
            val localRelByUuid = localRelations.filter { !it.uuid.isNullOrEmpty() }.associateBy { it.uuid!! }
            relations.forEach { remote ->
                // 解析 bookId / shelfId(本地匹配)
                val localBookId = resolveLocalBookIdByContentHash(remote.bookContentHash) ?: return@forEach
                val localShelfId = shelfIdByUuid[remote.shelfUuid]
                    ?: shelfDao.getByUuid(remote.shelfUuid)?.id
                    ?: return@forEach
                val local = localRelByUuid[remote.uuid]
                if (local == null) {
                    bookShelfDao.insert(
                        BookShelfEntity(
                            bookId = localBookId,
                            shelfId = localShelfId,
                            uuid = remote.uuid,
                            deleted = remote.deleted,
                            syncHlcL = remote.hlc.l,
                            syncHlcC = remote.hlc.c,
                            syncHlcDevice = remote.hlc.deviceId,
                        )
                    )
                } else {
                    val localHlc = HlcTimestamp(local.syncHlcL, local.syncHlcC, local.syncHlcDevice)
                    if (remote.hlc >= localHlc) {
                        val updated = local.copy(
                            deleted = remote.deleted,
                            syncHlcL = remote.hlc.l,
                            syncHlcC = remote.hlc.c,
                            syncHlcDevice = remote.hlc.deviceId,
                        )
                        bookShelfDao.update(updated)
                    }
                }
            }
        }
    }

    // ===== mergeReadingActivities(§3.3.1:本机还原保留今天数据)=====

    suspend fun mergeAndWriteReadingActivities(
        records: List<ReadingActivityRecord>,
        localDeviceId: String,
        forceOverwrite: Boolean,
        todayMs: Long,
        backupCreatedAt: Long,
    ) {
        appDb.withTransaction {
            records.forEach { record ->
                val isLocal = record.deviceId == localDeviceId || record.deviceId.isEmpty()
                // 跨设备场景:跳过自己(v2.6 标准)
                if (!forceOverwrite && isLocal) return@forEach
                // ★ 一般-E:本机还原(forceOverwrite=true)时,今天的数据保留不覆盖
                if (forceOverwrite && isLocal && record.date >= todayMs) return@forEach
                // 跨设备远端 或 本机非今天数据:upsert
                readingActivityDao.upsertRemote(
                    ReadingActiveEntity(
                        date = record.date,
                        deviceId = record.deviceId.ifEmpty { localDeviceId },
                        readingTime = record.readingTime,
                    )
                )
            }
        }
    }

    // ===== Helpers =====

    private suspend fun resolveLocalBookIdByContentHash(contentHash: String): Long? {
        if (contentHash.isEmpty()) return null
        val ids = bookDao.getBookIdsByContentHash(contentHash)
        return when {
            ids.isEmpty() -> null
            ids.size == 1 -> ids.first()
            else -> bookDao.getLatestOpenedBookIdAmong(ids)
        }
    }

    private fun pickNonBlank(remote: String, local: String, winnerIsRemote: Boolean): String {
        if (winnerIsRemote && remote.isNotBlank()) return remote
        return local.ifBlank { remote }
    }

    private fun <T> pickNullable(remote: T?, local: T?, winnerIsRemote: Boolean): T? {
        if (winnerIsRemote && remote != null) return remote
        return local ?: remote
    }
}

/**
 * ★ reading 档决胜纯函数:谁的时间(HLC)更晚谁赢,平局取 REMOTE(LWW 确定性 tie-break)。
 *
 * 抽成顶层 internal 函数便于单测(无 Android/Room 依赖)。被 [SyncMergeEngine.mergeAndWriteReading] 使用。
 *
 * 设计:与 meta/user 档的 `remote.hlc >= localHlc` 语义对齐,统一为「最后写赢」。
 * progression 不参与决胜——它是非单调量(用户会回翻/跳章),拿它当主键会导致
 * 「时间更新但进度百分比更低」的远端被旧本地覆盖(还原后进度不更新 bug)。
 */
internal fun resolveReadingWinner(
    remoteHlc: HlcTimestamp,
    localHlc: HlcTimestamp,
): SyncMergeEngine.FieldWinner =
    if (remoteHlc >= localHlc) SyncMergeEngine.FieldWinner.REMOTE else SyncMergeEngine.FieldWinner.LOCAL
