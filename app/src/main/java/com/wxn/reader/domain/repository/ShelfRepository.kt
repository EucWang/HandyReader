package com.wxn.reader.domain.repository
import com.wxn.base.bean.Book
import com.wxn.reader.domain.model.Shelf
import kotlinx.coroutines.flow.Flow

/**
 * ★ 同步红线:本接口的所有写方法(suspend fun 返回 Int/Long/Unit 且非 Flow)
 * 必须在 [com.wxn.reader.data.repository.SyncableShelfRepository] 中 override 并调
 * markShelfDirty / markBookShelfRelationDirty,否则书架改动 HLC 不推进,备份/还原会丢数据。
 * 新增写方法时,务必同步更新装饰器。
 */
interface ShelfRepository {
    fun getShelves(): Flow<List<Shelf>>
    suspend fun getShelfById(shelfId: Long): Shelf?
    suspend fun addShelf(shelf: Shelf): Long
    suspend fun updateShelf(shelf: Shelf)
    suspend fun deleteShelf(shelf: Shelf)

    suspend fun addBookToShelf(bookId: Long, shelfId: Long)
    suspend fun removeBookFromShelf(bookId: Long, shelfId: Long)
    fun getBooksForShelf(shelfId: Long): Flow<List<Book>>
    fun getShelvesForBook(bookId: Long): Flow<List<Shelf>>
}