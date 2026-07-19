package com.wxn.reader.data.source.local.dao

import androidx.room.*
import com.wxn.reader.data.dto.BookShelfEntity

@Dao
interface BookShelfDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookShelf: BookShelfEntity)

    @Update
    suspend fun update(bookShelf: BookShelfEntity)

    @Delete
    suspend fun delete(bookShelf: BookShelfEntity)

    @Query("SELECT * FROM book_shelf WHERE bookId = :bookId AND deleted = 0")
    suspend fun getShelvesForBook(bookId: Long): List<BookShelfEntity>

    @Query("SELECT * FROM book_shelf WHERE shelfId = :shelfId AND deleted = 0")
    suspend fun getBooksForShelf(shelfId: Long): List<BookShelfEntity>

    // ===== ★ 同步方案 §3.2.4 一期新增:per-row HLC + 软删 + 备份查询 =====
    @Query("UPDATE book_shelf SET syncHlcL = :l, syncHlcC = :c, syncHlcDevice = :d WHERE bookId = :bookId AND shelfId = :shelfId")
    suspend fun updateHlcByBookAndShelf(bookId: Long, shelfId: Long, l: Long, c: Int, d: String)

    @Query("UPDATE book_shelf SET deletedHlcL = :l, deletedHlcC = :c, deletedHlcDevice = :d WHERE bookId = :bookId AND shelfId = :shelfId")
    suspend fun updateDeletedHlcByBookAndShelf(bookId: Long, shelfId: Long, l: Long, c: Int, d: String)

    @Query("UPDATE book_shelf SET deleted = 1, deletedHlcL = :l, deletedHlcC = :c, deletedHlcDevice = :d WHERE bookId = :bookId AND shelfId = :shelfId AND deleted = 0")
    suspend fun markDeleted(bookId: Long, shelfId: Long, l: Long, c: Int, d: String): Int

    @Query("SELECT * FROM book_shelf WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): BookShelfEntity?

    @Query("SELECT * FROM book_shelf")
    suspend fun getAllIncludeDeleted(): List<BookShelfEntity>

    @Query("UPDATE book_shelf SET deleted = 0, syncHlcL = :l, syncHlcC = :c, syncHlcDevice = :d WHERE uuid = :uuid")
    suspend fun reviveByUuid(uuid: String, l: Long, c: Int, d: String): Int
}