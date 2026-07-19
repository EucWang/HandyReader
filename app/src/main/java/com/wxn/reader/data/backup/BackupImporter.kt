package com.wxn.reader.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import coil3.SingletonImageLoader
import com.wxn.base.util.Logger
import com.wxn.reader.R
import com.wxn.reader.data.dto.BookEntity
import com.wxn.reader.data.mapper.sync.SyncRecordMapper
import com.wxn.reader.data.model.backup.BackupErrorCode
import com.wxn.reader.data.model.backup.BackupManifest
import com.wxn.reader.data.model.backup.BackupResult
import com.wxn.reader.data.model.backup.BookFileRecord
import com.wxn.reader.data.model.backup.BookSyncFailure
import com.wxn.reader.data.model.backup.COVER_SYNC_SCHEMA
import com.wxn.reader.data.model.backup.CURRENT_BACKUP_SCHEMA
import com.wxn.reader.data.model.backup.RestoreDiff
import com.wxn.reader.data.model.backup.ShelvesFile
import com.wxn.reader.data.model.backup.StorageInsufficientException
import com.wxn.reader.data.model.backup.UserDecision
import com.wxn.reader.data.remote.sync.canonical.BookIdentity
import com.wxn.reader.data.remote.sync.canonical.BookMetaRecord
import com.wxn.reader.data.remote.sync.canonical.ReadingActivityRecord
import com.wxn.reader.data.remote.sync.merge.SyncMergeEngine
import com.wxn.reader.data.source.local.AppDatabase
import com.wxn.reader.data.source.local.DeviceLocalStore
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.util.io.CoverSyncIO
import com.wxn.reader.util.sync.BackupPhase
import com.wxn.reader.util.sync.BackupProgressEmitter
import com.wxn.reader.util.sync.HybridLogicalClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ★ 一般-C 流式读 ZipInputStream(无落地)。
 * ★ v1.4 一般-F6:Pass 1 合并 readManifest + computeDiff,真正两次遍历。
 * ★ v1.4 严重-F3:Pass 2 补三个 merge 调用(notes/bookmarks/vocabulary)。
 *
 * ★ 同步方案 §6.2。
 */
@Singleton
class BackupImporter @Inject constructor(
    private val appDb: AppDatabase,
    private val bookDao: BookDao,
    private val mergeEngine: SyncMergeEngine,
    private val mapper: SyncRecordMapper,
    private val hlc: HybridLogicalClock,
    private val deviceLocalStore: DeviceLocalStore,
    @ApplicationContext private val ctx: Context,
    private val contentHashCalculator: ContentHashCalculator,
) {
    /** Coil 3 ImageLoader(由 SingletonImageLoader 工厂提供,无需 Hilt @Provides)。 */
    private val imageLoader by lazy { SingletonImageLoader.get(ctx) }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun import(
        sourceUri: Uri,
        emitter: BackupProgressEmitter,
    ): BackupResult = withContext(Dispatchers.IO) {
        // ★ P0:顶层 try/catch —— 与 BackupExporter 对称,保证任何未捕获异常(ZipException /
        //   SerializationException / SecurityException / IOException)都经 emitter.finish 落到
        //   Done 态,避免 UI 永久卡在 Active。
        try {
            doImport(sourceUri, emitter)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 红线 #6:全链路传播,绝不吞
        } catch (e: Exception) {
            Logger.e("BackupImporter: import failed: ${e.message}")
            val r = BackupResult.Failed(BackupErrorCode.fromException(e), e.message ?: "unknown")
            emitter.finish(r, isRestore = true)
            r
        }
    }

    private suspend fun doImport(
        sourceUri: Uri,
        emitter: BackupProgressEmitter,
    ): BackupResult {
        // ── Pass 1:读 manifest + 流式扫 books 取 hash(★ v1.4 一般-F6 合并一次遍历)──
        emitter.update(BackupPhase.READING_ZIP, R.string.backup_detail_reading_zip, progress = null)
        val pass1 = readManifestAndDiffHashes(sourceUri)
        if (pass1 == null) {
            val r = BackupResult.Failed(BackupErrorCode.MANIFEST_MISSING, "无 manifest.json")
            emitter.finish(r, isRestore = true)
            return r
        }
        val (manifest, backupHashes, backupDeletedHashes) = pass1

        // ★ v1.4 一般-F7:用 CURRENT_BACKUP_SCHEMA 常量
        if (manifest.schemaVersion > CURRENT_BACKUP_SCHEMA) {
            val r = BackupResult.Failed(BackupErrorCode.SCHEMA_TOO_HIGH, "请升级 App")
            emitter.finish(r, isRestore = true)
            return r
        }
        // ★ v1.3 建议-C:manifest.deviceId 非空校验
        if (manifest.deviceId.isBlank()) {
            val r = BackupResult.Failed(BackupErrorCode.MANIFEST_CORRUPT, "manifest deviceId 为空")
            emitter.finish(r, isRestore = true)
            return r
        }

        emitter.update(BackupPhase.DIFFING, R.string.backup_detail_diffing, progress = null)
        val localBooks = bookDao.getCountIncludeDeleted()
        // ★ v1.4 一般-F10:countByContentHashIn 分批(每批 500)
        val contentHashMatched = countByContentHashInBatched(backupHashes.toList())
        val deletedTombstones = countActiveByContentHashInBatched(backupDeletedHashes.toList())
        val matched = contentHashMatched
        val newOrphan = (manifest.counts.books - matched - deletedTombstones).coerceAtLeast(0)
        val diff = RestoreDiff(
            deviceName = manifest.deviceName,
            createdAt = manifest.createdAt,
            backupBooks = manifest.counts.books,
            backupNotes = manifest.counts.notes,
            backupHighlights = manifest.counts.annotations,
            backupBookmarks = manifest.counts.bookmarks,
            localBooks = localBooks,
            matched = matched,
            newOrphan = newOrphan,
            deletedTombstones = deletedTombstones,
        )

        // ── 交互态:CONFIRMING ──
        val decision = emitter.awaitRestoreConfirm(diff)
        if (decision == UserDecision.Cancel) {
            val r = BackupResult.Cancelled
            emitter.finish(r, isRestore = true)
            return r
        }

        // ── RECEIVING_HLC(严重-1:用 manifest.sourceDeviceHlc)──
        emitter.update(BackupPhase.RECEIVING_HLC, R.string.backup_detail_receiving_hlc, progress = null)
        hlc.receive(manifest.sourceDeviceHlc)

        // ── Pass 2:MERGING(★ v1.3 建议-F 分两阶段:先 books,后 shelves/activities)──
        emitter.update(
            BackupPhase.MERGING,
            R.string.backup_detail_merging,
            0,
            manifest.counts.books,
            progress = 0f,
        )
        val localDeviceId = deviceLocalStore.getOrCreateLocalDeviceId()
        val forceOverwriteActivities = manifest.deviceId == localDeviceId // P0-4
        val todayMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        var successCount = 0
        val failures = mutableListOf<BookSyncFailure>()
        var i = 0

        // ★ 封面同步 S2:双映射,阶段②处理 covers/ 时复用
        val sidToBookId = mutableMapOf<String, Long>()
        val sidToRemoteMeta = mutableMapOf<String, BookMetaRecord>()
        // ★ 封面预决策:阶段1用 pre-merge HLC 判定,阶段2按此集合决定是否落地
        val sidToLandCover = mutableSetOf<String>()
        val coversSupported = manifest.schemaVersion >= COVER_SYNC_SCHEMA
        val newCoverPaths = mutableListOf<String>() // 阶段②落地后收集,供清 Coil cache

        // ── 阶段 1:books(同时建 sid→bookId / sid→remoteMeta 映射)──
        ctx.contentResolver.openInputStream(sourceUri).use { input ->
            if (input == null) {
                val r = BackupResult.Failed(BackupErrorCode.SAF_WRITE_FAILED, "无法读取备份文件")
                emitter.finish(r, isRestore = true)
                return r
            }
            ZipInputStream(BufferedInputStream(input)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.startsWith("books/") && name.endsWith(".json")) {
                        val content = zis.bufferedReader(Charsets.UTF_8).readText()
                        try {
                            val bookFile = json.decodeFromString(BookFileRecord.serializer(), content)
                            val (localBookId, coverShouldLand) = appDb.withTransaction {
                                val id = resolveLocalBookId(bookFile.meta.identity)
                                    ?: createOrphanBook(bookFile)

                                // ★ 封面预决策:必须在 mergeAndWriteMeta 之前读 pre-merge localBook。
                                //   mergeAndWriteMeta 会把 metaHlc 推进到墙钟,阶段2 再判会恒得 LOCAL。
                                //   注意:用 bookFile.meta(含原始 coverPath),不是 metaForMerge(已剥离)。
                                val preMergeLocal = bookDao.getBookByIdIncludeDeleted(id)
                                val shouldLand = preMergeLocal != null &&
                                    mergeEngine.resolveCoverPathWinner(bookFile.meta, preMergeLocal) ==
                                        SyncMergeEngine.FieldWinner.REMOTE

                                mergeEngine.mergeAndWriteMeta(id, bookFile.meta.copy(coverPath = null)) //移除远端的图片路径
                                mergeEngine.mergeAndWriteUser(id, bookFile.user)
                                mergeEngine.mergeAndWriteReading(
                                    id, bookFile.reading, bookFile.meta.identity.fileType,
                                )
                                mergeEngine.mergeAndWriteAnnotations(id, bookFile.annotations)
                                // ★ v1.4 严重-F3:补三个 merge 调用
                                mergeEngine.mergeAndWriteNotes(id, bookFile.notes)
                                mergeEngine.mergeAndWriteBookmarks(id, bookFile.bookmarks)
                                mergeEngine.mergeAndWriteVocabulary(id, bookFile.vocabulary)
                                Pair(id, shouldLand)
                            }
                            // ★ A+++ 严重-9:orphan / 已匹配书创建后去重兜底(事务外,避免持锁)
                            //   createOrphanBook 直接调 bookDao.insertBook 不经过 InsertBookUseCase,
                            //   需手动调 handlePotentialConflict 避免恢复多本同 hash 书导致重复。
                            val ch = bookFile.meta.identity.contentHash
                            if (ch.isNotEmpty()) {
                                try {
                                    contentHashCalculator.handlePotentialConflict(localBookId, ch)
                                } catch (e: CancellationException) {
                                    throw e  // 红线 #6:取消向上传播,不吞
                                } catch (e: Exception) {
                                    Logger.w("BackupImporter: dedup failed bookId=$localBookId: ${e.message}")
                                }
                            }
                            successCount++
                            // ★ 封面同步 S2:建映射(stableId 来自 BookFileRecord,等价 entry 名)
                            val sid = bookFile.stableId
                            if (sid.isNotEmpty()) {
                                sidToBookId[sid] = localBookId
                                sidToRemoteMeta[sid] = bookFile.meta
                                if (coverShouldLand) sidToLandCover.add(sid)  // ★ 传递预决策到阶段2
                            }
                        } catch (e: CancellationException) {
                            // ★ C3 配套:取消信号向上传播,不当作单本失败
                            throw e
                        } catch (e: Exception) {
                            Logger.e("BackupImporter: book entry $name failed: ${e.message}")
                            failures.add(BookSyncFailure(name, BackupErrorCode.fromException(e)))
                        }
                        i++
                        emitter.update(
                            BackupPhase.MERGING,
                            R.string.backup_detail_merging,
                            i,
                            manifest.counts.books,
                            progress = i.toFloat() / manifest.counts.books.coerceAtLeast(1),
                        )
                    }
                    entry = zis.nextEntry
                }
            }
        }

        // ── 阶段 2:shelves + reading_activities + covers(books 全部 merge 完后再处理)──
        // ★ 封面同步 S2:covers/ 也在这一遍处理(sidToBookId/sidToRemoteMeta 已就绪)
        var consecutiveIoErrors = 0
        ctx.contentResolver.openInputStream(sourceUri).use { input ->
            if (input == null) {
                val r = finishImport(manifest, successCount, failures, newCoverPaths)
                emitter.finish(r, isRestore = true)
                return r
            }
            ZipInputStream(BufferedInputStream(input)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == "shelves/shelves.json" -> {
                            val content = zis.bufferedReader(Charsets.UTF_8).readText()
                            try {
                                val shelvesData = json.decodeFromString(ShelvesFile.serializer(), content)
                                appDb.withTransaction {
                                    mergeEngine.mergeAndWriteShelves(shelvesData.shelves, shelvesData.relations)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Logger.e("BackupImporter: shelves entry failed: ${e.message}")
                                failures.add(BookSyncFailure(name, BackupErrorCode.fromException(e)))
                            }
                        }
                        name.startsWith("reading_activities/") && name.endsWith(".json") -> {
                            val content = zis.bufferedReader(Charsets.UTF_8).readText()
                            try {
                                val records = json.decodeFromString(
                                    ListSerializer(ReadingActivityRecord.serializer()),
                                    content,
                                )
                                mergeEngine.mergeAndWriteReadingActivities(
                                    records, localDeviceId, forceOverwriteActivities, todayMs, manifest.createdAt,
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Logger.e("BackupImporter: ra entry $name failed: ${e.message}")
                                failures.add(BookSyncFailure(name, BackupErrorCode.fromException(e)))
                            }
                        }
                        // ★ 封面同步 Step 3.2:covers/<sid>.<ext> 落地
                        coversSupported && name.startsWith("covers/") -> {
                            // ★ 按阶段1预决策过滤:只有 shouldLand 的封面才进入落地流程
                            val coverSid = parseCoverSid(name)
                            if (coverSid != null && coverSid in sidToLandCover) {
                                try {
                                    val landed = landCoverEntry(name, zis, sidToBookId, sidToRemoteMeta)
                                    if (landed != null) {
                                        newCoverPaths += landed
                                        consecutiveIoErrors = 0
                                    }
                                } catch (e: StorageInsufficientException) {
                                    // S6:磁盘满,整体中止
                                    val r = BackupResult.Failed(BackupErrorCode.STORAGE_INSUFFICIENT, e.message ?: "")
                                    emitter.finish(r, isRestore = true)
                                    return r
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Logger.w("BackupImporter: cover entry $name skipped: ${e.message}")
                                    failures.add(BookSyncFailure(name, BackupErrorCode.fromException(e)))
                                    consecutiveIoErrors++
                                    if (consecutiveIoErrors >= MAX_CONSECUTIVE_IO_ERRORS) {
                                        val r = BackupResult.Failed(BackupErrorCode.STORAGE_INSUFFICIENT, "磁盘 IO 持续失败")
                                        emitter.finish(r, isRestore = true)
                                        return r
                                    }
                                }
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        }

        val r = finishImport(manifest, successCount, failures, newCoverPaths)
        emitter.finish(r, isRestore = true)
        return r
    }

    /** ★ 从 covers/<sid>.<ext> entry 名中提取 stableId。返回 null 表示不是合法 cover entry。 */
    private fun parseCoverSid(entryName: String): String? {
        if (!entryName.startsWith("covers/")) return null
        val fileName = entryName.removePrefix("covers/")
        val dotIdx = fileName.indexOf('.')
        return if (dotIdx > 0) fileName.substring(0, dotIdx) else fileName
    }

    /** ★ 封面同步:落地单个 cover entry + writeCoverPath。返回新本地路径;无需落地时返回 null。 */
    private suspend fun landCoverEntry(
        entryName: String,
        zis: ZipInputStream,
        sidToBookId: Map<String, Long>,
        sidToRemoteMeta: Map<String, BookMetaRecord>,
    ): String? {
        // ★ resolveCoverPathWinner 已在阶段1 pre-merge 完成(sidToLandCover 过滤),
        //   此处直接落地。sid 仍需解析(writeCoverPath 需要 remoteMeta.hlc)。
        val sid = parseCoverSid(entryName) ?: return null
        val bookId = sidToBookId[sid] ?: return null // S12:找不到对应书,跳过
        val remoteMeta = sidToRemoteMeta[sid] ?: return null
        // S1:ZipSlip 校验
        val target = CoverSyncIO.safeCoverFile(ctx.filesDir, entryName) ?: return null

        // §4.3:write-to-temp-then-rename 原子落地
        val coversDir = File(ctx.filesDir, "covers").apply { mkdirs() }
        val tmp = File(coversDir, ".$sid.tmp")
        try {
            tmp.outputStream().use { out ->
                CoverSyncIO.copyToCancellable(zis, out) // 红线 #9:可取消
            }
            // rename 原子覆盖(同 fs)。若 target 已存在(旧封面),rename 会覆盖。
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw java.io.IOException("rename cover failed")
            }

            val newCoverPath = target.absolutePath
            // Step 4.2:强写 coverPath(绕过 mergeAndWriteMeta 早退 + 推进 metaHlc)
            mergeEngine.writeCoverPath(bookId, newCoverPath, remoteMeta.hlc)
            return newCoverPath
        } catch (e: CancellationException) {
            // 取消:tmp 残留下次覆盖,正式文件未变(§4.3);D9 不回滚
            tmp.delete()
            throw e
        }
    }

    /** ★ 封面同步:收尾——清 Coil disk cache(S8),返回最终结果。 */
    private suspend fun finishImport(
        manifest: BackupManifest,
        successCount: Int,
        failures: List<BookSyncFailure>,
        newCoverPaths: List<String>,
    ): BackupResult {
        // S8:清 Coil disk cache(同路径覆盖写,避免返回旧 bitmap)
        newCoverPaths.forEach { path ->
            runCatching { imageLoader.diskCache?.remove(path) }
        }
        return if (failures.isEmpty()) BackupResult.Success(manifest)
        else BackupResult.PartialFail(successCount, failures)
    }

    private companion object {
        /** S6:连续 IO 错误阈值,超过即判定磁盘满,中止整体。 */
        const val MAX_CONSECUTIVE_IO_ERRORS = 10
    }

    /** ★ v1.4 一般-F6:Pass 1 合并 manifest + diff hash,一次 ZipInputStream 遍历。
     *  ★ 封面同步 S7:遇 covers/ 前缀直接跳过字节(不读入 hash 流程,避免 200MB 封面拖慢 Pass1)。 */
    private suspend fun readManifestAndDiffHashes(
        uri: Uri,
    ): Pass1Data? = withContext(Dispatchers.IO) {
        var manifest: BackupManifest? = null
        val backupHashes = mutableSetOf<String>()
        val backupDeletedHashes = mutableSetOf<String>()
        ctx.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return@withContext null
            ZipInputStream(BufferedInputStream(input)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "manifest.json" -> {
                            manifest = json.decodeFromString(
                                BackupManifest.serializer(),
                                zis.bufferedReader().readText(),
                            )
                        }
                        entry.name.startsWith("books/") && entry.name.endsWith(".json") -> {
                            val bookFile = json.decodeFromString(
                                BookFileRecord.serializer(),
                                zis.bufferedReader().readText(),
                            )
                            val hash = bookFile.meta.identity.contentHash
                            if (hash.isNotEmpty()) {
                                backupHashes += hash
                                if (bookFile.meta.deleted) backupDeletedHashes += hash
                            }
                        }
                        // S7:封面 entry 在 Pass1 不读字节,直接进下一 entry
                        entry.name.startsWith("covers/") -> { /* skip */ }
                    }
                    entry = zis.nextEntry
                }
            }
        }
        manifest?.let { Pass1Data(it, backupHashes, backupDeletedHashes) }
    }

    /** ★ v1.4 严重-F5:多行命中兜底,取最近打开的一本。 */
    private suspend fun resolveLocalBookId(identity: BookIdentity): Long? {
        // ── Step 1: contentHash 精确匹配 ──
        if (identity.contentHash.isNotEmpty()) {
            val ids = bookDao.getBookIdsByContentHash(identity.contentHash)
            if (ids.isNotEmpty()) {
                return when {
                    ids.size == 1 -> ids.first()
                    else -> bookDao.getLatestOpenedBookIdAmong(ids)
                }
            }
        }

        return null
    }

    /** 本地无此书 → 创建 orphan 行(source = "sync_orphan")。
     *  ★ 封面同步 Step 3.4:coverPath 强制 null(不写源设备失效路径)。
     *    阶段②若发现对应 sid 的 cover entry,会经 writeCoverPath 回填本地新路径。 */
    private suspend fun createOrphanBook(bookFile: BookFileRecord): Long {
        val meta = bookFile.meta
        // ★ 防御:旧备份(修复前生成)可能含失败占位行(crc=0 + 元数据全空)。
        //   正常解析成功的书经 FileParserImpl 的 CRC32 兜底必有 crc!=0,
        //   故 crc=0 + author/description 全空 → 强烈疑似失败占位行。
        //   一期仅告警计数,不硬跳过(避免误伤极少数合法无 author 的书)。
        if (meta.identity.crc == 0 &&
            meta.authors.isNullOrEmpty() &&
            meta.description.isNullOrEmpty()
        ) {
            Logger.w(
                "BackupImporter: suspicious orphan (likely failed-parse placeholder): " +
                    "title=${meta.title}, fileType=${meta.identity.fileType}"
            )
        }
        val orphan = BookEntity(
            uri = "",
            fileType = meta.identity.fileType,
            title = meta.title,
            authors = meta.authors,
            description = meta.description,
            publishDate = meta.publishDate,
            publisher = meta.publisher,
            language = meta.language,
            numberOfPages = meta.numberOfPages,
            wordCount = meta.wordCount,
            subjects = meta.subjects,
            coverPath = null,
            locator = bookFile.reading.locator,
            progression = bookFile.reading.progression,
            lastOpened = bookFile.reading.lastOpened,
            deleted = meta.deleted,
            rating = bookFile.user.rating,
            isFavorite = bookFile.user.isFavorite,
            favoriteDate = bookFile.user.favoriteDate,
            readingStatus = com.wxn.reader.data.dto.ReadingStatus.intToReadStatus(bookFile.user.readingStatus),
            readingTime = bookFile.reading.readingTime,
            startReadingDate = bookFile.reading.startReadingDate,
            endReadingDate = bookFile.reading.endReadingDate,
            review = bookFile.user.review,
            duration = meta.duration,
            narrator = meta.narrator,
            scrollIndex = bookFile.reading.scrollIndex,
            scrollOffset = bookFile.reading.scrollOffset,
            source = "sync_orphan",
            contentHash = meta.identity.contentHash.ifEmpty { null },
            partialMd5 = meta.identity.partialMd5,
            crc = meta.identity.crc, // ★ schema 4:接收端 orphan 拿到源设备真实 CRC32,供后续 FAB 导入提升匹配
            metaHlcL = meta.hlc.l,
            metaHlcC = meta.hlc.c,
            metaHlcDevice = meta.hlc.deviceId,
            userHlcL = bookFile.user.hlc.l,
            userHlcC = bookFile.user.hlc.c,
            userHlcDevice = bookFile.user.hlc.deviceId,
            syncHlcL = bookFile.reading.hlc.l,
            syncHlcC = bookFile.reading.hlc.c,
            syncHlcDevice = bookFile.reading.hlc.deviceId,
        )
        return bookDao.insertBook(orphan)
    }

    /** ★ v1.4 一般-F10:分批 500 绑定,防 >999 bind args 崩。 */
    private suspend fun countByContentHashInBatched(hashes: List<String>): Int {
        if (hashes.isEmpty()) return 0
        return hashes.chunked(500).sumOf { batch -> bookDao.countByContentHashInRaw(batch) }
    }

    private suspend fun countActiveByContentHashInBatched(hashes: List<String>): Int {
        if (hashes.isEmpty()) return 0
        return hashes.chunked(500).sumOf { batch -> bookDao.countActiveByContentHashInRaw(batch) }
    }
}

/**
 * Pass 1 解析结果。
 * @property backupHashes 备份中所有书的 contentHash 集合(用于 contentHash 精确匹配计数)
 * @property backupDeletedHashes 备份中已删除书的 contentHash 集合
 */
private data class Pass1Data(
    val manifest: BackupManifest,
    val backupHashes: Set<String>,
    val backupDeletedHashes: Set<String>,
)
