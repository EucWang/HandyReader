package com.wxn.reader.data.repository

import com.wxn.bookparser.parser.txt.TxtBookMetaStore
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.ChapterDao
import javax.inject.Inject

/**
 * [TxtBookMetaStore] 在 `app` 模块的实现。
 *
 * 桥接 `bookparser`（不依赖 `app`）和 `app` 模块的 Room DAO：
 * - [getCharset] / [updateCharset] → [BookDao.getTxtCharset] / [BookDao.updateTxtCharset]
 * - [getChaptersWithWordCount] → [ChapterDao.getChapterWordCountsByBookId]
 *
 * 单例作用域由 [com.wxn.reader.di.AppModule.provideTxtBookMetaStore] 的 `@Singleton` 控制
 * （对齐 ChaptersRepositoryImpl 模式：impl 类本身不带 @Singleton）。
 *
 * 详见 `docs/plans/plan-txt-unify-byte-offset.md` §3.3.3 / §3.5.1 / §3.5.2 / §10.1。
 */
class TxtBookMetaStoreImpl @Inject constructor(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
) : TxtBookMetaStore {

    override suspend fun getCharset(bookId: Long): String? {
        return bookDao.getTxtCharset(bookId)
    }

    override suspend fun updateCharset(bookId: Long, charset: String) {
        bookDao.updateTxtCharset(bookId, charset)
    }

    override suspend fun getChaptersWithWordCount(bookId: Long): List<Long> {
        return chapterDao.getChapterWordCountsByBookId(bookId)
    }
}
