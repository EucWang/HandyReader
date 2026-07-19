package com.wxn.reader.data.source.local.dao

import androidx.room.*
import com.wxn.reader.data.dto.ShelfEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(shelf: ShelfEntity): Long

    @Update
    suspend fun update(shelf: ShelfEntity)

    @Delete
    suspend fun delete(shelf: ShelfEntity)

    @Query("SELECT * FROM shelves WHERE deleted = 0 ORDER BY `order` ASC")
    fun getAllShelves(): Flow<List<ShelfEntity>>

    @Query("SELECT * FROM shelves WHERE id = :shelfId")
    suspend fun getShelfById(shelfId: Long): ShelfEntity?

    @Query("SELECT * FROM shelves WHERE id IN (:shelfIds)")
    suspend fun getShelfsByIds(shelfIds: List<Long>): List<ShelfEntity>

    // ===== ★ 同步方案 §3.2.4 一期新增:per-row HLC + 软删 + 备份查询 =====
    @Query("UPDATE shelves SET syncHlcL = :l, syncHlcC = :c, syncHlcDevice = :d WHERE id = :id")
    suspend fun updateShelfHlcById(id: Long, l: Long, c: Int, d: String)

    @Query("UPDATE shelves SET deletedHlcL = :l, deletedHlcC = :c, deletedHlcDevice = :d WHERE id = :id")
    suspend fun updateShelfDeletedHlcById(id: Long, l: Long, c: Int, d: String)

    @Query("SELECT * FROM shelves WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): ShelfEntity?

    @Query("SELECT * FROM shelves")
    suspend fun getAllIncludeDeleted(): List<ShelfEntity>

    @Query("UPDATE shelves SET deleted = 0, syncHlcL = :l, syncHlcC = :c, syncHlcDevice = :d WHERE uuid = :uuid")
    suspend fun reviveByUuid(uuid: String, l: Long, c: Int, d: String): Int
}