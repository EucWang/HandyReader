package com.wxn.reader.data.repository

import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult.Page
import androidx.paging.PagingState
import com.wxn.base.bean.Book
import com.wxn.reader.data.dto.BookEntity
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.mapper.book.BookMapper
import com.wxn.reader.data.model.SortOption
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.base.util.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/***
 * 从数据库查询出来的数据返回的如果直接就是PagingSource, 则需要从BookEntity 到 Book 到一次转换
 */
class BookPagingSource(val bookDao: BookDao,
                       val bookMapper: BookMapper,
                       val sortOption: SortOption,
                       val isAscending: Boolean,
                       val readingStatuses: Set<ReadingStatus>,
                       val fileTypes: Set<FileType>,
    ): PagingSource<Int, Book>() {

    private val source = bookDao.getAllBooksSorted(
        sortOption.name.lowercase(),
        isAscending,
        readingStatuses = readingStatuses.toList().takeIf { it.isNotEmpty() },
        fileTypes = fileTypes.toList().takeIf { it.isNotEmpty() }
    )

    override fun getRefreshKey(state: PagingState<Int, Book>): Int? {
        val job = SupervisorJob()
        val dispatcher = Dispatchers.IO
        val exceptionHandler = CoroutineExceptionHandler{
                ctx, throwable ->
            Logger.e(throwable)
        }
        val result: List<Page<Int, BookEntity>> = runBlocking(job + dispatcher + exceptionHandler) {
            val pages = arrayListOf<Page<Int,BookEntity>>()
            for(page in state.pages) {
                val data = page.data.map { item ->
                    bookMapper.toBookEntity(item)
                }
                pages.add(
                    Page(
                        data = data,
                        prevKey = page.prevKey,
                        nextKey = page.nextKey,
                        itemsBefore = page.itemsBefore,
                        itemsAfter = page.itemsAfter
                    )
                )
            }
            pages
        }
        val innerState = PagingState(
            pages = result,
            anchorPosition = state.anchorPosition,
            config = state.config,
            leadingPlaceholderCount = 0
        )
        return source.getRefreshKey(innerState)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Book> {
        val result = source.load(params)
        return when(result) {
            is LoadResult.Invalid -> {
                LoadResult.Invalid()
            }
            is LoadResult.Error -> {
                LoadResult.Error(result.throwable)
            }
            is Page -> {
                val items = result.data.map {
                    bookMapper.toBook(it)
                }
                Page(
                    data = items,
                    prevKey = result.prevKey,
                    nextKey = result.nextKey,
                    itemsBefore = result.itemsBefore,
                    itemsAfter = result.itemsAfter
                )
            }
        }
    }
}