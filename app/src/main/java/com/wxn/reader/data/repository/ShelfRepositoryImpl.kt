package com.wxn.reader.data.repository

import com.wxn.base.bean.Book
import com.wxn.reader.data.mapper.book.BookMapper
import com.wxn.reader.data.mapper.bookshelf.BookShelfMapper
import com.wxn.reader.data.mapper.shelf.ShelfMapper
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookShelfDao
import com.wxn.reader.data.source.local.dao.ShelfDao
import com.wxn.reader.domain.model.BookShelf
import com.wxn.reader.domain.model.Shelf
import com.wxn.reader.domain.repository.ShelfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ShelfRepositoryImpl @Inject constructor(
    private val shelfDao: ShelfDao,
    private val bookShelfDao: BookShelfDao,
    private val bookDao: BookDao,

    private val shelfMapper: ShelfMapper,
    private val bookMapper: BookMapper,
    private val bookshelfMapper: BookShelfMapper

) : ShelfRepository {

    override fun getShelves(): Flow<List<Shelf>> {
        return shelfDao.getAllShelves()
            .map { entities ->
                entities.map { entity ->
                    shelfMapper.toShelf(entity)
                }
            }.flowOn(Dispatchers.IO)
    }

    override suspend fun getShelfById(shelfId: Long): Shelf? = withContext(Dispatchers.IO) {
        shelfDao.getShelfById(shelfId)?.let{
            shelfMapper.toShelf(it)
        }
    }

    override suspend fun addShelf(shelf: Shelf): Long = withContext(Dispatchers.IO) {
        shelfDao.insert(shelfMapper.toShelfEntity(shelf))
    }

    override suspend fun updateShelf(shelf: Shelf) = withContext(Dispatchers.IO) {
        shelfDao.update(shelfMapper.toShelfEntity(shelf))
    }

    override suspend fun deleteShelf(shelf: Shelf) = withContext(Dispatchers.IO) {
        shelfDao.delete(shelfMapper.toShelfEntity(shelf))
    }

    override suspend fun addBookToShelf(bookId: Long, shelfId: Long) = withContext(Dispatchers.IO) {
        val bookshelf = BookShelf(bookId, shelfId)
        bookShelfDao.insert(bookshelfMapper.toBookShelfEntity(bookshelf))
    }

    override suspend fun removeBookFromShelf(bookId: Long, shelfId: Long) = withContext(Dispatchers.IO) {
        val bookshelf = BookShelf(bookId, shelfId)
        bookShelfDao.delete(bookshelfMapper.toBookShelfEntity(bookshelf))
    }

    override fun getBooksForShelf(shelfId: Long): Flow<List<Book>> = flow {
        val bookIds: List<Long> = bookShelfDao.getBooksForShelf(shelfId).map { it.bookId }

        val books = bookDao.getBooksByIds(bookIds).map { entity ->
                bookMapper.toBook(entity)
            }

        emit(books)
    }.flowOn(Dispatchers.IO)

    override fun getShelvesForBook(bookId: Long): Flow<List<Shelf>> = flow {
        val shelfIds = bookShelfDao.getShelvesForBook(bookId).map { it.shelfId }
        val shelfs = shelfDao.getShelfsByIds(shelfIds).map { entity ->
            shelfMapper.toShelf(entity)
        }
        emit(shelfs)
    }.flowOn(Dispatchers.IO)
}