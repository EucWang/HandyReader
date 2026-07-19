package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wxn.reader.data.dto.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE deleted = 0")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND deleted = 0")
    fun getBookmarksForBook(bookId: Long): Flow<List<BookmarkEntity>>

    // ===== ★ 同步方案 §3.2.2 一期新增:per-row HLC + 软删 + 备份查询 =====
    @Query("UPDATE bookmarks SET syncHlcL = :l, syncHlcC = :c, syncHlcDevice = :d WHERE id = :id")
    suspend fun updateBookmarkHlcById(id: Long, l: Long, c: Int, d: String)

    @Query("UPDATE bookmarks SET deletedHlcL = :l, deletedHlcC = :c, deletedHlcDevice = :d WHERE id = :id")
    suspend fun updateBookmarkDeletedHlcById(id: Long, l: Long, c: Int, d: String)

    @Query("SELECT * FROM bookmarks WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId")
    suspend fun getByBookIdIncludeDeleted(bookId: Long): List<BookmarkEntity>

    @Query("UPDATE bookmarks SET deleted = 1, deletedHlcL = :l, deletedHlcC = :c, deletedHlcDevice = :d WHERE bookId = :bookId AND deleted = 0")
    suspend fun markDeletedByBook(bookId: Long, l: Long, c: Int, d: String): Int

    @Query("UPDATE bookmarks SET deleted = 0 WHERE bookId = :bookId AND deleted = 1")
    suspend fun reviveByBook(bookId: Long): Int

    @Query("UPDATE bookmarks SET deleted = 0, syncHlcL = :l, syncHlcC = :c, syncHlcDevice = :d WHERE uuid = :uuid")
    suspend fun reviveByUuid(uuid: String, l: Long, c: Int, d: String): Int
}