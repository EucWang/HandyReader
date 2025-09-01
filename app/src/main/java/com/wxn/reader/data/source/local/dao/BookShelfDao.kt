package com.wxn.reader.data.source.local.dao

import androidx.room.*
import com.wxn.reader.data.dto.BookShelfEntity

@Dao
interface BookShelfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookShelf: BookShelfEntity)

    @Delete
    suspend fun delete(bookShelf: BookShelfEntity)

    @Query("SELECT * FROM book_shelf WHERE bookId = :bookId")
    suspend fun getShelvesForBook(bookId: Long): List<BookShelfEntity>

    @Query("SELECT * FROM book_shelf WHERE shelfId = :shelfId")
    suspend fun getBooksForShelf(shelfId: Long): List<BookShelfEntity>
}