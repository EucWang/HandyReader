package com.wxn.reader.data.source.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wxn.reader.data.dto.BookEntity
import com.wxn.reader.data.dto.BookListItemEntity
import com.wxn.reader.data.dto.ReadingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE deleted = 0 AND importStatus = 0")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query(
        """
        SELECT * FROM books 
        WHERE deleted = 0 AND importStatus = 0
        AND (:allStatus == 1 OR readingStatus IN (:readingStatuses))
        AND (:allFileTypes == 1 OR fileType IN (:fileTypes))
        ORDER BY 
        CASE WHEN :sortBy = 'last_opened' AND :isAsc = 1 THEN lastOpened END ASC, 
        CASE WHEN :sortBy = 'last_opened' AND :isAsc = 0 THEN lastOpened END DESC, 
        CASE WHEN :sortBy = 'last_added' AND :isAsc = 1 THEN id END ASC, 
        CASE WHEN :sortBy = 'last_added' AND :isAsc = 0 THEN id END DESC, 
        CASE WHEN :sortBy = 'title' AND :isAsc = 1 THEN title END ASC, 
        CASE WHEN :sortBy = 'title' AND :isAsc = 0 THEN title END DESC, 
        CASE WHEN :sortBy = 'author' AND :isAsc = 1 THEN authors END ASC, 
        CASE WHEN :sortBy = 'author' AND :isAsc = 0 THEN authors END DESC,
        CASE WHEN :sortBy = 'rating' AND :isAsc = 1 THEN rating END ASC, 
        CASE WHEN :sortBy = 'rating' AND :isAsc = 0 THEN rating END DESC, 
        CASE WHEN :sortBy = 'progression' AND :isAsc = 1 THEN progression END ASC, 
        CASE WHEN :sortBy = 'progression' AND :isAsc = 0 THEN progression END DESC
        """
    )
    fun getAllBooksSorted(
        sortBy: String,
        isAsc: Boolean,
        readingStatuses: List<ReadingStatus>?,
        allStatus: Int,
        fileTypes: List<String>?,
        allFileTypes: Int
    ): PagingSource<Int, BookEntity>

    @Deprecated(
        message = "存在 fileTypes 多元素时 row value misused 的潜在崩溃风险，请改用 getBooksSortedLite",
        replaceWith = ReplaceWith("getBooksSortedLite(sortBy, isAsc, readingStatuses, allStatus, fileTypes, allFileTypes)")
    )
    @Query(
        """
        SELECT * FROM books
        WHERE deleted = 0 AND importStatus = 0
        AND (:allStatus == 1 OR readingStatus IN (:readingStatuses))
        AND (:fileTypes IS NULL OR fileType IN (:fileTypes))
        ORDER BY
        CASE WHEN :sortBy = 'last_opened' AND :isAsc = 1 THEN lastOpened END ASC,
        CASE WHEN :sortBy = 'last_opened' AND :isAsc = 0 THEN lastOpened END DESC,
        CASE WHEN :sortBy = 'last_added' AND :isAsc = 1 THEN id END ASC,
        CASE WHEN :sortBy = 'last_added' AND :isAsc = 0 THEN id END DESC,
        CASE WHEN :sortBy = 'title' AND :isAsc = 1 THEN title END ASC,
        CASE WHEN :sortBy = 'title' AND :isAsc = 0 THEN title END DESC,
        CASE WHEN :sortBy = 'author' AND :isAsc = 1 THEN authors END ASC,
        CASE WHEN :sortBy = 'author' AND :isAsc = 0 THEN authors END DESC,
        CASE WHEN :sortBy = 'rating' AND :isAsc = 1 THEN rating END ASC,
        CASE WHEN :sortBy = 'rating' AND :isAsc = 0 THEN rating END DESC,
        CASE WHEN :sortBy = 'progression' AND :isAsc = 1 THEN progression END ASC,
        CASE WHEN :sortBy = 'progression' AND :isAsc = 0 THEN progression END DESC
        """
    )
    fun getBooksSorted(
        sortBy: String,
        isAsc: Boolean,
        readingStatuses: List<ReadingStatus>?,
        allStatus: Int,
        fileTypes: List<String>?
    ): Flow<List<BookEntity>>


    @Query("SELECT * FROM books WHERE deleted = 1 AND importStatus = 0")
    fun getDeletedBooks(): Flow<List<BookEntity>>

    @Query("SELECT uri FROM books")
    suspend fun getAllBookUris(): List<String>

    @Query("SELECT * FROM books WHERE uri = :uri")
    fun getBookByUri(uri: String): BookEntity?

    @Query("SELECT * FROM books WHERE uri LIKE :dirUriPrefix || '%'")
    suspend fun getBooksByUriPrefix(dirUriPrefix: String): List<BookEntity>

    @Query("SELECT count(*) FROM books WHERE uri LIKE :dirUriPrefix || '%'")
    suspend fun getBookCountByUriPrefix(dirUriPrefix: String): Int

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: Long): BookEntity?

    @Query("SELECT * FROM books WHERE id IN (:bookIds)")
    suspend fun getBooksByIds(bookIds: List<Long>): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertBooks(books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBooksIgnoreConflict(books: List<BookEntity>): List<Long>

    @Transaction
    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("DELETE FROM books WHERE uri = :bookUri")
    fun deleteBookByUri(bookUri: String)

    @Query("SELECT locator FROM books WHERE id = :bookId")
    fun getReadingProgress(bookId: Long): String

    @Query("UPDATE books SET locator = :locator, progression = :progression WHERE id = :bookId")
    fun setReadingProgress(bookId: Long, locator: String, progression: Float)

    @Query("UPDATE books SET readingStatus = :status WHERE id = :bookId")
    suspend fun setReadingStatus(bookId: Long, status: ReadingStatus)

    /**
     * 原子性增加阅读时间
     * 使用 SQL 的增量更新避免读-修改-写竞态条件
     * @param bookId 书籍ID
     * @param delta 增量（毫秒）
     * @return 影响的行数
     */
    @Query("UPDATE books SET readingTime = readingTime + :delta WHERE id = :bookId")
    suspend fun incrementReadingTime(bookId: Long, delta: Long): Int

    /** 覆盖设置 readingTime(派生值重算后调用)。由 [BooksRepositoryImpl] 和 [SyncMergeEngine] 使用。 */
    @Query("UPDATE books SET readingTime = :sum WHERE id = :id")
    suspend fun updateReadingTime(id: Long, sum: Long): Int

    // ============ 选择性更新方法 ============

    /**
     * 只更新阅读进度相关字段，不覆盖 readingTime
     */
    @Query("""
         UPDATE books 
         SET lastOpened = :lastOpened, 
             scrollIndex = :scrollIndex, 
             scrollOffset = :scrollOffset, 
             progression = :progression 
         WHERE id = :bookId
     """)
    suspend fun updateProgressFields(
        bookId: Long,
        lastOpened: Long,
        scrollIndex: Int,
        scrollOffset: Int,
        progression: Float
    ): Int

    /**
     * 只更新阅读状态
     */
    @Query("UPDATE books SET readingStatus = :status WHERE id = :bookId")
    suspend fun updateReadingStatus(bookId: Long, status: ReadingStatus): Int

    /**
     * 更新开始阅读时间
     */
    @Query("UPDATE books SET startReadingDate = :startDate WHERE id = :bookId")
    suspend fun updateStartReadingDate(bookId: Long, startDate: Long): Int

    /**
     * 更新结束阅读时间和状态
     */
    @Query("""
        UPDATE books 
        SET endReadingDate = :endDate, 
            readingStatus = :status 
        WHERE id = :bookId
    """)
    suspend fun updateEndReadingDateAndStatus(
        bookId: Long, 
        endDate: Long, 
        status: ReadingStatus
    ): Int

    /**
     * 更新书总字数
     */
    @Query("UPDATE books SET wordCount = :wordCount WHERE id = :bookId")
    suspend fun updateWordCount(bookId: Long, wordCount: Long): Int

    /**
     * 更新 PDF 阅读进度字段（locator, progression, readingStatus, endReadingDate）
     */
    @Query("""
        UPDATE books 
        SET locator = :locator, 
            progression = :progression, 
            readingStatus = :readingStatus, 
            endReadingDate = :endReadingDate 
        WHERE id = :bookId
    """)
    suspend fun updatePdfProgressFields(
        bookId: Long,
        locator: String,
        progression: Float,
        readingStatus: ReadingStatus,
        endReadingDate: Long?
    ): Int

    /**
     * 更新删除标志
     */
    @Query("UPDATE books SET deleted = :deleted WHERE id = :bookId")
    suspend fun updateDeletedFlag(bookId: Long, deleted: Boolean): Int

    @Query("SELECT * FROM books WHERE documentId = :documentId LIMIT 1")
    suspend fun getBookByDocumentId(documentId: String): BookEntity?

    @Query("SELECT * FROM books WHERE crc = :crc AND crc != 0 AND deleted = 0 LIMIT 1")
    suspend fun getBookByCrc(crc: Int): BookEntity?

    /**
     * 按 contentHash 查 sync_orphan 行(orphan 提升主匹配)。
     *
     * 导入侧(scan / FAB import)的 FileParserImpl 对所有格式都计算 SHA-256 contentHash，
     * orphan 行在还原时也写入了 contentHash，故 contentHash 是两端都可用且最可靠的匹配键。
     */
    @Query(
        """
        SELECT id FROM books
        WHERE contentHash = :hash
          AND source = 'sync_orphan'
          AND deleted = 0
        LIMIT 1
        """
    )
    suspend fun getOrphanBookIdByContentHash(hash: String): Long?

    /**
     * 按 crc + fileType 两键查 sync_orphan 行(旧备份 orphan 无 contentHash 时的兜底匹配)。
     *
     * 仅当 orphan 行 contentHash 为 null 时使用（极旧备份导出的数据）。
     * CRC32 碰撞概率极低(~2^-32)，配合 fileType 联合过滤可忽略。
     */
    @Query(
        """
        SELECT id FROM books
        WHERE crc = :crc
          AND fileType = :fileType
          AND source = 'sync_orphan'
          AND deleted = 0
        LIMIT 1
        """
    )
    suspend fun getOrphanBookIdByCrcFileType(crc: Int, fileType: String): Long?

    /**
     * 提升 orphan 行为正常书籍:填回文件信息及 hash 字段,标记为已激活。
     *
     * ★ 不触碰 locator/progression/scrollIndex/scrollOffset/readingStatus/9 个 HLC 字段——
     *   同步阶段带来的进度数据与同步状态完整保留。
     * ★ contentHash/crc/partialMd5 使用 COALESCE 语义:仅在 orphan 原值为空/0 时由导入侧回填，
     *   以保留还原带进来的同步数据。
     */
    @Query(
        """
        UPDATE books SET
            uri = :uri,
            source = :source,
            documentId = :documentId,
            fileType = :fileType,
            deleted = 0,
            coverPath = :coverPath,
            contentHash = COALESCE(contentHash, :contentHash),
            partialMd5 = COALESCE(partialMd5, :partialMd5),
            crc = CASE WHEN crc = 0 THEN :crc ELSE crc END
        WHERE id = :id
        """
    )
    suspend fun promoteOrphanBook(
        id: Long,
        uri: String,
        source: String,
        documentId: String?,
        fileType: String,
        coverPath: String?,
        contentHash: String?,
        crc: Int,
        partialMd5: String?,
    )

    @Query("SELECT * FROM books WHERE documentId IS NULL")
    suspend fun getBooksWithNullDocumentId(): List<BookEntity>

    @Query("UPDATE books SET documentId = :documentId WHERE id = :bookId")
    suspend fun updateDocumentId(bookId: Long, documentId: String)

    @Query(
        """
        SELECT id, uri, fileType, title, authors, coverPath, progression, lastOpened,
               deleted, rating, isFavorite, favoriteDate, readingStatus, readingTime,
               startReadingDate, endReadingDate, importStatus, source, documentId,
               subjects, publishDate, publisher, language, numberOfPages, narrator, duration
        FROM books
        WHERE deleted = 0 AND importStatus = 0
        AND (:allStatus == 1 OR readingStatus IN (:readingStatuses))
        AND (:allFileTypes == 1 OR fileType IN (:fileTypes))
        ORDER BY
        CASE WHEN :sortBy = 'last_opened' AND :isAsc = 1 THEN lastOpened END ASC,
        CASE WHEN :sortBy = 'last_opened' AND :isAsc = 0 THEN lastOpened END DESC,
        CASE WHEN :sortBy = 'last_added' AND :isAsc = 1 THEN id END ASC,
        CASE WHEN :sortBy = 'last_added' AND :isAsc = 0 THEN id END DESC,
        CASE WHEN :sortBy = 'title' AND :isAsc = 1 THEN title END ASC,
        CASE WHEN :sortBy = 'title' AND :isAsc = 0 THEN title END DESC,
        CASE WHEN :sortBy = 'author' AND :isAsc = 1 THEN authors END ASC,
        CASE WHEN :sortBy = 'author' AND :isAsc = 0 THEN authors END DESC,
        CASE WHEN :sortBy = 'rating' AND :isAsc = 1 THEN rating END ASC,
        CASE WHEN :sortBy = 'rating' AND :isAsc = 0 THEN rating END DESC,
        CASE WHEN :sortBy = 'progression' AND :isAsc = 1 THEN progression END ASC,
        CASE WHEN :sortBy = 'progression' AND :isAsc = 0 THEN progression END DESC
        """
    )
    fun getBooksSortedLite(
        sortBy: String,
        isAsc: Boolean,
        readingStatuses: List<ReadingStatus>?,
        allStatus: Int,
        fileTypes: List<String>?,
        allFileTypes: Int
    ): Flow<List<BookListItemEntity>>

    @Query("UPDATE books SET lastOpened = :lastOpened WHERE id = :bookId")
    suspend fun updateLastOpened(bookId: Long, lastOpened: Long): Int

    @Query("UPDATE books SET rating = :rating WHERE id = :bookId")
    suspend fun updateRating(bookId: Long, rating: Float): Int

    @Query("UPDATE books SET startReadingDate = :startDate WHERE id = :bookId")
    suspend fun updateStartReadingDateOnly(bookId: Long, startDate: Long?): Int

    @Query("UPDATE books SET endReadingDate = :endDate WHERE id = :bookId")
    suspend fun updateEndReadingDateOnly(bookId: Long, endDate: Long?): Int

    @Query("""
        UPDATE books SET
            readingStatus = :status,
            startReadingDate = :startDate,
            endReadingDate = :endDate,
            readingTime = :readingTime,
            progression = :progression
        WHERE id = :bookId
    """)
    suspend fun updateReadingStatusFull(
        bookId: Long,
        status: ReadingStatus,
        startDate: Long?,
        endDate: Long?,
        readingTime: Long,
        progression: Float
    ): Int

    @Query("SELECT * FROM books WHERE id = :bookId AND deleted = 0")
    fun getBookByIdFlow(bookId: Long): Flow<BookEntity?>

    @Query(
        """
        SELECT id, uri, fileType, title, authors, coverPath, progression, lastOpened,
               deleted, rating, isFavorite, favoriteDate, readingStatus, readingTime,
               startReadingDate, endReadingDate, importStatus, source, documentId,
               subjects, publishDate, publisher, language, numberOfPages, narrator, duration
        FROM books
        WHERE deleted = 0 AND importStatus = 0 AND lastOpened IS NOT NULL
        ORDER BY lastOpened DESC
        LIMIT :limit
        """
    )
    fun getRecentlyReadBooksLite(limit: Int): Flow<List<BookListItemEntity>>

    @Query(
        """
        SELECT id, uri, fileType, title, authors, coverPath, progression, lastOpened,
               deleted, rating, isFavorite, favoriteDate, readingStatus, readingTime,
               startReadingDate, endReadingDate, importStatus, source, documentId,
               subjects, publishDate, publisher, language, numberOfPages, narrator, duration
        FROM books
        WHERE deleted = 0 AND importStatus = 0
        ORDER BY id DESC
        LIMIT :limit
        """
    )
    fun getRecentlyAddedBooksLite(limit: Int): Flow<List<BookListItemEntity>>

    @Query(
        """
        SELECT id, uri, fileType, title, authors, coverPath, progression, lastOpened,
               deleted, rating, isFavorite, favoriteDate, readingStatus, readingTime,
               startReadingDate, endReadingDate, importStatus, source, documentId,
               subjects, publishDate, publisher, language, numberOfPages, narrator, duration
        FROM books
        WHERE deleted = 0 AND importStatus = 0 AND isFavorite = 1
        ORDER BY favoriteDate DESC
        LIMIT :limit
        """
    )
    fun getFavoriteBooksLite(limit: Int): Flow<List<BookListItemEntity>>

    @Query("UPDATE books SET isFavorite = :isFavorite, favoriteDate = :favoriteDate WHERE id = :bookId")
    suspend fun updateFavorite(bookId: Long, isFavorite: Boolean, favoriteDate: Long?)

    // ===== ★ 同步方案 v1.5 §3.2.2 一期新增:books 表三档 HLC(无 scope 参数的专用方法)=====
    //   markDirty 的 when(scope) 分派与此处三个专用方法对齐(C1)。
    /** reading 档:推进 syncHlcL/C/Device。 */
    @Query("UPDATE books SET syncHlcL = :l, syncHlcC = :c, syncHlcDevice = :d WHERE id = :id")
    suspend fun updateReadingHlc(id: Long, l: Long, c: Int, d: String)

    /** user 档:推进 userHlcL/C/Device。 */
    @Query("UPDATE books SET userHlcL = :l, userHlcC = :c, userHlcDevice = :d WHERE id = :id")
    suspend fun updateUserHlc(id: Long, l: Long, c: Int, d: String)

    /** meta 档:推进 metaHlcL/C/Device。 */
    @Query("UPDATE books SET metaHlcL = :l, metaHlcC = :c, metaHlcDevice = :d WHERE id = :id")
    suspend fun updateMetaHlc(id: Long, l: Long, c: Int, d: String)

    /**
     * 更新 contentHash(导入时 / 后台静默补算时)。允许传 null 用于清空。
     *
     * ★ A+++ 严重-3:加 WHERE importStatus=0 防御,避免极端并发下被并发 markDeduped 后
     *   UPDATE 把 NULL 覆盖回 hash 破坏 dedupe 语义。返回影响行数(0=被并发 dedupe / 已 dedupe / 不存在)。
     */
    @Query("UPDATE books SET contentHash = :hash WHERE id = :id AND importStatus = 0")
    suspend fun updateContentHash(id: Long, hash: String?): Int

    /** 取 fileType + contentHash(stableId 派生 + 补算前预查)。 */
    @Query("SELECT fileType, contentHash FROM books WHERE id = :id")
    suspend fun getContentHashAndFileType(id: Long): FileTypeHashProjection?

    /**
     * ★ 2026-07-07 清理 3:仅取 fileType 的轻量查询。
     * 用于 incrementReadingTime 等只需 fileType 区分音频/电子书、不需要 contentHash 的场景。
     * 比 getContentHashAndFileType 少读一列(64 字符 string),消除"取 hash 但不用"的语义错位。
     */
    @Query("SELECT fileType FROM books WHERE id = :id")
    suspend fun getFileType(id: Long): String?

    /** ★ 同步入口过滤:取单本书 importStatus(SyncableBooksRepository.markDirty 用,
     *  排除 importStatus != 0 的失败占位行进入 sync_queue)。 */
    @Query("SELECT importStatus FROM books WHERE id = :id")
    suspend fun getImportStatusById(id: Long): Int?

    /**
     * 按 contentHash 取 bookId(单行命中场景)。配合 UNIQUE 部分索引 `idx_books_content_hash`。
     * 返回 List 以兼容历史数据建索引前多行;调用方多行命中按 lastOpened DESC 取最新(§6.2)。
     */
    @Query("SELECT id FROM books WHERE contentHash = :hash")
    suspend fun getBookIdsByContentHash(hash: String): List<Long>

    /**
     * 按 contentHash 查活行(deleted=0 AND importStatus=0 AND 非 orphan)。
     * 用于扫描/导入去重预查，避免命中已软删/失败占位行导致新扫描书被误判为重复。
     *
     * 排除 source = 'sync_orphan'：orphan 行仅有来自备份的元数据而无本地文件，
     * 不应被视为"活跃重复书"而跳过扫描导入。扫描导入应触发 orphan 提升机制
     * （见 BooksRepositoryImpl.insertBook），而非被预查拦截。
     */
    @Query("SELECT id FROM books WHERE contentHash = :hash AND deleted = 0 AND importStatus = 0 AND source != 'sync_orphan'")
    suspend fun getActiveBookIdsByContentHash(hash: String): List<Long>

    /**
     * keepId 三级优先级决断(同 contentHash 多行保留谁):
     *   ① lastOpened 非空优先 ② lastOpened 较新优先 ③ id 较小优先(先导入的)
     * 仅在活行(deleted=0 AND importStatus=0)中选。
     */
    @Query("""
        SELECT id FROM books
        WHERE id IN (:ids) AND deleted = 0 AND importStatus = 0
        ORDER BY (lastOpened IS NOT NULL) DESC, lastOpened DESC, id ASC
        LIMIT 1
    """)
    suspend fun resolveKeepIdAmong(ids: List<Long>): Long?

    /**
     * 原子标记去重行:importStatus=-1(失败占位语义,不参与同步/备份/展示)
     * + contentHash=NULL(避免 UNIQUE 索引/未来启用索引时冲突) + source='deduped'(区分于真失败)。
     */
    @Query("UPDATE books SET importStatus = -1, contentHash = NULL, source = 'deduped' WHERE id = :id")
    suspend fun markBookAsDeduped(id: Long)

    /**
     * ★ A+++ 一般-1:批量原子标记去重行(性能优化,单 SQL 替代 forEach N 次)。
     * 加 AND importStatus=0 防御,避免重复 dedupe 已 deduped 的行(幂等)。
     * 返回影响行数(可用于统计)。
     */
    @Query(
        "UPDATE books SET importStatus = -1, contentHash = NULL, source = 'deduped' " +
            "WHERE id IN (:ids) AND importStatus = 0"
    )
    suspend fun markBooksAsDeduped(ids: List<Long>): Int

    /** 多行命中兜底:在给定 ids 内取 lastOpened 最新的一行(§6.2 resolveLocalBookId)。 */
    @Query("SELECT id FROM books WHERE id IN (:ids) ORDER BY lastOpened DESC LIMIT 1")
    suspend fun getLatestOpenedBookIdAmong(ids: List<Long>): Long?

    /** 备份/还原:取所有 bookId(含软删)。 */
    @Query("SELECT id FROM books")
    suspend fun getAllBookIdsIncludeDeleted(): List<Long>

    /**
     * ★ A+++ 严重-6:取需要补算 contentHash 的活行(deleted=0 AND importStatus=0)。
     * 用于 EnsureContentHashWorker / BackupExporter.ensureAllContentHashes 入口,
     * 避免反复处理已 deduped(importStatus=-1)/ 软删(deleted=1)的书,浪费 sha256 IO。
     */
    @Query("SELECT id FROM books WHERE deleted = 0 AND importStatus = 0")
    suspend fun getActiveBookIds(): List<Long>

    /** 备份:取单本书(含软删)。 */
    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookByIdIncludeDeleted(bookId: Long): BookEntity?

    /** 备份/还原 diff:取 books 总数(含软删)。 */
    @Query("SELECT COUNT(*) FROM books")
    suspend fun getCountIncludeDeleted(): Int

    /**
     * diff:计数 contentHash 命中(精确过滤:活行 + 非占位 + 非 orphan)。
     *
     * ★ P1-2 修复:原查询含 orphan/deleted/importStatus!=0 的行,导致 matched 偏高、newOrphan 偏低,
     *   确认框数字与实际创建 orphan 数不符,误导用户决策。
     */
    @Query(
        """
        SELECT COUNT(*) FROM books
        WHERE contentHash IN (:hashes)
          AND deleted = 0 AND importStatus = 0 AND source != 'sync_orphan'
        """
    )
    suspend fun countByContentHashInRaw(hashes: List<String>): Int

    /** diff:计数 active 行命中 contentHash(用于墓碑统计)。 */
    @Query("SELECT COUNT(*) FROM books WHERE contentHash IN (:hashes) AND deleted = 0")
    suspend fun countActiveByContentHashInRaw(hashes: List<String>): Int

    // ===== ★ v12 TXT 统一字节偏移方案（plan-txt-unify-byte-offset.md §3.3.3 / §3.5.1 / §3.5.2）=====

    /** 读取 TXT 字符编码名（BookEntity.txtCharset）。null 表示尚未回填。 */
    @Query("SELECT txtCharset FROM books WHERE id = :bookId")
    suspend fun getTxtCharset(bookId: Long): String?

    /** 回填 TXT 字符编码名。允许传 null（理论不会用到，但保留以防误用）。 */
    @Query("UPDATE books SET txtCharset = :charset WHERE id = :bookId")
    suspend fun updateTxtCharset(bookId: Long, charset: String?)


    /**
     * ★ 重导入复用软删行时刷新文件层字段（uri/documentId/cover 可能随重新选择文件而变）。
     * 不触碰 contentHash / 进度字段 / HLC 字段（HLC 由 SyncableBooksRepository.insertBook
     * 的 markDirtyAll 统一推进）。
     */
    @Query("""
        UPDATE books SET
            uri = :uri,
            documentId = :documentId,
            coverPath = :coverPath
        WHERE id = :bookId
    """)
    suspend fun refreshBookFileFields(bookId: Long, uri: String, documentId: String?, coverPath: String?)

    /**
     * ★ 重导入时重置阅读进度（全新导入语义）：进度/位置/状态/时间全部归零。
     * 不触碰 contentHash / 文件字段 / HLC 字段。
     * HLC 由外层 SyncableBooksRepository.insertBook 的 markDirtyAll(id) 覆盖推进。
     */
    @Query("""
        UPDATE books SET
            locator = '',
            progression = 0,
            scrollIndex = 0,
            scrollOffset = 0,
            lastOpened = NULL,
            readingStatus = NULL,
            startReadingDate = NULL,
            endReadingDate = NULL
        WHERE id = :bookId
    """)
    suspend fun resetReadingProgress(bookId: Long)

    @Query("""
        UPDATE books SET
            readingTime = 0
        WHERE id = :bookId
    """)
    suspend fun resetAudioPlaybackPosition(bookId: Long)
}

/** [BookDao.getContentHashAndFileType] 投影 POJO。 */
data class FileTypeHashProjection(
    val fileType: String,
    val contentHash: String?,
)

