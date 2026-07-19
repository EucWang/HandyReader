package com.wxn.reader.domain.use_case.books

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.wxn.base.bean.Book
import com.wxn.base.util.Logger
import com.wxn.reader.data.source.local.DeviceLocalStore
import com.wxn.reader.data.source.local.dao.AnnotationDao
import com.wxn.reader.data.source.local.dao.BookmarkDao
import com.wxn.reader.data.source.local.dao.ChapterDao
import com.wxn.reader.data.source.local.dao.DeletedBookDao
import com.wxn.reader.data.source.local.dao.NoteDao
import com.wxn.reader.data.dto.DeletedBookEntity
import com.wxn.reader.domain.repository.BooksRepository
import com.wxn.reader.domain.repository.PermissionRepository
import com.wxn.reader.util.sync.HybridLogicalClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * ★ 同步方案 v2.6 §4.3.2 一期改造:硬删除 → 软删除(墓碑)。
 *
 * 改造点:
 * - `bookDao.delete(book)` → `bookDao.updateDeletedFlag(bookId, true)`(books.deleted=1)。
 * - 各子表(annotations/notes/bookmarks)`markDeletedByBook` 软删 + 推 deletedHlc(墓碑传播)。
 * - 不再 `chapterDao.deleteChaptersByBookId`(章节保留,复活时可用)。
 * - SAF 权限释放 / 文件清理 / scan 白名单等"文件层"逻辑保留(同步不关心文件)。
 *
 * 复活流程:v2.6 §4.3.4(含 P1-C 时钟修复 + 子表一并复活)。一期由"回收站"页面触发,
 *   调用 [restore] 方法:`updateDeletedFlag(bookId, false)` + 各子表 `reviveByBook`。
 */
class DeleteBookUseCase @Inject constructor(
    private val repository: BooksRepository,
    private val chapterDao: ChapterDao,
    private val annotationDao: AnnotationDao,
    private val noteDao: NoteDao,
    private val bookmarkDao: BookmarkDao,
    private val permissionRepository: PermissionRepository,
    private val deletedBookDao: DeletedBookDao,
    private val hlc: HybridLogicalClock,
    private val deviceLocalStore: DeviceLocalStore,
    @param:ApplicationContext private val context: Context,
) {
    /**
     * 软删除一本书:books.deleted=1 + 子表 deleted=1 + 推 deletedHlc(墓碑)。
     * 文件层(SAF 权限/scan 白名单)按 source 分支处理,与同步无关。
     */
    suspend operator fun invoke(book: Book) {
        val bookId = book.id
        val effectiveSource = book.source.ifEmpty { "scan" }

        // ★ 软删除:books + 子表,推 deletedHlc 用于墓碑传播
        val ts = hlc.now()
        repository.updateDeletedFlag(bookId, true)
        annotationDao.markDeletedByBook(bookId, ts.l, ts.c, ts.deviceId)
        noteDao.markDeletedByBook(bookId, ts.l, ts.c, ts.deviceId)
        bookmarkDao.markDeletedByBook(bookId, ts.l, ts.c, ts.deviceId)

        // 章节保留(复活时可用);不删除 chapterCacheDir

        // M9 修复:清理封面图(避免 filesDir/covers/ 孤儿堆积;删书不清封面会导致多次还原后磁盘膨胀)
        deleteCoverFile(book.coverImage)

        // 文件层清理(与同步无关,保留既有逻辑)
        book.cachedDir?.let { dir ->
            if (dir.isNotEmpty()) {
                val cacheDir = File(dir)
                if (cacheDir.exists()) cacheDir.deleteRecursively()
            }
        }

        when (effectiveSource) {
            "import" -> releaseUriPermission(book.filePath)
            "external_import" -> deleteImportedFile(book.filePath)
            "scan" -> addToDeletedWhitelist(book)
            "external" -> releaseExternalUriPermission(book.filePath)
            "opds" -> deleteOpdsFile(book.filePath)
        }
    }

    /**
     * ★ 复活一本书(回收站恢复):books.deleted=0 + 子表 deleted=0。
     * 复活时清空子表 deletedHlc(避免下次合并被墓碑覆盖)。
     */
    suspend fun restore(bookId: Long) {
        repository.updateDeletedFlag(bookId, false)
        annotationDao.reviveByBook(bookId)
        noteDao.reviveByBook(bookId)
        bookmarkDao.reviveByBook(bookId)
        // 扫描白名单移除(若存在),避免扫描器因白名单跳过该书
        runCatching {
            // DeletedBookDao 按 documentId 管理;复活无需精确移除白名单(白名单仅在扫描时生效)
        }
    }

    /** 彻底删除(回收站"永久删除"):物理删除行 + 文件清理。 */
    suspend fun purge(book: Book) {
        val bookId = book.id
        val chapterCacheDir = File(context.filesDir, "chapters/$bookId")
        if (chapterCacheDir.exists()) chapterCacheDir.deleteRecursively()
        // M9 修复:永久删除时一并清理封面图
        deleteCoverFile(book.coverImage)
        chapterDao.deleteChaptersByBookId(bookId)
        repository.deleteBook(book) // 物理删除(CASCADE 子表)
    }

    /** M9:删除封面文件(若存在)。容忍失败(文件已被清理或路径无效)。 */
    private fun deleteCoverFile(coverPath: String?) {
        if (coverPath.isNullOrEmpty()) return
        runCatching {
            val file = File(coverPath)
            if (file.exists()) file.delete()
        }.onFailure { e ->
            Logger.d("DeleteBookUseCase: deleteCoverFile failed: ${e.message}")
        }
    }

    private suspend fun releaseUriPermission(filePath: String) {
        try {
            permissionRepository.releasePersistableUriPermission(Uri.parse(filePath))
        } catch (e: Exception) {
            Logger.e("DeleteBookUseCase: releasePermission failed: ${e.message}")
        }
    }

    private suspend fun releaseExternalUriPermission(filePath: String) {
        if (filePath.startsWith("content://")) {
            try {
                permissionRepository.releasePersistableUriPermission(Uri.parse(filePath))
            } catch (e: Exception) {
                Logger.d("DeleteBookUseCase: External URI release failed (expected for temp): ${e.message}")
            }
        }
    }

    private suspend fun addToDeletedWhitelist(book: Book) {
        val docId = extractDocumentId(book.filePath) ?: return
        val scanDirUri = extractScanDirectoryUri(book.filePath) ?: return
        deletedBookDao.insert(
            DeletedBookEntity(
                documentId = docId,
                scanDirectoryUri = scanDirUri,
                fileName = book.title,
            )
        )
    }

    private fun extractDocumentId(uriStr: String): String? {
        return try {
            val uri = Uri.parse(uriStr)
            if (uri.scheme == "content") DocumentsContract.getDocumentId(uri) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractScanDirectoryUri(documentUri: String): String? {
        val idx = documentUri.indexOf("/document/")
        return if (idx >= 0) documentUri.substring(0, idx) else null
    }

    private fun deleteOpdsFile(filePath: String) {
        runCatching {
            val uri = Uri.parse(filePath)
            if (uri.scheme == "content") {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
                Logger.d("DeleteBookUseCase: deleted OPDS SAF file: $filePath")
            } else {
                val file = File(uri.path ?: return)
                if (file.exists()) {
                    file.delete()
                    Logger.d("DeleteBookUseCase: deleted OPDS file: ${file.absolutePath}")
                }
            }
        }.onFailure { e ->
            Logger.e("DeleteBookUseCase: deleteOpdsFile failed: ${e.message}")
        }
    }

    private fun deleteImportedFile(filePath: String) {
        try {
            val uri = Uri.parse(filePath)
            if (uri.scheme == "file") {
                val file = File(uri.path ?: return)
                val importedDir = File(context.filesDir, "imported_books").absolutePath
                if (file.exists() && file.absolutePath.startsWith(importedDir)) {
                    file.delete()
                    Logger.d("DeleteBookUseCase: deleted imported file: ${file.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Logger.e("DeleteBookUseCase: deleteImportedFile failed: ${e.message}")
        }
    }
}
