package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.OnConflictStrategy
import com.wxn.reader.data.dto.NoteEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY COALESCE(NULLIF(createdAt, 0), 0) DESC, id DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE bookId = :bookId AND deleted = 0 ORDER BY COALESCE(NULLIF(createdAt, 0), 0) DESC, id DESC")
    fun getNotesForBook(bookId: Long): Flow<List<NoteEntity>>

    // ===== ★ 同步方案 §3.2.2 一期新增:per-row HLC + 软删 + 备份查询 =====
    @Query("UPDATE notes SET syncHlcL = :l, syncHlcC = :c, syncHlcDevice = :d WHERE id = :id")
    suspend fun updateNoteHlcById(id: Long, l: Long, c: Int, d: String)

    @Query("UPDATE notes SET deletedHlcL = :l, deletedHlcC = :c, deletedHlcDevice = :d WHERE id = :id")
    suspend fun updateNoteDeletedHlcById(id: Long, l: Long, c: Int, d: String)

    @Query("SELECT * FROM notes WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE bookId = :bookId")
    suspend fun getByBookIdIncludeDeleted(bookId: Long): List<NoteEntity>

    @Query("UPDATE notes SET deleted = 1, deletedHlcL = :l, deletedHlcC = :c, deletedHlcDevice = :d WHERE bookId = :bookId AND deleted = 0")
    suspend fun markDeletedByBook(bookId: Long, l: Long, c: Int, d: String): Int

    @Query("UPDATE notes SET deleted = 0 WHERE bookId = :bookId AND deleted = 1")
    suspend fun reviveByBook(bookId: Long): Int

    @Query("UPDATE notes SET deleted = 0, syncHlcL = :l, syncHlcC = :c, syncHlcDevice = :d WHERE uuid = :uuid")
    suspend fun reviveByUuid(uuid: String, l: Long, c: Int, d: String): Int
}