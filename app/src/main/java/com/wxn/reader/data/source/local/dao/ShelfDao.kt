package com.wxn.reader.data.source.local.dao

import androidx.room.*
import com.wxn.reader.data.dto.ShelfEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shelf: ShelfEntity): Long

    @Update
    suspend fun update(shelf: ShelfEntity)

    @Delete
    suspend fun delete(shelf: ShelfEntity)

    @Query("SELECT * FROM shelves ORDER BY `order` ASC")
    fun getAllShelves(): Flow<List<ShelfEntity>>

    @Query("SELECT * FROM shelves WHERE id = :shelfId")
    suspend fun getShelfById(shelfId: Long): ShelfEntity?

    @Query("SELECT * FROM shelves WHERE id IN (:shelfIds)")
    suspend fun getShelfsByIds(shelfIds: List<Long>): List<ShelfEntity>
}