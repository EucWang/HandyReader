package com.wxn.reader.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.wxn.base.util.Logger
import com.wxn.reader.R
import com.wxn.reader.data.mapper.sync.SyncRecordMapper
import com.wxn.reader.data.model.backup.BackupCounts
import com.wxn.reader.data.model.backup.BackupErrorCode
import com.wxn.reader.data.model.backup.BackupManifest
import com.wxn.reader.data.model.backup.BackupResult
import com.wxn.reader.data.model.backup.BookFileRecord
import com.wxn.reader.data.model.backup.CURRENT_BACKUP_SCHEMA
import com.wxn.reader.data.model.backup.ShelvesFile
import com.wxn.reader.data.model.backup.UserDecision
import com.wxn.reader.data.source.local.AppDatabase
import com.wxn.reader.data.source.local.DeviceLocalStore
import com.wxn.reader.data.source.local.dao.AnnotationDao
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookReadingTimeDao
import com.wxn.reader.data.source.local.dao.BookShelfDao
import com.wxn.reader.data.source.local.dao.BookVocabularyDao
import com.wxn.reader.data.source.local.dao.BookmarkDao
import com.wxn.reader.data.source.local.dao.NoteDao
import com.wxn.reader.data.source.local.dao.ReadingActivityDao
import com.wxn.reader.data.source.local.dao.ShelfDao
import com.wxn.reader.util.io.CoverSyncIO
import com.wxn.reader.util.sync.BackupPhase
import com.wxn.reader.util.sync.BackupProgressEmitter
import com.wxn.reader.util.sync.HybridLogicalClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ★ 一般-B 分批流式导出(防 OOM):每本书单独事务读自身数据 → 立即写 ZIP entry → 丢弃。
 *
 * ★ v1.5 G1:reading_activities 快照在 books 循环【之前】取(allRaSnapshot),避免后台 TTS 写入漂移。
 * ★ 严重-2:ZIP 制品内 deviceId 永不空串,导出时强制规范化。
 * ★ 严重-1:manifest.sourceDeviceHlc = hlc.now()(≥ 所有 Record 的 HLC)。
 *
 * ★ 同步方案 §5.2。
 */
@Singleton
class BackupExporter @Inject constructor(
    private val appDb: AppDatabase,
    private val bookDao: BookDao,
    private val noteDao: NoteDao,
    private val annotationDao: AnnotationDao,
    private val bookmarkDao: BookmarkDao,
    private val shelfDao: ShelfDao,
    private val bookShelfDao: BookShelfDao,
    private val vocabularyDao: BookVocabularyDao,
    private val readingActivityDao: ReadingActivityDao,
    private val bookReadingTimeDao: BookReadingTimeDao,
    private val mapper: SyncRecordMapper,
    private val hlc: HybridLogicalClock,
    private val deviceLocalStore: DeviceLocalStore,
    private val stableIdResolver: StableIdResolver,
    private val contentHashCalculator: ContentHashCalculator,  // ★ A+++ 一般-3:Hilt 注入替代局部 new
    @ApplicationContext private val ctx: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(
        targetUri: Uri,
        emitter: BackupProgressEmitter,
    ): BackupResult = withContext(Dispatchers.IO) {
        val localDeviceId = deviceLocalStore.getOrCreateLocalDeviceId()
        if (localDeviceId.isBlank()) {
            val r = BackupResult.Failed(BackupErrorCode.UNKNOWN, "deviceId 生成失败")
            emitter.finish(r, isRestore = false)
            return@withContext r
        }

        // 0. 备份前兜底补算 contentHash(时机3,串行)
        emitter.update(BackupPhase.HASH_CHECK, R.string.backup_detail_hash_check)
        val hashResult = ensureAllContentHashes(emitter)
        if (hashResult is HashCheck.Failed) {
            val r = BackupResult.Failed(BackupErrorCode.HASH_ALL_FAILED, "所有书均无法计算指纹")
            emitter.finish(r, isRestore = false)
            return@withContext r
        }
        if (hashResult is HashCheck.Partial) {
            val decision = emitter.awaitHashPartial(hashResult.inaccessible, hashResult.failed)
            if (decision == UserDecision.Cancel) {
                val r = BackupResult.Cancelled
                emitter.finish(r, isRestore = false)
                return@withContext r
            }
        }

        // 1. ★ 一般-B:只读 bookId 列表(轻量)
        emitter.update(BackupPhase.EXPORTING, R.string.backup_detail_preparing, progress = null)
        val bookIds = appDb.withTransaction { bookDao.getAllBookIdsIncludeDeleted() }
        val total = bookIds.size

        // 2. ★ v1.5 G1:reading_activities 在 books 循环【之前】取快照
        val allRaSnapshot = readingActivityDao.getAll()
        var raTotal = allRaSnapshot.size

        // ★ v1.4 建议-F5:磁盘预检 cacheDir.usableSpace > estimatedZipSize * 2
        // 封面同步 M10:预估纳入封面字节(遍历 stat 所有 coverPath 文件)
        val coverBytesEstimate = estimateCoverBytes(bookIds)
        val estimatedZipSize = estimateZipSize(bookIds.size, allRaSnapshot.size, coverBytesEstimate)
        if (ctx.cacheDir.usableSpace < estimatedZipSize * 2) {
            val r = BackupResult.Failed(BackupErrorCode.STORAGE_INSUFFICIENT, "存储空间不足")
            emitter.finish(r, isRestore = false)
            return@withContext r
        }

        // 计数器(★ v1.3 严重-5 + v1.4 严重-F3:四类标注全部累加)
        var exportedBookCount = 0
        val skippedBooks = mutableListOf<String>()
        var noteTotal = 0
        var annotationTotal = 0
        var bookmarkTotal = 0
        var vocabTotal = 0
        // 封面同步计数
        var coverSyncCount = 0
        var coverSyncTotalBytes = 0L

        val zipFile = File(ctx.cacheDir, "handyreader-backup-${System.currentTimeMillis()}.zip.tmp")

        try {
            var manifest : BackupManifest? = null
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                // books/(按书逐本流式)
                bookIds.forEachIndexed { i, bookId ->
                    val bookFile: BookFileRecord? = appDb.withTransaction {
                        val book = bookDao.getBookByIdIncludeDeleted(bookId) ?: return@withTransaction null
                        // ★ 失败占位行(importStatus != 0)不参与备份:源文件已损坏,元数据全空,
                        //   还原后只会生成幽灵书(uri=""、打不开)。
                        //   与日常展示查询 WHERE importStatus = 0 语义一致。
                        if (!BackupBookFilter.shouldExport(book)) {
                            Logger.d(
                                "BackupExporter: skipping bookId=$bookId " +
                                    "(importStatus=${book.importStatus}, title=${book.title})"
                            )
                            return@withTransaction null
                        }
                        val notes = noteDao.getByBookIdIncludeDeleted(bookId)
                        val annotations = annotationDao.getByBookIdIncludeDeleted(bookId)
                        val bookmarks = bookmarkDao.getByBookIdIncludeDeleted(bookId)
                        val vocab = vocabularyDao.getByBookIdIncludeDeleted(bookId)
                        val brt = bookReadingTimeDao.getByBookIdAndDevice(bookId, localDeviceId)
                        val perDeviceReadingTime = brt?.readingTimeMs ?: book.readingTime
                        val meta = mapper.toBookMetaRecord(book, perDeviceReadingTime, localDeviceId)
                        val user = mapper.toBookUserRecord(book)
                        val reading = mapper.toBookReadingRecord(book, perDeviceReadingTime)
                        BookFileRecord(
                            meta = meta,
                            user = user,
                            reading = reading,
                            annotations = annotations.map { mapper.toCanonicalAnnotation(it) },
                            notes = notes.map { mapper.toCanonicalNote(it) },
                            bookmarks = bookmarks.map { mapper.toCanonicalBookmark(it) },
                            vocabulary = vocab.map { mapper.toVocabularyRecord(it) },
                        )
                    }
                    if (bookFile != null) {
                        val contentHash = bookFile.meta.identity.contentHash
                        if (contentHash.isBlank()) {
                            val title = bookFile.meta.title.ifEmpty { "bookId=$bookId" }
                            Logger.w("BackupExporter: skipping $title (bookId=$bookId): contentHash empty")
                            skippedBooks.add(title)
                        } else {
                            val stableId = bookFile.stableId
                            writeZipEntry(zos, "books/$stableId.json", json.encodeToString(bookFile))
                            noteTotal += bookFile.notes.size
                            annotationTotal += bookFile.annotations.size
                            bookmarkTotal += bookFile.bookmarks.size
                            vocabTotal += bookFile.vocabulary.size
                            exportedBookCount++

                            // ★ 封面同步 Step 2.2:写 covers/<stableId>.<ext>(单封面 try/catch 不阻塞)
                            val coverPath = bookFile.meta.coverPath
                            if (!coverPath.isNullOrEmpty()) {
                                val src = File(coverPath)
                                if (src.exists()) {
                                    val ext = src.extension.ifEmpty { "jpg" }
                                    try {
                                        val written = writeCoverZipEntry(zos, "covers/$stableId.$ext", src)
                                        coverSyncCount++
                                        coverSyncTotalBytes += written
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        // 红线 #6:取消向上传播,不吞
                                        throw e
                                    } catch (e: Exception) {
                                        Logger.w("BackupExporter: cover $stableId skipped: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                    emitter.update(
                        BackupPhase.EXPORTING,
                        R.string.backup_detail_exporting,
                        i + 1,
                        total,
                        progress = (i + 1).toFloat() / total.coerceAtLeast(1),
                    )
                }

                // shelves + book_shelf(数据量小,一次性读)
                val shelvesData = appDb.withTransaction {
                    val shelves = shelfDao.getAllIncludeDeleted()
                    val relations = bookShelfDao.getAllIncludeDeleted()
                    // ★ 修复:null uuid 的 shelf 先生成 UUID,保证 shelf record 和 relation 用同一个
                    val shelfUuidById = mutableMapOf<Long, String>()
                    val shelfRecords = shelves.map { shelf ->
                        val uuid = shelf.uuid ?: UUID.randomUUID().toString()
                        shelfUuidById[shelf.id] = uuid
                        mapper.toShelfRecord(shelf).copy(uuid = uuid)
                    }
                    val bookHashById = mutableMapOf<Long, String>()
                ShelvesFile(
                    shelves = shelfRecords,
                    relations = relations.mapNotNull { rel ->
                        val shelfUuid = shelfUuidById[rel.shelfId] ?: return@mapNotNull null
                        val bookHash = bookHashById.getOrPut(rel.bookId) {
                            bookDao.getBookByIdIncludeDeleted(rel.bookId)?.contentHash ?: ""
                        }
                        // ★ P1-4:contentHash 为空的书(未补算 / deleted 未覆盖)的 relation 不写入,
                        //   否则还原时 resolveLocalBookIdByContentHash("") 返回 null,relation 静默丢失。
                        //   备份前 ensureAllContentHashes 已覆盖活行,此处仅兜底 deleted 行等边角。
                        if (bookHash.isEmpty()) {
                            Logger.w("BackupExporter: skip relation(bookId=${rel.bookId}, shelfId=${rel.shelfId}): book contentHash empty")
                            return@mapNotNull null
                        }
                        mapper.toRelationRecord(rel, bookHash).copy(shelfUuid = shelfUuid)
                    },
                )
                }
                val shelvesTotal = shelvesData.shelves.size
                val relationsTotal = shelvesData.relations.size
                writeZipEntry(zos, "shelves/shelves.json", json.encodeToString(shelvesData))

                // reading_activities(★ v1.5 G1:用循环前取的 allRaSnapshot;★ 严重-2:deviceId 空串规范化)
                val raByFile = allRaSnapshot.groupBy { ra ->
                    val devId = ra.deviceId.ifEmpty { localDeviceId }
                    "$devId/${ra.date}"
                }
                raByFile.forEach { (key, records) ->
                    val (devIdPart, datePart) = key.split("/")
                    val fileName = "${devIdPart}_${datePart}.json"
                    val normalized = records.map {
                        com.wxn.reader.data.dto.ReadingActiveEntity(
                            date = it.date,
                            deviceId = devIdPart,
                            readingTime = it.readingTime,
                        )
                    }
                    val raRecords = normalized.map { mapper.toReadingActivityRecord(it) }
                    writeZipEntry(
                        zos,
                        "reading_activities/$fileName",
                        json.encodeToString(raRecords),
                    )
                }

                // manifest(最后写,带 sourceDeviceHlc,严重-1)
                val sourceDeviceHlc = hlc.now()
                manifest = BackupManifest(
                    schemaVersion = CURRENT_BACKUP_SCHEMA,
                    appVersion = try {
                        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
                    } catch (e: Exception) {
                        "unknown"
                    },
                    createdAt = sourceDeviceHlc.l,
                    deviceName = android.os.Build.MODEL,
                    deviceId = localDeviceId,
                    sourceDeviceHlc = sourceDeviceHlc,
                    coverCount = coverSyncCount,
                    coverTotalBytes = coverSyncTotalBytes,
                    counts = BackupCounts(
                        books = exportedBookCount,
                        notes = noteTotal,
                        annotations = annotationTotal,
                        bookmarks = bookmarkTotal,
                        shelves = shelvesTotal,
                        bookShelfRelations = relationsTotal,
                        vocabulary = vocabTotal,
                        readingActivities = raTotal,
                    ),
                )
                writeZipEntry(zos, "manifest.json", json.encodeToString(manifest))

                emitter.update(BackupPhase.WRITING_SAF, R.string.backup_detail_writing, progress = null)
            }

            val dir = DocumentFile.fromTreeUri(ctx, targetUri) ?: run {
                val r = BackupResult.Failed(BackupErrorCode.SAF_WRITE_FAILED, "无法解析备份目录")
                emitter.finish(r, isRestore = false);
                return@withContext r
            }
            val filename = "handyreader-backup-${System.currentTimeMillis()}.zip"
            val file = dir.createFile("application/zip", filename) ?: run {
                val r = BackupResult.Failed(BackupErrorCode.SAF_WRITE_FAILED, "无法创建备份文件")
                emitter.finish(r, isRestore = false);
                return@withContext r
            }

            // 3. ★ 一般-F:copy 到 SAF,buffer 64KB
            ctx.contentResolver.openOutputStream(file.uri).use { out ->
                if (out == null) {
                    val r = BackupResult.Failed(BackupErrorCode.SAF_WRITE_FAILED, "无法写入目标 URI")
                    emitter.finish(r, isRestore = false)
                    return@withContext r
                }
                zipFile.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                    }
                }
            }
            zipFile.delete()

            val r =  manifest?.let {
                BackupResult.Success(manifest, skippedBooks.toList())
            } ?: BackupResult.Failed(BackupErrorCode.SAF_WRITE_FAILED, "")
            emitter.finish(r, isRestore = false)
            r
        } catch (e: kotlinx.coroutines.CancellationException) {
            // ★ C3:取消向上传播,不当作 Failed;emitter 已被 cancelPending 置 Idle/Cancelled
            zipFile.delete()
            throw e
        } catch (e: Exception) {
            Logger.e("BackupExporter: export failed: ${e.message}")
            zipFile.delete()
            val r = BackupResult.Failed(BackupErrorCode.fromException(e), e.message ?: "unknown")
            emitter.finish(r, isRestore = false)
            r
        }
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val crc = CRC32().apply { update(bytes) }.value

        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            setCrc(crc)
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
        }

        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    /**
     * ★ 封面同步 Step 2.2:把封面二进制写入 ZIP entry(STORED 无压缩)。
     *
     * 红线 #3 + #9:两遍流式实现。
     * - 第一遍:8KB buffer 流式读算 CRC32 + size(不全部入内存)。
     * - 第二遍:复用 [CoverSyncIO.copyToCancellable] 流式写入,每 buffer 检查协程取消。
     *
     * ZIP STORED 模式要求 putNextEntry 前给定 CRC32+size,故需两遍扫描;
     * 单封面 50-500KB,两遍共 ~1ms IO,可接受。
     *
     * @return 写入字节数;若文件不存在返回 0。
     */
    private suspend fun writeCoverZipEntry(
        zos: ZipOutputStream,
        name: String,
        srcFile: java.io.File,
    ): Long {
        if (!srcFile.exists()) return 0L
        // 第一遍:算 CRC + size
        val crc = CRC32()
        var size = 0L
        srcFile.inputStream().use { input ->
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                crc.update(buf, 0, n)
                size += n
            }
        }
        // 第二遍:写 entry(可取消)
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            setCrc(crc.value)
            this.size = size
            compressedSize = size
        }
        zos.putNextEntry(entry)
        srcFile.inputStream().use { input ->
            CoverSyncIO.copyToCancellable(input, zos)
        }
        zos.closeEntry()
        return size
    }

    /** 备份前兜底补算(串行,不并行,瓶颈在 IO)。 */
    private suspend fun ensureAllContentHashes(emitter: BackupProgressEmitter): HashCheck {
        // ★ A+++ 一般-3:用构造函数注入的 contentHashCalculator(原为局部 new)
        // ★ A+++ 严重-6:用 getActiveBookIds(只查 deleted=0 AND importStatus=0),避免反复处理已 deduped / 软删行
        val inaccessible = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val bookIds = bookDao.getActiveBookIds()
        var nullCount = 0
        bookIds.forEach { id ->
            val proj = bookDao.getContentHashAndFileType(id)
            if (proj != null && proj.contentHash == null) nullCount++
        }
        if (nullCount == 0) return HashCheck.Ok

        emitter.update(BackupPhase.COMPUTING_HASH, R.string.backup_detail_computing_hash, 0, nullCount, progress = 0f)
        var i = 0
        bookIds.forEach { id ->
            val proj = bookDao.getContentHashAndFileType(id) ?: return@forEach
            if (proj.contentHash != null) return@forEach
            when (contentHashCalculator.ensureContentHash(id)) {
                is ContentHashCalculator.EnsureHashResult.Ok -> {}
                ContentHashCalculator.EnsureHashResult.Deduped -> {}  // ★ A+++ 严重-1:被去重,不当失败
                ContentHashCalculator.EnsureHashResult.Inaccessible -> {
                    val book = bookDao.getBookByIdIncludeDeleted(id)
                    inaccessible.add(book?.title ?: "bookId=$id")
                }
                ContentHashCalculator.EnsureHashResult.HashFailed -> {
                    val book = bookDao.getBookByIdIncludeDeleted(id)
                    failed.add(book?.title ?: "bookId=$id")
                }
            }
            i++
            emitter.update(
                BackupPhase.COMPUTING_HASH,
                R.string.backup_detail_computing_hash,
                i,
                nullCount,
                // ★ A+++ 一般-7:进度 clamp 到 [0,1],防 deduped 导致 i > nullCount 时进度超 100%
                progress = (i.toFloat() / nullCount.coerceAtLeast(1)).coerceAtMost(1f),
            )
        }
        return when {
            inaccessible.size + failed.size >= nullCount -> HashCheck.Failed
            inaccessible.isEmpty() && failed.isEmpty() -> HashCheck.Ok
            else -> HashCheck.Partial(inaccessible, failed)
        }
    }

    /** 粗估 ZIP 大小(每本书 ~5KB + 每条标注 ~500B + activities ~100B/条 + 封面字节数)。 */
    private fun estimateZipSize(bookCount: Int, raCount: Int, coverBytes: Long): Long {
        val perBook = 5L * 1024
        val perRa = 100L
        return (bookCount * perBook + raCount * perRa + coverBytes).coerceAtLeast(1024 * 1024)
    }

    /** ★ 封面同步 M10:遍历所有书的 coverPath,stat 累加实际封面字节数(用于容量预估)。 */
    private suspend fun estimateCoverBytes(bookIds: List<Long>): Long {
        var total = 0L
        bookIds.forEach { id ->
            val book = bookDao.getBookByIdIncludeDeleted(id) ?: return@forEach
            val cp = book.coverPath ?: return@forEach
            if (cp.isEmpty()) return@forEach
            val f = File(cp)
            if (f.exists()) total += f.length()
        }
        return total
    }

    private inline fun <reified T> Json.encodeToString(value: T): String =
        encodeToString(serializer(), value)
}
