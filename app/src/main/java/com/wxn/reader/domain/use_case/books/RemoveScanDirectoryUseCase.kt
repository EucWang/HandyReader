package com.wxn.reader.domain.use_case.books

import android.content.Context
import android.net.Uri
import com.wxn.base.util.Logger
import com.wxn.reader.data.dto.BookEntity
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.ChapterDao
import com.wxn.reader.data.source.local.dao.DeletedBookDao
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.repository.PermissionRepository
import com.wxn.reader.service.TtsStateHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

sealed class DirectoryDeleteResult {
    data class Success(
        val totalBooks: Int,
        val deletedBooks: Int,
        val failedBooks: Int
    ) : DirectoryDeleteResult()

    data class TtsBlocked(val totalBooks: Int) : DirectoryDeleteResult()
    data object Empty : DirectoryDeleteResult()
}

class RemoveScanDirectoryUseCase @Inject constructor(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val deletedBookDao: DeletedBookDao,
    private val appPreferencesUtil: AppPreferencesUtil,
    private val permissionRepository: PermissionRepository,
    private val ttsStateHolder: TtsStateHolder,
    @param:ApplicationContext private val context: Context
) {
    sealed class DeleteProgress {
        data class Querying(val directory: String) : DeleteProgress()
        data class CheckingTts(val directory: String) : DeleteProgress()
        data class DeletingBook(
            val current: Int,
            val total: Int,
            val bookTitle: String
        ) : DeleteProgress()
        data class Completed(val result: DirectoryDeleteResult) : DeleteProgress()
    }

    suspend fun getBookCountInDirectory(directoryUri: String): Int {
        return bookDao.getBookCountByUriPrefix(directoryUri)
    }

    private suspend fun isTtsPlayingBookInDirectory(directoryUri: String): Boolean {
        val state = ttsStateHolder.state.value
        if (!state.isPlaying) return false
        val ttsBookUri = state.bookUri
        if (ttsBookUri.isNullOrEmpty()) return false
        return ttsBookUri.startsWith(directoryUri)
    }

    suspend fun execute(
        directoryUri: String,
        onProgress: (DeleteProgress) -> Unit
    ): DirectoryDeleteResult {
        onProgress(DeleteProgress.Querying(directoryUri))

        val books = bookDao.getBooksByUriPrefix(directoryUri)
        if (books.isEmpty()) {
            removeDirectoryPreference(directoryUri)
            return DirectoryDeleteResult.Empty
        }

        onProgress(DeleteProgress.CheckingTts(directoryUri))
        if (isTtsPlayingBookInDirectory(directoryUri)) {
            return DirectoryDeleteResult.TtsBlocked(totalBooks = books.size)
        }

        var deleted = 0
        var failed = 0

        books.forEachIndexed { index, bookEntity ->
            onProgress(DeleteProgress.DeletingBook(
                current = index + 1,
                total = books.size,
                bookTitle = bookEntity.title
            ))
            try {
                deleteSingleBook(bookEntity)
                deleted++
            } catch (e: Exception) {
                Logger.e("RemoveScanDirectoryUseCase: failed to delete book ${bookEntity.title}: ${e.message}")
                failed++
            }
        }

        removeDirectoryPreference(directoryUri)

        val result = DirectoryDeleteResult.Success(
            totalBooks = books.size,
            deletedBooks = deleted,
            failedBooks = failed
        )
        onProgress(DeleteProgress.Completed(result))
        return result
    }

    private suspend fun deleteSingleBook(bookEntity: BookEntity) {
        chapterDao.deleteChaptersByBookId(bookEntity.id)
        bookDao.delete(bookEntity)
        if (bookEntity.cachedDir.isNotEmpty()) {
            val cacheDir = File(bookEntity.cachedDir)
            if (cacheDir.exists()) cacheDir.deleteRecursively()
        }
        val chapterCacheDir = File(context.filesDir, "chapters/${bookEntity.id}")
        if (chapterCacheDir.exists()) chapterCacheDir.deleteRecursively()
    }

    private suspend fun removeDirectoryPreference(directoryUri: String) {
        val prefs = appPreferencesUtil.appPrefsFlow.first() ?: return
        val updated = prefs.scanDirectories - directoryUri
        if (updated != prefs.scanDirectories) {
            appPreferencesUtil.updateAppPreferences(prefs.copy(scanDirectories = updated))
            permissionRepository.releasePersistableUriPermission(Uri.parse(directoryUri))
            deletedBookDao.deleteByScanDirectoryUri(directoryUri)
            Logger.d("RemoveScanDirectoryUseCase: removed directory and released permission: $directoryUri")
        }
    }
}
